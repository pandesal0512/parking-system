# Connecting the gate hardware (for the hardware team)

The backend already has a door for the hardware. The hardware only has to do three things: **read a plate,
send it to the backend, open the barrier if the answer is "allowed"**. All the rules (registered, approved,
reservation, penalties, capacity) stay in the backend, so the hardware never decides anything.

```
plate source ──► ESP32-S3 ──Wi-Fi──► laptop running the backend ──► answer {allowed: true/false, message}
(see "Reading the plate")        POST /api/device/gate                      │
                                                                 ESP32 opens the servo (SG90),
                                                                 shows the message on the TFT,
                                                                 green/red RGB LED, speaker beep
```

The manual **Gate** page in the web app stays as the fallback. It uses exactly the same rules.

## 1. Reading the plate (the open question)

The ESP32 boards in the shopping list cannot read a plate by themselves, and **no camera and no vehicle
sensor appear in the cart** (the AIoT kit has TFT, audio, servo, relay, RGB LED, DHT11, rain and soil sensors;
the Creator kit has OLED, PIR, buttons, light sensor). The backend does not care how the plate text is
obtained, it just needs the text. Pick one:

| Option | How it works | What is missing |
|---|---|---|
| **A. Camera + OCR on the laptop** | An ESP32-S3 camera board (or a USB webcam) takes a picture; a small program on the laptop reads the plate (Python OCR, for example EasyOCR) and posts the text to the backend. Closest to the real project. | A camera. Reading small printed plates on toy cars needs good light and a close, straight shot. |
| **B. RFID/NFC tag per vehicle** | Each toy vehicle carries a tag; the reader sends the tag text as the "plate". Very reliable for a miniature model. | An RC522 reader and tags. The tag text must equal the registered plate. |
| **C. Keypad** | The driver types the plate on a keypad. | A keypad. |

OCR is not done on the ESP32. Whatever the option, the ESP32 (or the laptop program) ends up with a text
like `ABC 1234` and sends it. Spaces, dashes and lower case are cleaned by the backend, so `abc-1234` is fine.

A **vehicle detector** (an IR obstacle sensor or an ultrasonic sensor, both a few pesos) tells the ESP32
that a vehicle is at the gate, so it knows when to take the picture or read the tag. The PIR sensor in the
Creator kit detects people moving, not a toy car sitting still, so it is a poor choice.

## 2. Set up (once)

1. **Choose a device key** and start the backend with it. Use a long random value (20+ characters).
   Never commit it to git.
   - IntelliJ: Run > Edit Configurations > Environment variables: `DEVICE_API_KEY=your-long-random-key`
   - Or in `application-local.properties`: `app.device.api-key=your-long-random-key`
   Without a key the hardware endpoints are **off** (they answer 403).
2. **Same network.** The laptop running the backend and the ESP32 must be on the same Wi-Fi (a phone hotspot
   works). Find the laptop's address: Windows `ipconfig` (IPv4 Address, for example `192.168.1.20`).
   The ESP32 uses `http://192.168.1.20:8080`, **not** `localhost` (on the ESP32, localhost is the ESP32 itself).
3. **Firewall.** Windows asks to allow Java on private networks the first time. Allow it. If the ESP32 cannot
   connect, this is the first thing to check. Test from another device's browser: `http://192.168.1.20:8080`.
4. **Test without hardware** using the script in this project:
   `python tools/fake_gate.py --url http://localhost:8080 --key your-long-random-key`
   Type a plate. It prints `OPEN THE BARRIER` or `KEEP IT CLOSED` with the reason. The dashboard shows
   the fake device as **Online** under "Gate hardware".

## 3. The API

Every request needs the header `X-Device-Key: <the key>` and `Content-Type: application/json`.
The key opens **only** `/api/device/**`, nothing else in the system.

### POST /api/device/gate : a plate was read

```json
{ "deviceId": "gate-entry", "identifier": "ABC 1234", "direction": "IN" }
```

- `deviceId`: any name, letters/numbers/dot/underscore/dash, up to 40. Shown on the dashboard.
- `direction`: `IN` for the entrance device, `OUT` for the exit device. Fixed by where it is mounted.

Answer (HTTP 200 whether allowed or refused):

