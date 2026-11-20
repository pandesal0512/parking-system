#!/usr/bin/env python3
"""
Pretend to be the gate hardware, to test the backend without any ESP32.

It sends the same requests the real device sends (POST /api/device/gate and /api/device/heartbeat).
Needs only Python 3 (no extra packages).

Examples (the server must be running with DEVICE_API_KEY set):

  python tools/fake_gate.py --key YOUR_DEVICE_KEY                      # type plates by hand
  python tools/fake_gate.py --key YOUR_DEVICE_KEY --plate ABC1234 --direction IN
  python tools/fake_gate.py --url http://192.168.1.20:8080 --key YOUR_DEVICE_KEY

The key can also come from the environment variable DEVICE_API_KEY.
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request


def post(base, key, path, body):
    data = json.dumps(body).encode("utf-8")
    request = urllib.request.Request(
        base.rstrip("/") + path,
        data=data,
        method="POST",
        headers={"Content-Type": "application/json", "X-Device-Key": key},
    )
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        text = error.read().decode("utf-8", "replace")
        try:
            return error.code, json.loads(text)
        except ValueError:
            return error.code, {"message": text}
    except (urllib.error.URLError, TimeoutError) as error:
        return 0, {"message": "Cannot reach the server: %s" % error}


def send_plate(base, key, device, plate, direction):
    status, body = post(base, key, "/api/device/gate",
                        {"deviceId": device, "identifier": plate, "direction": direction})
    if status == 200:
        verdict = "OPEN THE BARRIER" if body.get("allowed") else "KEEP IT CLOSED"
        reason = (" [%s]" % body["reason"]) if body.get("reason") else ""
        print("%s%s - %s" % (verdict, reason, body.get("message", "")))
        if body.get("ownerName"):
            print("   %s, %s, %s" % (body["ownerName"], body.get("vehicleType"), body.get("identifier")))
    else:
        print("ERROR %s: %s" % (status or "network", body.get("message", body)))
        if status == 401:
            print("   Check that the key matches DEVICE_API_KEY on the server.")
        if status == 403:
            print("   The server has no DEVICE_API_KEY set, so the hardware endpoints are off.")


def main():
    parser = argparse.ArgumentParser(description="Fake gate hardware for testing the parking backend.")
    parser.add_argument("--url", default="http://localhost:8080", help="backend address (default http://localhost:8080)")
    parser.add_argument("--key", default=os.environ.get("DEVICE_API_KEY", ""), help="the device key")
    parser.add_argument("--device", default="fake-gate", help="device name shown on the dashboard")
    parser.add_argument("--plate", help="send this plate once and exit")
    parser.add_argument("--direction", choices=["IN", "OUT"], default="IN")
    args = parser.parse_args()

    if not args.key:
        sys.exit("Give the device key with --key or the DEVICE_API_KEY environment variable.")

    if args.plate:
        send_plate(args.url, args.key, args.device, args.plate, args.direction)
        return

    status, body = post(args.url, args.key, "/api/device/heartbeat", {"deviceId": args.device})
    print("Connected." if status == 200 else "Heartbeat failed: %s" % body.get("message", body))
    print("Type a plate and press Enter. Start with 'out ' for an exit, for example: out ABC1234. Empty line quits.")
    while True:
        try:
            line = input("plate> ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            break
        if not line:
            break
        direction = "IN"
        if line.lower().startswith("out "):
            direction, line = "OUT", line[4:].strip()
        send_plate(args.url, args.key, args.device, line, direction)


if __name__ == "__main__":
    main()
