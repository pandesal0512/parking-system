/*
  Gate controller (one ESP32 for the whole gate).

  The laptop's Python reader (tools/plate_gate.py --esp32 COMx) reads the plate with the webcam and sends
  one line over the USB cable:      PLATE,IN,ABC1234      (or OUT)
  This board asks the backend over Wi-Fi whether the vehicle may pass, then:
      allowed  -> green LED, short beep, the barrier (servo) lifts for a few seconds
      refused  -> red LED, long beep, the barrier stays closed
      no answer -> red LED, the barrier stays closed ("Use the manual gate")
  and sends one line back over USB:  RESULT,1,message   (or RESULT,0,message)
  It also sends the heartbeat every 10 seconds, so the Gate page shows this board as Online.

  Arduino IDE: board "ESP32S3 Dev Module" (or your board). Libraries: ArduinoJson (v7), ESP32Servo.
  Untested on real hardware: check the pin numbers against your wiring first.
*/
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <ESP32Servo.h>

// ---- change these ----------------------------------------------------------
const char* WIFI_SSID  = "YOUR_WIFI";
const char* WIFI_PASS  = "YOUR_WIFI_PASSWORD";
const char* BACKEND    = "http://192.168.1.20:8080";   // the laptop running the backend, NOT localhost
const char* DEVICE_KEY = "your-long-random-key";       // same as DEVICE_API_KEY on the backend
const char* DEVICE_ID  = "gate-esp32";

const int SERVO_PIN = 18;    // entrance barrier servo (power it from 5V, not from the 3.3V pin; share GND with the board)
const int SERVO_OUT_PIN = -1; // exit barrier servo. -1 = there is only one barrier, so entry and exit both use SERVO_PIN
const int LED_GREEN = 5;
const int LED_RED   = 6;
const int BUZZER    = -1;    // -1 = no buzzer connected
const int BARRIER_CLOSED_ANGLE = 0;
const int BARRIER_OPEN_ANGLE   = 90;
const unsigned long BARRIER_OPEN_MS = 5000;
// ---------------------------------------------------------------------------

Servo barrier;      // entrance (or the only barrier)
Servo barrierOut;   // exit, only when SERVO_OUT_PIN is set
unsigned long lastBeat = 0;

void beep(int ms) {
  if (BUZZER < 0) return;
  digitalWrite(BUZZER, HIGH); delay(ms); digitalWrite(BUZZER, LOW);
}

// Put the message on a screen here later (TFT or OLED). For now it goes to the LEDs only.
void showResult(bool allowed, const String& message) {
  digitalWrite(LED_GREEN, allowed ? HIGH : LOW);
  digitalWrite(LED_RED, allowed ? LOW : HIGH);
}

void openBarrier(const String& direction) {
  Servo& arm = (direction == "OUT" && SERVO_OUT_PIN >= 0) ? barrierOut : barrier;
  arm.write(BARRIER_OPEN_ANGLE);
  delay(BARRIER_OPEN_MS);
  arm.write(BARRIER_CLOSED_ANGLE);
}

// Returns true when the backend answered. Any other outcome (no Wi-Fi, timeout, error) returns false.
bool askBackend(const String& plate, const String& direction, bool& allowed, String& message) {
  if (WiFi.status() != WL_CONNECTED) return false;
  HTTPClient http;
  http.setTimeout(4000);
  http.begin(String(BACKEND) + "/api/device/gate");
  http.addHeader("Content-Type", "application/json");
  http.addHeader("X-Device-Key", DEVICE_KEY);
  JsonDocument body;
  body["deviceId"] = DEVICE_ID;
  body["identifier"] = plate;
  body["direction"] = direction;
  String payload;
  serializeJson(body, payload);
  int code = http.POST(payload);
  if (code != 200) { http.end(); return false; }
  JsonDocument answer;
  deserializeJson(answer, http.getString());
  allowed = answer["allowed"] | false;
  message = String((const char*)(answer["message"] | ""));
  http.end();
  return true;
}

void heartbeat() {
  if (WiFi.status() != WL_CONNECTED) return;
  HTTPClient http;
  http.setTimeout(3000);
  http.begin(String(BACKEND) + "/api/device/heartbeat");
  http.addHeader("Content-Type", "application/json");
  http.addHeader("X-Device-Key", DEVICE_KEY);
  http.POST(String("{\"deviceId\":\"") + DEVICE_ID + "\"}");
  http.end();
}

void handleLine(String line) {
  line.trim();
  if (!line.startsWith("PLATE,")) return;            // ignore anything else
  int second = line.indexOf(',', 6);
  if (second < 0) return;
  String direction = line.substring(6, second);
  String plate = line.substring(second + 1);
  if ((direction != "IN" && direction != "OUT") || plate.length() == 0) return;

  bool allowed = false;
  String message;
  if (!askBackend(plate, direction, allowed, message)) {
    allowed = false;
    message = "Use the manual gate";
  }
  message.replace('\n', ' ');
  message.replace('\r', ' ');

  showResult(allowed, message);
  Serial.println(String("RESULT,") + (allowed ? "1" : "0") + "," + message);   // the answer goes back to Python
  if (allowed) {
    beep(150);
    openBarrier(direction);
  } else {
    beep(600);
  }
  digitalWrite(LED_GREEN, LOW);
  digitalWrite(LED_RED, LOW);
}

void setup() {
  Serial.begin(115200);
  pinMode(LED_GREEN, OUTPUT);
  pinMode(LED_RED, OUTPUT);
  if (BUZZER >= 0) pinMode(BUZZER, OUTPUT);
  barrier.attach(SERVO_PIN);
  barrier.write(BARRIER_CLOSED_ANGLE);
  if (SERVO_OUT_PIN >= 0) {
    barrierOut.attach(SERVO_OUT_PIN);
    barrierOut.write(BARRIER_CLOSED_ANGLE);
  }
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  unsigned long started = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - started < 20000) delay(300);   // red LED = no Wi-Fi yet
  digitalWrite(LED_RED, WiFi.status() == WL_CONNECTED ? LOW : HIGH);
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) WiFi.reconnect();
  if (millis() - lastBeat > 10000) { heartbeat(); lastBeat = millis(); }
  if (Serial.available()) handleLine(Serial.readStringUntil('\n'));
}
