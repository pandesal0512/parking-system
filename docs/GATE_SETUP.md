# Gate setup, step by step

Everything needed to get the plate reader, the ESP32 and the backend working together. Do the parts in order. The other guides go deeper: `PYTHON_SETUP.md` (Python and the camera libraries) and `HARDWARE.md` (the device API).

## What connects to what

```
webcam IN  ─┐
            ├─► tools/plate_gate.py (reads plates) ──USB──► ESP32 ──Wi-Fi──► backend (the laptop)
webcam OUT ─┘                                                  │
                                                 servo barrier, LEDs, buzzer
```

The backend never talks to a camera. It only receives a plate as text. The Python script reads the plate, the ESP32 asks the backend and moves the barrier.

## Part 1: Choose the device key

The device key is a secret that the backend and the ESP32 both know. Pick any long text, for example `my-demo-key-2026-gate`. Write it down, you need it in two places.

## Part 2: Start the backend with the key

In IntelliJ: Run > Edit Configurations, open the Spring Boot run configuration, and add this to the environment variables, next to the ones already there:

```
DEVICE_API_KEY=my-demo-key-2026-gate
```

Run the backend. Find the laptop's address: open PowerShell, type `ipconfig`, and note the IPv4 Address (for example `192.168.1.20`). The ESP32 will use this address. Windows may ask to allow Java on private networks the first time, so allow it.

## Part 3: Set up the Arduino IDE (once)

1. Install the Arduino IDE (version 2) from arduino.cc.
2. File > Preferences. In Additional boards manager URLs paste:
   `https://espressif.github.io/arduino-esp32/package_esp32_index.json`
3. Tools > Board > Boards Manager. Search `esp32` and install the one by Espressif Systems.
4. Sketch > Include Library > Manage Libraries. Install `ArduinoJson` (by Benoit Blanchon, version 7) and `ESP32Servo`.

## Part 4: Wire the ESP32

Pins are up to you. Whatever GPIO pin you plug a wire into, you write that number in the sketch (Part 5).

| Part | Wiring |
|---|---|
| Servo (the barrier) | signal wire to a GPIO pin, red to 5V, brown or black to GND |
| Green LED | GPIO pin, then a resistor of about 220 ohms, then the LED, then GND |
| Red LED | the same, on another pin |
| Buzzer (optional) | GPIO pin and GND |
| Second servo (optional) | for a separate exit barrier, same as the first servo on another pin |

- Connect the servo's ground to the ESP32's ground.
- If the ESP32 restarts whenever the servo moves, give the servo its own 5V supply (with the grounds still joined).
- On the ESP32-S3, avoid pins 0, 19, 20, 45 and 46 (boot and USB).

## Part 5: Fill in and upload the sketch

1. File > Open, choose `esp32/gate_controller/gate_controller.ino`.
2. Tools > Board > esp32, then your exact board (for example ESP32S3 Dev Module). Plug it in and choose it under Tools > Port.
3. Edit the lines near the top, between "change these":

   | Line | What to put |
   |---|---|
   | `WIFI_SSID`, `WIFI_PASS` | your Wi-Fi name and password. The ESP32 works only on 2.4 GHz, not 5 GHz. A phone hotspot is fine if it is set to 2.4 GHz. |
   | `BACKEND` | `http://` + the laptop's address from Part 2 + `:8080`, for example `http://192.168.1.20:8080`. Never `localhost`. |
   | `DEVICE_KEY` | the key from Part 1 |
   | `SERVO_PIN`, `LED_GREEN`, `LED_RED`, `BUZZER` | the GPIO numbers you wired. `BUZZER = -1` means none. |
   | `SERVO_OUT_PIN` | the pin of a second servo for the exit barrier. Leave `-1` if there is only one barrier. |

4. Press the Upload arrow. If it cannot connect, hold the board's BOOT button while it says "Connecting...".
5. Test the board alone: Tools > Serial Monitor at 115200 baud. Type `PLATE,IN,ABC1234` and press Enter. A refused plate gives the red LED and `RESULT,0,...`. A plate with a reservation gives the green LED, a beep, the barrier lifting and `RESULT,1,...`.
6. Close the Serial Monitor. Only one program can use the USB port.

## Part 6: Python and the camera (once)

Follow `PYTHON_SETUP.md` to install Python 3.12 and the camera libraries. Then also install the USB library:

```
C:\Users\Asus\pyenv\camera\Scripts\python.exe -m pip install pyserial
```

(Use your own folder if it is different.) Find the ESP32's port: Windows Device Manager > Ports (COM & LPT), for example `COM5`.

## Part 7: Run it (one laptop)

1. The backend is running (Part 2).
2. In IntelliJ, edit the Python run configuration for `tools\plate_gate.py`. In Parameters put `--esp32 COM5`. No device key is needed on the laptop in this mode.
3. Run it. The first time it downloads the OCR model, so wait for "Ready. Hold a plate in front of a camera."
4. Hold a registered plate with an active reservation in front of the camera. After two matching reads you see OPEN BARRIER, the green LED lights and the barrier lifts.
5. Press Q in a camera window to stop.

Cameras: camera 0 is the entrance (IN) and camera 1 is the exit (OUT). A phone can be a camera too (see "Using a phone as a camera" below). To check which camera number is which, run the script with `--list-cameras`. To swap them, add `--in-cam 1 --out-cam 0`.

