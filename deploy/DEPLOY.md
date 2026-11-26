# Running the parking system without IntelliJ

This is the simple, one-laptop setup for the defense and the trial period. The laptop runs the backend and the database, and the gate laptop runs the plate reader.

## What to install (once, on each laptop)

| Laptop | Install |
|---|---|
| Server laptop | Java 21 (Java runtime), MySQL 8 or MariaDB |
| Gate laptop | Python 3.12, packages from `docs/PYTHON_SETUP.md` |

If the server and gate are on the same laptop, install both on that laptop.

## Step 1: Build the program (done once, by a developer, on a laptop with Maven and Java 21)

Open a terminal in the project folder and run:

    mvn clean package -DskipTests

This creates `target/parking-system-0.1.0.jar`. Copy that file into this `deploy` folder, next to `run-server.bat`.

## Step 2: Create the database (done once)

Follow `docs/DATABASE_SETUP.md`. The result must be a database named `parking_db` and a user named `parking_app` with the password you chose.

## Step 3: Set the values

Open `run-server.bat` and `run-gate.bat` in Notepad and replace every `CHANGE_ME`:
- the database password,
- a long random `JWT_SECRET`,
- `SEED_ADMIN_PASSWORD`,
- `DEVICE_API_KEY` (the same value in both files).

Do not share these files or put them in GitHub.

## Step 4: Start

1. Double-click `run-server.bat`. Wait until the window shows that the app has started. The first start creates the tables and the super admin account.
2. Open http://localhost:8080 in a browser and sign in as `superadmin` with the password from Step 3.
3. Double-click `run-gate.bat` on the gate laptop. Use the phone address instead of `--out-cam 1` if the phone is the exit camera, for example `--out-cam http://192.168.0.151:8080/video`.

## Other laptops and phones

Other laptops on the same Wi-Fi open `http://<server laptop's IP>:8080`, for example `http://192.168.0.151:8080`. Set `PARKING_BACKEND` in `run-gate.bat` to that address when the gate laptop is different from the server.

## Known gaps for this setup

- The ESP32 still needs to be flashed once with the Arduino IDE (see `docs/GATE_SETUP.md`). Then plug it in and start the gate with `--esp32 COMx`.
- The Java and Python steps are not tested on a fresh laptop yet. Test the full steps once on a clean machine before the defense.
