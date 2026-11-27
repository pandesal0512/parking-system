"""Run:  python tools/test_plate_sync.py   (no board or backend needed)"""
import unittest
import urllib.error

from plate_logic import EspLink
from plate_sync import PlateSyncer
from test_plate_logic import FakeSerial


class SyncTest(unittest.TestCase):
    def test_sends_the_list_and_reports_changes(self):
        ser, changes = FakeSerial([]), []
        link = EspLink(ser)
        link.send_list = lambda plates: ser.written.append(",".join(plates))      # no pauses in the test
        syncer = PlateSyncer(link, lambda: ["XYZ99", "ABC1234"], on_change=lambda p, e: changes.append((p, e)))
        self.assertTrue(syncer.sync_once())
        self.assertTrue(syncer.sync_once())                                        # same list: sent again, reported once
        self.assertEqual(ser.written, ["ABC1234,XYZ99", "ABC1234,XYZ99"])
        self.assertEqual(changes, [(["ABC1234", "XYZ99"], None)])

    def test_backend_down_sends_nothing(self):
        ser, changes = FakeSerial([]), []
        link = EspLink(ser)

        def down():
            raise urllib.error.URLError("refused")
        syncer = PlateSyncer(link, down, on_change=lambda p, e: changes.append((p, e)))
        self.assertFalse(syncer.sync_once())
        self.assertEqual(ser.written, [])
        self.assertIn("Cannot reach the backend", changes[0][1])


if __name__ == "__main__":
    unittest.main()
