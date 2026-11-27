"""
Gate reader: two cameras (IN and OUT), automatic plate reading, no key presses.

Each camera watches its own lane. When a plate has been read the same way twice in a row, it is sent
to the backend, and the screen shows OPEN BARRIER or KEEP CLOSED. The same plate is not sent again for
15 seconds. Press Q in a camera window to quit.

Run (the backend must be running):
    python tools/plate_gate.py                      camera 0 = IN, camera 1 = OUT
    python tools/plate_gate.py --in-cam 1 --out-cam 2
    python tools/plate_gate.py --out-cam -1         only one camera: press I to switch it between IN and OUT
    python tools/plate_gate.py --esp32 COM5 --allowlist   the board opens the barrier from a list of allowed plates that is
                                                          kept up to date from the backend (see plate_sync.py)
    python tools/plate_gate.py --esp32 COM5         send the plates to the ESP32 over USB (it asks the backend and moves the barrier)
    python tools/plate_gate.py --out-cam http://192.168.1.30:8080/video     a phone as the camera (IP camera app)
    python tools/plate_gate.py --list-cameras       shows which camera numbers work (to tell the laptop cam from the USB cam)

The key must equal DEVICE_API_KEY on the backend. Put it in the DEVICE_API_KEY environment variable
(IntelliJ: Run configuration > Environment variables), or the script asks for it when it starts.
"""
import argparse
import json
import os
import sys
import threading
import time
import urllib.error
import urllib.request

from plate_sync import PlateSyncer, fetch_allowed
from plate_logic import Debouncer, EspLink, is_camera_off, parse_camera, pick_plate

GREEN, RED, WHITE, YELLOW = (0, 255, 0), (0, 0, 255), (255, 255, 255), (255, 255, 0)


class Lane:
    """One camera, one direction, one device id (it shows up as its own light on the Gate page)."""

    def __init__(self, name, direction, device_id, camera_index):
        self.name = name
        self.direction = direction
        self.device_id = device_id
        self.camera_index = camera_index
        self.frame = None                 # newest picture from the camera
        self.lock = threading.Lock()
        self.debouncer = Debouncer(confirm=2, cooldown=15.0)
        self.plate_text = ""
        self.decision = "Waiting for a plate..."
        self.decision_color = WHITE


def post(backend, key, path, payload):
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        backend + path, data=body, method="POST",
        headers={"Content-Type": "application/json", "X-Device-Key": key})
    with urllib.request.urlopen(req, timeout=5) as resp:
        return json.loads(resp.read().decode("utf-8"))


def check_plate(backend, key, lane, plate):
    try:
        return post(backend, key, "/api/device/gate",
                    {"deviceId": lane.device_id, "identifier": plate, "direction": lane.direction})
    except urllib.error.HTTPError as e:   # must come before URLError (it is a subclass)
        return {"allowed": False, "message": f"Server refused the request ({e.code}). Check the device key."}
    except (urllib.error.URLError, TimeoutError):
        return {"allowed": False, "message": "Backend not reachable. Use the manual gate."}


def report(backend, key, lane, plate, board_answer):
    """After the board has decided, tell the backend too, so the log, the dashboard and refusals stay correct."""
    backend_answer = check_plate(backend, key, lane, plate)
    if board_answer.get("allowed") and not backend_answer.get("allowed"):
        print(f"[{lane.name}] WARNING: the board opened for {plate} but the backend refused: "
              f"{backend_answer.get('message')}", flush=True)


def handle_frame(lane, results, send):
    """One OCR result for one lane. `send(lane, plate)` asks the backend. Returns the answer, or None."""
    plate = lane.debouncer.feed(pick_plate(results))
    if plate is None:
        return None
    answer = send(lane, plate)
    allowed = answer.get("allowed", False)
    message = answer.get("message") or answer.get("reason") or ""
    lane.plate_text = plate
    lane.decision = ("OPEN BARRIER - " if allowed else "KEEP CLOSED - ") + message
    lane.decision_color = GREEN if allowed else RED
    print(f"[{lane.name}] Read: {plate} | {'ALLOWED' if allowed else 'REFUSED'} | {message}", flush=True)
    return answer


def ocr_worker(lanes, reader, send, stop):
    """Takes the newest picture from each camera in turn. One reader is shared, so cameras take turns."""
    while not stop.is_set():
        did_work = False
        for lane in lanes:
            with lane.lock:
                frame = None if lane.frame is None else lane.frame.copy()
            if frame is None:
                continue
            did_work = True
            try:
                handle_frame(lane, reader.readtext(frame), send)
            except Exception as e:                      # a bad frame must not stop the reader
                print(f"[{lane.name}] reading failed: {e}", flush=True)
        if not did_work:
            time.sleep(0.2)


def heartbeat_worker(lanes, backend, key, stop):
    """Tells the backend every 10 seconds that each camera is alive (the Online light on the Gate page)."""
    while not stop.is_set():
        for lane in lanes:
            try:
                post(backend, key, "/api/device/heartbeat", {"deviceId": lane.device_id})
            except Exception:
                pass
        stop.wait(10)


def draw(cv2, image, lane, single=False):
    lines = [(f"{lane.name} camera", WHITE), (f"Plate: {lane.plate_text or '-'}", YELLOW),
             (lane.decision, lane.decision_color), ("Reads automatically.   Q = quit" + ("   I = IN/OUT" if single else ""), WHITE)]
    y = 40
    for text, color in lines:
        cv2.putText(image, text, (20, y), cv2.FONT_HERSHEY_SIMPLEX, 0.9, color, 2)
        y += 40


