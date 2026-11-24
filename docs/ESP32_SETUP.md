# ESP32 gate controller setup (Arduino IDE, Windows)

This guide sets up the ESP32 board that opens the barrier. It covers installing the Arduino IDE, the board
and libraries, the sketch settings, uploading, testing, and running it together with the camera reader.

## How the parts work together

1. The **laptop camera** reads the plate, and `tools/plate_gate.py --esp32 COMx` sends it to the board over USB.
2. The **ESP32** asks the backend over Wi-Fi whether the vehicle may pass.
3. If **allowed**, the board lights the green LED, beeps briefly, and lifts the barrier (servo) for about 5 seconds.
   If **refused**, or if the backend can't be reached, the red LED lights and the barrier stays closed.
4. The board sends the result back to the laptop over USB, and it sends a heartbeat every 10 seconds so the
   dashboard shows it as Online.

The laptop, the backend, and the board must all be on the same Wi-Fi network.

## What you need

- An ESP32 board (the sketch is written for the **ESP32-S3 Dev Module**)
- A USB cable that carries data, not charge-only
- A servo for the barrier (SG90 or similar), plus two LEDs (green and red) and resistors
- Optional: a buzzer
- A Wi-Fi network: your router, or a phone hotspot
- The sketch: `esp32_gate_controller.zip`, unzipped to `esp32\gate_controller\gate_controller.ino`

## 1. Install the Arduino IDE

Download the Arduino IDE from arduino.cc and install it. This is a separate program from IntelliJ, so it has its
own menus. Its settings are under **File > Preferences**.

## 2. Install the ESP32 board package

1. Open **Tools > Board > Boards Manager**.
2. Search for `esp32`.
3. Install the package by **Espressif Systems** (version 3.3.12 or newer).

Do not install the "Arduino ESP32 Boards" package. It's for a different set of boards.

In **File > Preferences**, the **Additional boards manager URLs** box should be empty. The official Espressif package
doesn't need a URL.

## 3. Choose the board

1. Click the **Select Board** dropdown at the top of the window.
2. Type `ESP32S3` and choose **ESP32S3 Dev Module**.

If your board is a different ESP32 model, choose that one instead.

## 4. Install the libraries

Open the Library Manager (the book icon in the left sidebar) and install:

- **ArduinoJson**, version **7** (the sketch uses version 7 syntax)
- **ESP32Servo**

## 5. Open the sketch

Go to **File > Open** and select `gate_controller.ino` from `esp32\gate_controller`. Arduino IDE expects the file
name to match its folder name, so keep the folder and file names as they are.

## 6. Change the settings at the top of the sketch

| Setting | What to put there |
|---|---|
| `WIFI_SSID` | The Wi-Fi name (or the phone hotspot name) |
| `WIFI_PASS` | The Wi-Fi password |
| `BACKEND` | `http://` + the laptop's IPv4 address + `:8080`, for example `http://192.168.1.20:8080`. Run `ipconfig` to find it. Never use `localhost`. |
| `DEVICE_KEY` | The same value as `DEVICE_API_KEY` on the backend |
| `SERVO_PIN` | The GPIO pin the servo signal wire is connected to (default 18) |
| `LED_GREEN`, `LED_RED` | The GPIO pins the LEDs are connected to (defaults 5 and 6) |
| `BUZZER` | The GPIO pin of the buzzer, or `-1` if there isn't one |

Leave `SERVO_OUT_PIN` at `-1` if you have one barrier. Use a second servo pin only if you have a separate exit barrier.

The sketch's `DEVICE_KEY` must match the backend exactly, with no spaces. If it doesn't, the backend refuses
the request and the board keeps the barrier closed.

## 7. Wire the parts

- **Servo:** signal wire to `SERVO_PIN`. Power it from **5V** (not the 3.3V pin), and connect its ground to the board's ground.
- **LEDs:** each LED goes through a resistor (about 220 to 330 ohms) to its pin, and then to ground.
- **Buzzer:** connect it to `BUZZER`, or leave `BUZZER` at `-1`.

Share a common ground between the board and the servo power supply. Without it, the servo can behave erratically.