```json
{ "allowed": true, "reason": null, "message": "Entry recorded. The reservation ends Nov 19, 11:00 AM.",
  "direction": "IN", "identifier": "ABC1234", "ownerName": "Ana Cruz", "vehicleType": "CAR" }
```

- **`allowed: true` : open the barrier. `allowed: false` : keep it closed.** Show `message` on the screen.
- `reason` when refused: `UNREGISTERED`, `VEHICLE_NOT_APPROVED`, `ACCOUNT_DISABLED`, `ACCOUNT_SUSPENDED`,
  `ALREADY_INSIDE`, `NO_RESERVATION`, `LOT_FULL`.
- **Exits are always allowed** (`direction: OUT`): a vehicle is never trapped inside. `reason` may be
  `OVERSTAY` (it stayed late, the penalty is applied) or `NO_ENTRY_RECORD` (the vehicle was never recorded
  inside). Open the barrier for both.

### POST /api/device/heartbeat : "I am alive"

```json
{ "deviceId": "gate-entry" }
```

Send it every 10 seconds. The dashboard shows **Online** if the device was heard in the last 30 seconds,
**Offline** otherwise (`app.device.online-seconds`). Every `/gate` call also counts.

### Errors

| HTTP | `code` | Meaning |
|---|---|---|
| 401 | `INVALID_DEVICE_KEY` | Header missing or key wrong |
| 403 | `DEVICE_API_DISABLED` | The server has no `DEVICE_API_KEY` |
| 400 | `VALIDATION_FAILED` / `INVALID_IDENTIFIER` | Empty or malformed plate or deviceId |

## 4. What the hardware must do when something goes wrong

- **Cannot reach the backend, or any error / timeout: keep the barrier CLOSED** and show
  "Use the manual gate". Never open by default. Security then types the plate in the web app's Gate page and
  lifts the barrier by hand.
- Use a timeout of about 3 to 5 seconds on the request.
- If the OCR is unsure about a plate, do not guess: show "Please see security". A wrong plate is refused
  (`UNREGISTERED`) and appears in the log of refused entries.

## 5. Visitors and manual entries (known gap)

A visitor has no registered plate, so the hardware gets `UNREGISTERED` and the barrier stays closed.
Security records the visitor on the **Gate** page (name, plate, reason). **The web page only records the
entry; it does not move the servo.** For now security lifts the barrier by hand. If the team wants the
servo to open for manual entries too, the backend can add a "pending open" command that the ESP32 checks with
each heartbeat. Ask for it if needed.

## 6. One ESP32 with the webcam (the setup we use)

We have one ESP32 and two webcams (entrance and exit). The webcams read the plates, and the ESP32 is the gate
controller: it asks the backend and moves the barrier.

```
webcam IN  ─┐
            ├─► tools/plate_gate.py (reads plates) ──USB──► ESP32 ──Wi-Fi──► backend
webcam OUT ─┘                                                  │
                                                  servo barrier, LEDs, buzzer
```

1. Open `esp32/gate_controller/gate_controller.ino` in the Arduino IDE. Install the libraries **ArduinoJson**
   (version 7) and **ESP32Servo**. Fill in the Wi-Fi name and password, the laptop's address (`BACKEND`) and the device
   key, then change the pin numbers to match the wiring. Upload it.
2. On the laptop, install the USB serial library once: `pip install pyserial` (in the same Python 3.12 environment
   as the camera libraries).
3. Find the ESP32's port: Windows Device Manager > Ports (COM & LPT), for example `COM5`.
4. Start the backend with `DEVICE_API_KEY` set to the same key as in the sketch.
5. Run `python tools/plate_gate.py --esp32 COM5`. The device key is not needed on the laptop in this mode.

What happens: the script sends `PLATE,IN,ABC1234` over the cable, the ESP32 asks the backend, then lifts the barrier
and answers `RESULT,1,message` (or `RESULT,0,message` and the barrier stays closed). The camera window shows the same
decision. The ESP32 sends its own heartbeat, so the Gate page shows one device, `gate-esp32`, as Online. The two
webcams are not listed there, because the backend only talks to the ESP32.

There is one barrier by default, so entry and exit both use it. A vehicle in the OUT camera's view is sent as `OUT` and goes through
the same barrier. If you have a second servo, connect it and set `SERVO_OUT_PIN` in the sketch: exits then lift their own barrier.

