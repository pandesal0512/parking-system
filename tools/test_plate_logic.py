"""Run:  python tools/test_plate_logic.py   (no camera or backend needed)"""
import unittest

from plate_gate import Lane, handle_frame
from plate_logic import Debouncer, EspLink, clean, parse_camera, pick_plate


def box(x):
    return [[x, 0], [x + 50, 0], [x + 50, 20], [x, 20]]


class PickPlate(unittest.TestCase):
    def test_single_read(self):
        self.assertEqual(pick_plate([(box(0), "abc 1234", 0.9)]), "ABC1234")

    def test_two_pieces_are_joined_left_to_right(self):
        self.assertEqual(pick_plate([(box(200), "1234", 0.8), (box(0), "ABC", 0.9)]), "ABC1234")

    def test_ignores_extra_words_and_low_confidence(self):
        results = [(box(0), "PHILIPPINES", 0.95), (box(60), "ABC 1234", 0.8), (box(300), "X7", 0.2)]
        self.assertEqual(pick_plate(results), "ABC1234")

    def test_needs_letters_and_digits(self):
        self.assertIsNone(pick_plate([(box(0), "PHILIPPINES", 0.9)]))
        self.assertIsNone(pick_plate([(box(0), "12345", 0.9)]))
        self.assertIsNone(pick_plate([]))

    def test_clean(self):
        self.assertEqual(clean(" ab-12 "), "AB12")


class DebouncerTest(unittest.TestCase):
    def test_needs_two_matching_reads_then_cools_down(self):
        now = [0.0]
        d = Debouncer(confirm=2, cooldown=15, clock=lambda: now[0])
        self.assertIsNone(d.feed("ABC1234"))
        self.assertEqual(d.feed("ABC1234"), "ABC1234")
        for _ in range(5):                       # still in front of the camera: not sent again
            self.assertIsNone(d.feed("ABC1234"))
        now[0] = 16                              # 15 seconds later it may be sent again
        self.assertEqual(d.feed("ABC1234"), "ABC1234")

    def test_a_different_read_resets_the_count(self):
        d = Debouncer(confirm=2, cooldown=15, clock=lambda: 0.0)
        self.assertIsNone(d.feed("ABC1234"))
        self.assertIsNone(d.feed("ABC1235"))     # misread
        self.assertIsNone(d.feed(None))
        self.assertIsNone(d.feed("ABC1234"))
        self.assertEqual(d.feed("ABC1234"), "ABC1234")


class LaneTest(unittest.TestCase):
    def test_lane_sends_its_own_direction_and_shows_the_answer(self):
        sent = []

        def send(lane, plate):
            sent.append((lane.device_id, lane.direction, plate))
            return {"allowed": True, "message": "Welcome"}

        out = Lane("OUT", "OUT", "gate-exit", 1)
        frame = [(box(0), "ABC 1234", 0.9)]
        self.assertIsNone(handle_frame(out, frame, send))
        self.assertTrue(handle_frame(out, frame, send)["allowed"])
        self.assertEqual(sent, [("gate-exit", "OUT", "ABC1234")])
        self.assertTrue(out.decision.startswith("OPEN BARRIER"))
        self.assertIsNone(handle_frame(out, frame, send))   # cooldown


class FakeSerial:
    def __init__(self, replies):
        self.replies, self.written = list(replies), []

    def write(self, data):
        self.written.append(data.decode())

    def readline(self):
        return (self.replies.pop(0) if self.replies else "").encode()


class EspLinkTest(unittest.TestCase):
    def test_sends_direction_and_plate_and_reads_the_answer(self):
        ser = FakeSerial(["boot noise\n", "RESULT,1,Entry recorded, welcome\n"])
        answer = EspLink(ser).check(Lane("IN", "IN", "x", 0), "ABC1234")
        self.assertEqual(ser.written, ["PLATE,IN,ABC1234\n"])
        self.assertEqual(answer, {"allowed": True, "message": "Entry recorded, welcome"})

    def test_refused_and_silence(self):
        ser = FakeSerial(["RESULT,0,No reservation\n"])
        self.assertFalse(EspLink(ser).check(Lane("OUT", "OUT", "x", 1), "ABC1234")["allowed"])
        now = [0.0]
        def tick():
            now[0] += 5
            return now[0]
        silent = EspLink(FakeSerial([]), wait_seconds=10, clock=tick).check(Lane("IN", "IN", "x", 0), "ABC1234")
        self.assertFalse(silent["allowed"])
        self.assertIn("did not answer", silent["message"])


class ParseCamera(unittest.TestCase):
    def test_numbers(self):
        self.assertEqual(parse_camera("0"), 0)
        self.assertEqual(parse_camera("1"), 1)
        self.assertEqual(parse_camera("-1"), -1)

    def test_phone_address_is_kept_as_text(self):
        self.assertEqual(parse_camera("http://192.168.1.23:8080/video"), "http://192.168.1.23:8080/video")
        self.assertEqual(parse_camera("  rtsp://10.0.0.5:554/live  "), "rtsp://10.0.0.5:554/live")

    def test_garbage_is_refused(self):
        with self.assertRaises(ValueError):
            parse_camera("front door")


if __name__ == "__main__":
    unittest.main()