## 8. Upload

1. Plug the board into the laptop with the USB cable.
2. Go to **Tools > Port** and note the COM port (for example `COM5`).
3. Click the **Upload** arrow.
4. If the upload fails at "Connecting...", hold the board's **BOOT** button while the upload starts, then release it.

## 9. Test the board

1. Open **Tools > Serial Monitor** and set the baud rate to **115200** (bottom right).
2. Watch the red LED. It stays on while the board connects to Wi-Fi (up to 20 seconds). When it goes off, the board is connected.
3. In the box at the top of the Serial Monitor, type `PLATE,IN,CLICK125` and press Enter.
4. You should see one line back:
   - `RESULT,1,...`: allowed. The green LED lights and the barrier lifts for about 5 seconds.
   - `RESULT,0,...`: refused. The red LED lights, and the buzzer sounds for longer if you have one.

Close the Serial Monitor before running the camera reader. Only one program can use the COM port at a time.

## 10. Run the camera reader with the board

Make sure ParkingApplication is running, then in the IntelliJ terminal run:

```
C:\Users\Asus\pyenv\camera\Scripts\python.exe tools\plate_gate.py --esp32 COM5
```

Replace `COM5` with your port. With `--esp32`, the script doesn't ask for the device key, because the key is stored in the sketch.

Hold a registered plate in front of the camera, steady for two or three seconds. The Run panel prints the result,
and the board moves the barrier.

## Using a phone hotspot

A phone can replace the Wi-Fi router, as long as the board, the laptop, and the phone's hotspot are all connected.

1. Turn on the hotspot on the phone. On Android: Settings > Network & internet > Hotspot & tethering. On iPhone: Settings > Personal Hotspot.
2. Connect the laptop to the hotspot.
3. Set `WIFI_SSID` and `WIFI_PASS` in the sketch to the hotspot's name and password.
4. The ESP32 needs **2.4 GHz** Wi-Fi. On an iPhone, turn on **Maximize Compatibility**. On Android, choose the 2.4 GHz band if the option is offered.
5. Run `ipconfig` on the laptop again, because the hotspot gives it a new IP address. Update `BACKEND` in the sketch and upload again.
6. If Windows asks about the new network, allow Java on it.

Keep the phone plugged in during testing, since hotspots use a lot of battery. Some carriers block hotspot use.

## Troubleshooting

| What you see | Cause | Fix |
|---|---|---|
| Upload fails at "Connecting..." | The board isn't in download mode | Hold **BOOT** while the upload starts, then release it |
| Red LED stays on | No Wi-Fi connection | Check the network name and password, and that it's 2.4 GHz |
| `RESULT,0,Use the manual gate` | The board can't reach the backend | Check that ParkingApplication is running, the `BACKEND` IP is correct, and the laptop and board are on the same network. Allow Java through the Windows firewall |
| `RESULT,0` for a registered plate | The device key doesn't match | Make `DEVICE_KEY` in the sketch exactly equal to `DEVICE_API_KEY` in the backend |
| Barrier doesn't move | Servo wiring or power | Power the servo from 5V, share the ground, and check the `SERVO_PIN` number |
| LEDs don't respond | Wrong pin numbers | Check `LED_GREEN` and `LED_RED` against your wiring |
| "Access is denied" or port busy | Another program is using the COM port | Close the Serial Monitor and any other program using the port |
| Board not in the port list | Charge-only USB cable, or a driver problem | Try a different cable. Unplug and replug the board |

## Checklist

- [ ] Arduino IDE installed, with the Espressif esp32 package and the ESP32S3 Dev Module board selected
- [ ] ArduinoJson (version 7) and ESP32Servo installed
- [ ] `WIFI_SSID`, `WIFI_PASS`, `BACKEND`, and `DEVICE_KEY` set in the sketch
- [ ] Sketch uploaded, and the red LED goes off once connected
- [ ] `PLATE,IN,CLICK125` gives a `RESULT` line in the Serial Monitor
- [ ] Serial Monitor closed before running `plate_gate.py --esp32 COMx`