Wiring notes (check them with your teammate):
- Power the servo from 5 V, not the 3.3 V pin, and connect its ground to the ESP32's ground.
- If the ESP32 resets whenever the servo moves, the servo needs its own supply (shared ground).
- Opening the serial port restarts the ESP32, so the script waits 2 seconds after connecting.
- Close the Arduino IDE's Serial Monitor before running the script, because only one program can use the port.
- Test the ESP32 without the camera: open the Serial Monitor at 115200 baud, type `PLATE,IN,ABC1234` and press Enter.

## 7. Example ESP32 sketch (untested starting point)

Arduino IDE, board "ESP32S3 Dev Module". Libraries: **ArduinoJson**, **ESP32Servo**. Pin numbers are placeholders:
change them to your wiring. This shows only the backend call, the barrier and the LED. Reading the plate
(camera, RFID or keypad) goes in `readPlate()`.

```cpp
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <ESP32Servo.h>

const char* WIFI_SSID = "YOUR_WIFI";
const char* WIFI_PASS = "YOUR_WIFI_PASSWORD";
const char* BACKEND   = "http://192.168.1.20:8080";   // the laptop's address, NOT localhost
const char* DEVICE_KEY = "your-long-random-key";      // same as DEVICE_API_KEY on the server
const char* DEVICE_ID  = "gate-entry";
const char* DIRECTION  = "IN";                        // "OUT" on the exit device

const int SERVO_PIN = 18, LED_GREEN = 5, LED_RED = 6; // placeholders
Servo barrier;
unsigned long lastBeat = 0;

void openBarrier() { barrier.write(90); delay(5000); barrier.write(0); }

void showResult(bool allowed, const char* message) {
  digitalWrite(LED_GREEN, allowed); digitalWrite(LED_RED, !allowed);
  Serial.println(message);            // also draw it on the TFT here
}

// Returns true and fills 'allowed' / 'message' if the backend answered.
bool askBackend(const String& plate, bool& allowed, String& message) {
  HTTPClient http;
  http.setTimeout(4000);
  http.begin(String(BACKEND) + "/api/device/gate");
  http.addHeader("Content-Type", "application/json");
  http.addHeader("X-Device-Key", DEVICE_KEY);
  JsonDocument body;
  body["deviceId"] = DEVICE_ID; body["identifier"] = plate; body["direction"] = DIRECTION;
  String payload; serializeJson(body, payload);
  int code = http.POST(payload);
  if (code != 200) { http.end(); return false; }       // any problem: keep the barrier closed
  JsonDocument answer;
  deserializeJson(answer, http.getString());
  allowed = answer["allowed"] | false;
  message = String((const char*)(answer["message"] | ""));
  http.end();
  return true;
}

void heartbeat() {
  HTTPClient http;
  http.setTimeout(3000);
  http.begin(String(BACKEND) + "/api/device/heartbeat");
  http.addHeader("Content-Type", "application/json");
  http.addHeader("X-Device-Key", DEVICE_KEY);
  http.POST(String("{\"deviceId\":\"") + DEVICE_ID + "\"}");
  http.end();
}

String readPlate() { return ""; }   // TODO: camera + OCR, RFID or keypad. Return "" when nothing was read.

void setup() {
  Serial.begin(115200);
  pinMode(LED_GREEN, OUTPUT); pinMode(LED_RED, OUTPUT);
  barrier.attach(SERVO_PIN); barrier.write(0);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  while (WiFi.status() != WL_CONNECTED) delay(300);
}

void loop() {
  if (millis() - lastBeat > 10000) { heartbeat(); lastBeat = millis(); }
  String plate = readPlate();
  if (plate.length() > 0) {
    bool allowed = false; String message;
    if (askBackend(plate, allowed, message)) {
      showResult(allowed, message.c_str());
      if (allowed) openBarrier();
    } else {
      showResult(false, "Use the manual gate");
    }
  }
}
```

## 8. Security notes

- The device key is a shared secret on a local network. Keep it out of git and out of screenshots.
  Anyone who has it can send plates to the gate endpoint, so treat it like a password.
- Plain HTTP on a private Wi-Fi is fine for this miniature model. Do not expose the backend to the internet.
- A device key can only call `/api/device/**`. It cannot read users, change capacity or see the dashboard.
