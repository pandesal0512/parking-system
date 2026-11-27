/*
  Gate board with an allowed-plates list (no Wi-Fi needed).

  The laptop runs:  python tools/plate_gate.py --esp32 COM5 --allowlist
  It sends this board the plates that may enter right now. When a camera reads a plate the laptop sends
  PLATE,IN,ABC1234 and this board decides by itself, using its list, and moves the barrier.

  Works on an Arduino Uno / Nano and on an ESP32. Serial Monitor: 115200 baud, "Newline".

  Messages from the laptop:
    LIST_BEGIN / P,<plate> ... / LIST_END   the new list of allowed plates
    PLATE,<IN|OUT>,<plate>                  a plate was read; answered with RESULT,<1|0>,<message>
  Safety: if no new list arrives for 60 seconds the list is forgotten and entries are refused.
  Exits are always allowed (the car is already inside).
*/
#if defined(ESP32)
  #include <ESP32Servo.h>
#else
  #include <Servo.h>
#endif

// ---- change these ----
const int SERVO_PIN = 9;          // barrier servo signal wire
const int LED_GREEN = 6;
const int LED_RED   = 7;
const int BUZZER    = -1;         // -1 = no buzzer
const int ANGLE_CLOSED = 0;
const int ANGLE_OPEN   = 90;
const unsigned long BARRIER_OPEN_MS = 5000;
const unsigned long LIST_TTL_MS = 60000;
// ----------------------

const int MAX_PLATES = 20;
const int PLATE_LEN = 13;         // 12 characters plus the end marker

char plates[MAX_PLATES][PLATE_LEN];
char staging[MAX_PLATES][PLATE_LEN];
int plateCount = 0, stagingCount = 0;
bool receiving = false;
unsigned long listAt = 0;
bool haveList = false;

Servo barrier;
bool barrierOpen = false;
unsigned long closeAt = 0;
unsigned long ledOffAt = 0;

char line[48];
int lineLen = 0;

void setup() {
  Serial.begin(115200);
  pinMode(LED_GREEN, OUTPUT);
  pinMode(LED_RED, OUTPUT);
  if (BUZZER >= 0) pinMode(BUZZER, OUTPUT);
  barrier.attach(SERVO_PIN);
  barrier.write(ANGLE_CLOSED);
  Serial.println("READY");
}

void loop() {
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n' || c == '\r') {
      if (lineLen > 0) { line[lineLen] = 0; handleLine(line); lineLen = 0; }
    } else if (lineLen < (int)sizeof(line) - 1) {
      line[lineLen++] = c;
    }
  }
  unsigned long now = millis();
  if (barrierOpen && (long)(now - closeAt) >= 0) { barrier.write(ANGLE_CLOSED); barrierOpen = false; }
  if (ledOffAt && (long)(now - ledOffAt) >= 0) {
    digitalWrite(LED_GREEN, LOW); digitalWrite(LED_RED, LOW);
    if (BUZZER >= 0) digitalWrite(BUZZER, LOW);
    ledOffAt = 0;
  }
  if (haveList && now - listAt > LIST_TTL_MS) { plateCount = 0; haveList = false; }   // stale: forget it
}

void handleLine(char *s) {
  if (strcmp(s, "LIST_BEGIN") == 0) { receiving = true; stagingCount = 0; return; }
  if (strcmp(s, "LIST_END") == 0) {
    if (receiving) {
      for (int i = 0; i < stagingCount; i++) strcpy(plates[i], staging[i]);
      plateCount = stagingCount; haveList = true; listAt = millis(); receiving = false;
    }
    return;
  }
  if (strncmp(s, "P,", 2) == 0) {
    if (receiving && stagingCount < MAX_PLATES) {
      strncpy(staging[stagingCount], s + 2, PLATE_LEN - 1);
      staging[stagingCount][PLATE_LEN - 1] = 0;
      stagingCount++;
    }
    return;
  }
  if (strncmp(s, "PLATE,", 6) == 0) {
    char *dir = s + 6;
    char *comma = strchr(dir, ',');
    if (!comma) return;
    *comma = 0;
    decide(dir, comma + 1);
  }
}

int indexOfPlate(const char *p) {
  for (int i = 0; i < plateCount; i++) if (strcmp(plates[i], p) == 0) return i;
  return -1;
}

void decide(const char *dir, const char *plate) {
  bool entering = strcmp(dir, "IN") == 0;
  if (!entering) { grant("Exit allowed"); return; }
  if (!haveList) { refuse("No list from the laptop. Use the manual gate."); return; }
  int i = indexOfPlate(plate);
  if (i < 0) { refuse("Not on the allowed list"); return; }
  for (int j = i; j < plateCount - 1; j++) strcpy(plates[j], plates[j + 1]);   // one entry per list
  plateCount--;
  grant("Welcome");
}

void grant(const char *msg) {
  digitalWrite(LED_GREEN, HIGH); digitalWrite(LED_RED, LOW);
  if (BUZZER >= 0) digitalWrite(BUZZER, HIGH);
  barrier.write(ANGLE_OPEN); barrierOpen = true; closeAt = millis() + BARRIER_OPEN_MS;
  ledOffAt = millis() + BARRIER_OPEN_MS;
  Serial.print("RESULT,1,"); Serial.println(msg);
}

void refuse(const char *msg) {
  digitalWrite(LED_RED, HIGH); digitalWrite(LED_GREEN, LOW);
  ledOffAt = millis() + 2000;
  Serial.print("RESULT,0,"); Serial.println(msg);
}
