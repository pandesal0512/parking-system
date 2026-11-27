"""
Keeps the gate board's list of allowed plates up to date.

Every few seconds this asks the backend which plates the gate would let in right now (approved vehicle, active
owner, not suspended, not already inside, a reservation that is valid at this moment) and gives that list to the
board over the USB cable. The board then opens the barrier by itself when a plate on the list is read.

Normally plate_gate.py starts this for you:   python tools/plate_gate.py --esp32 COM5 --allowlist
Only one program can use the USB port, so this file is also used as a library by plate_gate.py.

On its own, for testing without cameras:
    python tools/plate_sync.py --show                 print the list the backend gives (no board needed)
    python tools/plate_sync.py --esp32 COM5           keep the board's list updated and print it when it changes

The key must equal DEVICE_API_KEY on the backend (set it as an environment variable, or the script asks).
"""
import argparse
import json
import os
import sys
import threading
import urllib.error
import urllib.request

from plate_logic import EspLink


def fetch_allowed(backend, key):
    """Asks the backend for the plates allowed in right now. Returns a list of plates, or raises."""
    req = urllib.request.Request(backend + "/api/device/allowed-plates", headers={"X-Device-Key": key})
    with urllib.request.urlopen(req, timeout=5) as resp:
        return list(json.loads(resp.read().decode("utf-8")).get("plates", []))


class PlateSyncer(threading.Thread):
    """Background thread: fetch the list, give it to the board, repeat. If the backend cannot be reached,
    nothing is sent, and the board forgets its list after a minute (it then refuses entries: closed is the safe side)."""

    def __init__(self, link, fetch, interval=5.0, on_change=None):
        super().__init__(daemon=True)
        self.link = link
        self.fetch = fetch                  # a function that returns the list of plates
        self.interval = interval
        self.on_change = on_change or (lambda plates, error: None)
        self.stop_event = threading.Event()
        self.last = None

    def sync_once(self):
        try:
            plates = sorted(self.fetch())
        except (urllib.error.URLError, TimeoutError, ValueError, OSError) as e:
            self.on_change(self.last, "Cannot reach the backend: %s" % e)
            return False
        self.link.send_list(plates)         # sent every time, so the board's list never expires while we are alive
        if plates != self.last:
            self.last = plates
            self.on_change(plates, None)
        return True

    def run(self):
        while not self.stop_event.is_set():
            self.sync_once()
            self.stop_event.wait(self.interval)

    def stop(self):
        self.stop_event.set()


def print_change(plates, error):
    if error:
        print(error, flush=True)
    else:
        print("Allowed plates now (%d): %s" % (len(plates), ", ".join(plates) or "none"), flush=True)


def main():
    parser = argparse.ArgumentParser(description="Keep the gate board's allowed-plates list up to date")
    parser.add_argument("--backend", default=os.environ.get("PARKING_BACKEND", "http://localhost:8080"))
    parser.add_argument("--esp32", metavar="PORT", help="USB port of the board, for example COM5")
    parser.add_argument("--show", action="store_true", help="only print the list from the backend, once")
    parser.add_argument("--interval", type=float, default=5.0, help="seconds between updates (default 5)")
    args = parser.parse_args()
    if not args.show and not args.esp32:
        sys.exit("Give --esp32 COM5 to update a board, or --show to just print the list.")

    key = os.environ.get("DEVICE_API_KEY") or input("Device key (DEVICE_API_KEY of the backend): ").strip()
    fetch = lambda: fetch_allowed(args.backend, key)
    if args.show:
        try:
            print_change(sorted(fetch()), None)
        except urllib.error.HTTPError as e:
            sys.exit("The backend refused the request (%s). Check the device key." % e.code)
        except (urllib.error.URLError, TimeoutError):
            sys.exit("Cannot reach the backend at %s. Is it running?" % args.backend)
        return

    syncer = PlateSyncer(EspLink.open(args.esp32), fetch, args.interval, print_change)
    print("Updating the board on %s every %.0f seconds. Ctrl+C to stop." % (args.esp32, args.interval), flush=True)
    syncer.start()
    try:
        while True:
            syncer.join(1)
    except KeyboardInterrupt:
        syncer.stop()


if __name__ == "__main__":
    main()