## Part 8: The exit

The camera decides the direction. A plate seen by the IN camera is sent as an entry, and one seen by the OUT camera as an exit. The ESP32 handles both the same way.

- **Two webcams, one ESP32:** nothing more to do. Entry and exit share one barrier. With a second servo, set `SERVO_OUT_PIN` and exits lift their own barrier.
- **Only the laptop camera:** use `--out-cam -1`, and press I in the camera window to switch it between IN and OUT. Show the plate as IN, press I, then show it again as OUT.

## Part 9: Two laptops (one for entry, one for exit)

Only one laptop runs the backend (call it A). The other (B) is just a second camera reader. Both must be on the same Wi-Fi.

- **Laptop A** has the backend and the ESP32 on USB. Run:
  `--in-cam 0 --out-cam -1 --esp32 COM5`
- **Laptop B** has the exit camera. Run, with A's address from Part 2:
  `--in-cam -1 --out-cam 0 --backend http://192.168.1.20:8080`
  and set the same `DEVICE_API_KEY` environment variable on B (or the script asks for it).

Exits from B are recorded and shown on B's screen, and appear on the Gate page as the device `gate-exit`. The barrier only lifts for entries, because the ESP32 is plugged into A.

## Using a phone as a camera

A phone can be the exit camera (or the entrance one). Two ways, both need the phone and the laptop on the same Wi-Fi.

**A. A webcam app (easiest).** Install a phone-as-webcam app such as Iriun Webcam or DroidCam on the phone and its small program on the laptop, then connect them. The phone then shows up as one more camera number. Run `python tools/plate_gate.py --list-cameras`. The new number is the phone, for example `2`. Use it like any camera: `--out-cam 2`. On Windows 11 with an Android phone, "Link to Windows" can also make the phone appear as a camera.

**B. A stream address (an IP camera app).** Install an app such as "IP Webcam" (Android) and start its server. It shows an address on the phone screen, for example `http://192.168.1.30:8080`. Use the video address of it:

```
--out-cam http://192.168.1.30:8080/video
```

Open that address in the laptop's browser first. If you see the video there, the script can read it too.

Tips: put the phone in a stand, turn the screen timeout off, keep it charging, and give it good light with the plate straight in front of it. A phone camera is usually sharper than a laptop webcam, which helps the plate reading. Network cameras can lag a little. If the picture seems seconds old, move the phone closer to the router.

## Option: the board decides from a list of allowed plates

Instead of the board asking the backend for every plate, the laptop gives the board the list of plates that may enter right now (approved vehicle, active owner, not suspended, not already inside, reservation valid now). The board opens the barrier by itself when a plate on the list is read. This needs no Wi-Fi on the board, so it also works on an Arduino Uno or Nano.

1. Backend running with `DEVICE_API_KEY` set (Parts 1 and 2).
2. Open `esp32/gate_allowlist/gate_allowlist.ino`, set the pins at the top, choose your board and port, upload. (On an ESP32 install `ESP32Servo`; on an Uno the built-in `Servo` library is enough.)
3. Close the Serial Monitor. Run the reader with the list option:
   `--esp32 COM5 --allowlist` and set the `DEVICE_API_KEY` environment variable on the laptop (or the script asks).
4. The laptop refreshes the list every 5 seconds. The board keeps up to 20 plates. If the laptop stops updating for 60 seconds the board forgets the list and refuses entries. Exits are always allowed.
5. Every plate is also reported to the backend, so the Gate page log still shows entries, exits and refusals. If the board opened but the backend would have refused (for example the list was a few seconds old), the script prints a WARNING.

To see the list without a board: `python tools/plate_sync.py --show`.

## Without any hardware

- The Gate page lets the parking manager let a vehicle in or out by hand. It uses the same rules.
- `python tools/fake_gate.py --url http://localhost:8080 --key my-demo-key-2026-gate` pretends to be the hardware. Type a plate and it prints OPEN THE BARRIER or KEEP IT CLOSED.
- `python camera_test.py` only checks that the camera and the plate reading work.

## If something does not work

| What you see | What to check |
|---|---|
| The sketch does not upload | Choose the right board and port. Hold BOOT while it says "Connecting...". Try another USB cable (some are charge-only). |
| The red LED stays on after power-up | The ESP32 did not join the Wi-Fi. Check the name and password, and that it is 2.4 GHz. |
| Serial Monitor shows `RESULT,0,Use the manual gate` | The ESP32 cannot reach the backend. Check the address in `BACKEND`, that both are on the same Wi-Fi, that the backend is running, and the Windows firewall prompt for Java. |
| It answers but the message says the key is wrong (`Server refused`) | `DEVICE_KEY` in the sketch must equal `DEVICE_API_KEY` on the backend. |
| The Python script says the port is busy | Close the Arduino Serial Monitor. |
| The Python script says the ESP32 did not answer | Wrong COM port, or the sketch is not uploaded. Test the board in the Serial Monitor first. |
| The barrier does not move | Check the servo pin number and that it has 5V and a shared ground. |
| The Gate page shows no device | Nothing has contacted the backend yet. The board sends its first heartbeat within 10 seconds of joining the Wi-Fi. |
| The camera reads the wrong plate | More light, the plate closer and straight on, a bigger print. |
| Registered plate, but KEEP CLOSED | The vehicle must be approved and have a reservation that covers right now. The message says which. |