def open_camera(cv2, source):
    if isinstance(source, str):            # a phone or IP camera stream
        cam = cv2.VideoCapture(source)
        cam.set(cv2.CAP_PROP_BUFFERSIZE, 1)   # keep the picture current, not seconds old
    else:                                  # a USB or laptop camera
        cam = cv2.VideoCapture(source, cv2.CAP_DSHOW)
        cam.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
        cam.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
    return cam if cam.isOpened() else None


def main():
    parser = argparse.ArgumentParser(description="Two-camera automatic gate reader")
    parser.add_argument("--backend", default=os.environ.get("PARKING_BACKEND", "http://localhost:8080"))
    parser.add_argument("--in-cam", type=parse_camera, default=0,
                        help="entrance camera: a number (-1 = none) or a stream address such as http://192.168.1.30:8080/video")
    parser.add_argument("--out-cam", type=parse_camera, default=1, help="exit camera: same kinds of value as --in-cam")
    parser.add_argument("--esp32", metavar="PORT", help="USB port of the ESP32 (for example COM5). The ESP32 then asks the "
                        "backend, opens the barrier and sends the heartbeat. Without this the script asks the backend itself.")
    parser.add_argument("--allowlist", action="store_true", help="with --esp32: the board decides from a list of allowed "
                        "plates that this script keeps updated from the backend (board sketch: esp32/gate_allowlist). "
                        "Every plate is also reported to the backend, so entries, exits and refusals are still logged.")
    parser.add_argument("--list-cameras", action="store_true", help="show which camera numbers can be opened, then exit")
    args = parser.parse_args()

    if args.list_cameras:
        import cv2
        for index in range(5):
            cam = open_camera(cv2, index)
            print(f"Camera {index}: {'works' if cam else 'not found'}")
            if cam:
                cam.release()
        print("Usually 0 is the laptop camera and 1 is the USB camera. If they are the wrong way round, "
              "use --in-cam 1 --out-cam 0.")
        return

    if args.allowlist and not args.esp32:
        sys.exit("--allowlist needs --esp32 COM5 (the board that holds the list).")
    key = ""
    if not args.esp32 or args.allowlist:   # with the online ESP32 sketch the key lives in the sketch, not here
        key = os.environ.get("DEVICE_API_KEY") or input("Device key (DEVICE_API_KEY of the backend): ").strip()
        if not key:
            sys.exit("A device key is needed.")

    import cv2
    import easyocr

    wanted = [Lane("IN", "IN", "gate-entry", args.in_cam), Lane("OUT", "OUT", "gate-exit", args.out_cam)]
    cams, lanes = {}, []
    for lane in wanted:
        if is_camera_off(lane.camera_index):
            continue
        cam = open_camera(cv2, lane.camera_index)
        if cam is None:
            print(f"{lane.name} camera ({lane.camera_index}) could not be opened. Close other apps that use it, or check the address. Skipping it.")
            continue
        cams[lane.name] = cam
        lanes.append(lane)
    if not lanes:
        sys.exit("No camera could be opened.")

    if len(lanes) == 1:
        print(f"Only one camera is working, so it is the {lanes[0].name} camera. Press I to switch it between IN and OUT.")
    print("Loading the plate reader (the first run downloads its model)...", flush=True)
    reader = easyocr.Reader(["en"], gpu=False)

    stop = threading.Event()
    if args.esp32 and args.allowlist:
        link = EspLink.open(args.esp32)
        syncer = PlateSyncer(link, lambda: fetch_allowed(args.backend, key), 5.0,
                             lambda plates, error: print(error or f"Allowed plates now ({len(plates)}): {', '.join(plates) or 'none'}", flush=True))
        syncer.start()
        threading.Thread(target=heartbeat_worker, args=(lanes, args.backend, key, stop), daemon=True).start()

        def send(lane, plate):
            answer = link.check(lane, plate)            # the board decides, straight away
            threading.Thread(target=report, args=(args.backend, key, lane, plate, answer), daemon=True).start()
            return answer
        print(f"The board on {args.esp32} decides from its list of allowed plates; every plate is also reported to the backend.", flush=True)
    elif args.esp32:
        send = EspLink.open(args.esp32).check
        print(f"Plates go to the ESP32 on {args.esp32}.", flush=True)
    else:
        send = lambda lane, plate: check_plate(args.backend, key, lane, plate)
        threading.Thread(target=heartbeat_worker, args=(lanes, args.backend, key, stop), daemon=True).start()
    threading.Thread(target=ocr_worker, args=(lanes, reader, send, stop), daemon=True).start()
    print("Ready. Hold a plate in front of a camera.", flush=True)

    while not stop.is_set():
        for lane in lanes:
            ok, frame = cams[lane.name].read()
            if not ok:
                continue
            with lane.lock:
                lane.frame = frame            # the reader looks at the clean picture, without the overlay
            shown = frame.copy()
            draw(cv2, shown, lane, single=len(lanes) == 1)
            cv2.imshow("Gate camera" if len(lanes) == 1 else f"Gate {lane.name}", shown)
        key_pressed = cv2.waitKey(1) & 0xFF
        if key_pressed == ord("q"):
            stop.set()
        if key_pressed == ord("i") and len(lanes) == 1:     # one camera must serve both directions
            lane = lanes[0]
            old_name = lane.name
            lane.direction = "OUT" if lane.direction == "IN" else "IN"
            lane.name = lane.direction
            cams[lane.name] = cams.pop(old_name)     # the camera must be found under its new name too
            lane.device_id = "gate-entry" if lane.direction == "IN" else "gate-exit"
            lane.decision, lane.decision_color = "Waiting for a plate...", WHITE

    for cam in cams.values():
        cam.release()
    cv2.destroyAllWindows()


if __name__ == "__main__":
    main()
