"""
Camera + plate reading test.

Opens the webcam in a window. Hold a toy car with its plate facing the camera,
press SPACE to read the plate, press Q to quit.

Setup (once):   pip install opencv-python easyocr
Run:            python camera_test.py
"""
import cv2
import easyocr

reader = easyocr.Reader(["en"], gpu=False)   # loads the OCR model once (first run downloads it)

cam = cv2.VideoCapture(0, cv2.CAP_DSHOW)     # 0 = first camera (the webcam)
cam.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
cam.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)

if not cam.isOpened():
    raise SystemExit("Camera not found. Close other apps that use it (Zoom, Camera app, browser) and try again.")

text_on_screen = ""

while True:
    ok, frame = cam.read()
    if not ok:
        break

    display = frame.copy()                  # the text is drawn on this copy only
    cv2.putText(display, text_on_screen, (20, 50),
                cv2.FONT_HERSHEY_SIMPLEX, 1.2, (0, 255, 0), 3)
    cv2.putText(display, "SPACE = read plate   Q = quit", (20, 700),
                cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
    cv2.imshow("Gate camera", display)

    key = cv2.waitKey(1) & 0xFF
    if key == ord("q"):
        break
    if key == ord(" "):
        results = reader.readtext(frame)    # OCR reads the clean frame, without the overlay
        text_on_screen = " ".join(r[1] for r in results) or "(nothing read)"
        print("Read:", text_on_screen)

cam.release()
cv2.destroyAllWindows()
