"""
The thinking part of the gate reader, kept separate from the camera so it can be tested without one.

  pick_plate(results)  chooses the most plate-like text from what the OCR read in one frame
  Debouncer            decides when a plate has been seen often enough to ask the backend,
                       and stops the same plate being sent again while it sits in front of the camera
"""
import re
import threading
import time

MIN_CONFIDENCE = 0.35     # ignore text the OCR is not sure about
MIN_LENGTH = 4            # shortest and longest plate we accept (letters and digits only)
MAX_LENGTH = 10


def clean(text):
    """Uppercase letters and digits only: 'abc 1234' -> 'ABC1234'."""
    return re.sub(r"[^A-Z0-9]", "", text.upper())


def pick_plate(results, min_confidence=MIN_CONFIDENCE):
    """
    results: what easyocr's readtext() returns, a list of (box, text, confidence).
    Returns the best plate-like text, or None when nothing in the frame looks like a plate.

    A plate must have letters AND digits. Plates are often read as two pieces ('ABC' and '1234'),
    so the pieces next to each other (left to right) are tried joined as well as alone.
    """
    pieces = []
    for box, text, confidence in results:
        text = clean(text)
        if text and confidence >= min_confidence:
            left = min(point[0] for point in box)
            pieces.append((left, text, confidence))
    pieces.sort()

    candidates = []
    for i, (_, text, confidence) in enumerate(pieces):
        candidates.append((text, confidence))
        if i + 1 < len(pieces):
            nxt = pieces[i + 1]
            candidates.append((text + nxt[1], (confidence + nxt[2]) / 2))

    best, best_score = None, 0.0
    for text, confidence in candidates:
        if not MIN_LENGTH <= len(text) <= MAX_LENGTH:
            continue
        if not (any(c.isalpha() for c in text) and any(c.isdigit() for c in text)):
            continue
        score = confidence + (0.2 if 6 <= len(text) <= 7 else 0.0)
        if score > best_score:
            best, best_score = text, score
    return best


class Debouncer:
    """
    One reading can be wrong, so a plate is only sent after `confirm` readings in a row agree.
    After it is sent, the same plate is ignored for `cooldown` seconds (a car waiting in front of the
    camera must not open the gate or be logged again and again).
    """

    def __init__(self, confirm=2, cooldown=15.0, clock=time.monotonic):
        self.confirm = confirm
        self.cooldown = cooldown
        self.clock = clock
        self._plate = None
        self._count = 0
        self._sent = {}

    def feed(self, plate):
        """Give it the plate read in the latest frame (or None). Returns the plate when it should be sent."""
        if plate is None:
            self._plate, self._count = None, 0
            return None
        if plate == self._plate:
            self._count += 1
        else:
            self._plate, self._count = plate, 1
        if self._count < self.confirm:
            return None
        now = self.clock()
        if now - self._sent.get(plate, float("-inf")) < self.cooldown:
            return None
        self._sent[plate] = now
        self._plate, self._count = None, 0
        return plate


class EspLink:
    """
    The USB cable to the ESP32. Sends  PLATE,<IN|OUT>,<plate>  and waits for  RESULT,<1|0>,<message>.
    `serial_port` is a pyserial Serial object (anything with write() and readline() works, so it can be tested).
    """

    def __init__(self, serial_port, wait_seconds=10.0, clock=time.monotonic):
        self.port = serial_port
        self.wait_seconds = wait_seconds
        self.clock = clock
        self.lock = threading.Lock()          # one conversation on the cable at a time

    @classmethod
    def open(cls, name):
        import serial                       # pip install pyserial
        port = serial.Serial(name, 115200, timeout=1)
        time.sleep(2)                       # the board restarts when the port opens
        return cls(port)

    def check(self, lane, plate):
        """Same job as asking the backend directly: returns {"allowed": bool, "message": str}."""
        with self.lock:
            self.port.write(("PLATE,%s,%s\n" % (lane.direction, plate)).encode("ascii", "ignore"))
            deadline = self.clock() + self.wait_seconds
            while self.clock() < deadline:
                line = self.port.readline().decode("utf-8", "ignore").strip()
                if line.startswith("RESULT,"):
                    parts = line.split(",", 2)
                    if len(parts) >= 2:
                        return {"allowed": parts[1] == "1", "message": parts[2] if len(parts) > 2 else ""}
        return {"allowed": False, "message": "The board did not answer. Use the manual gate."}

    def send_list(self, plates, pause=0.02):
        """Gives the board the list of plates allowed in (see plate_sync.py). `pause` keeps a small board's buffer from filling."""
        with self.lock:
            for line in list_lines(plates):
                self.port.write((line + "\n").encode("ascii", "ignore"))
                if pause:
                    time.sleep(pause)


def parse_camera(value):
    """A camera is a number (0, 1, ...; -1 = none) or a stream address such as http://192.168.1.30:8080/video."""
    value = str(value).strip()
    try:
        return int(value)
    except ValueError:
        if value.lower().startswith(("http://", "https://", "rtsp://")):
            return value
        raise ValueError("A camera is a number like 0, or an address starting with http:// or rtsp://")


def is_camera_off(camera):
    return isinstance(camera, int) and camera < 0


MAX_LIST = 20          # the board has little memory, so the list is capped
MAX_PLATE = 12         # the longest plate the backend accepts


def list_lines(plates):
    """The lines that carry a list to the board: LIST_BEGIN, one P,<plate> per plate, LIST_END."""
    clean_plates = []
    for plate in plates:
        plate = clean(str(plate))
        if plate and len(plate) <= MAX_PLATE and plate not in clean_plates:
            clean_plates.append(plate)
    return ["LIST_BEGIN"] + ["P," + p for p in clean_plates[:MAX_LIST]] + ["LIST_END"]
