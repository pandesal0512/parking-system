# IoT Campus Parking System

Mabini Colleges Inc. A parking system for the campus lot. Students, faculty and personnel register one vehicle and reserve a space ahead of time. At the gate, a camera reads the plate and the system decides whether the barrier opens. Parking managers watch the lot from a web page, and the super admin looks after accounts, capacity and penalties.

The project has three parts that run together:

- a Java / Spring Boot backend with a database (MySQL, or H2 for quick tests)
- a plain HTML, CSS and JavaScript front end, served by the same Spring Boot app
- the gate hardware: webcams with a Python plate reader (or an ESP32), which call the backend with a device key

It runs on one laptop at http://localhost:8080. Nothing is deployed anywhere.

## Who uses it

| Role | What they do |
|---|---|
| User (student, faculty, personnel) | Registers one vehicle, reserves parking, reads their own log book. Students also see their warnings. |
| Visitor | Has no account and no reservation. The parking manager lets them in at the gate by hand. |
| Parking manager | Runs the gate, approves vehicles, watches the dashboard, looks things up. |
| Super admin | Creates manager accounts, resets passwords, deactivates users, clears penalties, edits capacity. |

People can only register as users. Manager accounts are created by the super admin inside the app.

## How it works

1. A user registers and adds a vehicle (plate and type). A parking manager approves it.
2. The user reserves a day, or several days, on a calendar. With the default settings the reservation is approved straight away, as long as the vehicle is approved and there is room.
3. At the gate the camera reads the plate. The backend checks that the vehicle is registered and approved, the owner is active and not suspended, the vehicle is not already inside, and there is a reservation for right now. If everything is fine it answers "allowed" and the barrier opens.
4. The exit camera does the same for leaving, and the visit is closed in the log.
5. A background check runs every minute. A reservation that ended without the vehicle ever entering is a no-show. A vehicle still inside long after its reservation ended is an overstay. Students get penalties for both.

If the cameras or the hardware are down, the manager uses the Gate page to let vehicles in and out by hand.

## What each person sees

**User**
- My vehicle: register one vehicle, edit it (a new plate or type needs approval again).
- Reservations: live space rings for each vehicle type, a calendar to reserve one or several days, and a list of their reservations with a cancel button.
- Log book: their own entries and exits.
- Warnings: students only.

**Parking manager**
- Dashboard: space rings for each vehicle type, a Today list (reservations starting today, vehicles that entered, vehicles inside past their reservation, refused entries, vehicles waiting for approval). Each number has a Show who button that lists the vehicles. It refreshes every 10 seconds.
- Gate: whether each camera is online, one table of vehicles in and out with a Record exit button, the visitor form and the manual override form.
- Vehicles: approve, reject or revoke vehicles. The Reservations tab lists everyone's reservations for one day.
- Activity: the full history with filters (who, vehicle, dates), refused entries, and a download.
- Users: Accounts (search, see warnings and suspensions) and Penalties.

**Super admin**
- The same pages, except Gate (only the parking manager uses it).
- Vehicles > Capacity: change the number of spaces for each area and vehicle type.
- Users > Accounts: reset a password, deactivate or reactivate, clear penalties.
- Users > Staff accounts: create and manage parking manager accounts.
- Demo time: only when the demo setting is on.

Settings (account menu, bottom of the sidebar) has Light, Dark or System appearance, the profile, and password change. Every password field has a Show / Hide button.

## Rules

Most of these are in `application.properties` and can be changed there.

| Rule | Value |
|---|---|
| Capacity, general lot | motorcycle 60, car 33, tricycle / e-trike 8 |
| Capacity, faculty parking | car 12, motorcycle 0, tricycle 0 |
| Reservations per day | 1 per user, counted by the day the reservation starts (`app.reservation.max-per-day`) |
| Longest reservation | 10 hours (`app.reservation.max-hours`) |
| How far ahead | 7 days (`app.reservation.max-days-ahead`) |
| Early entry | 15 minutes before the start (`app.reservation.grace-minutes`) |
| Cancelling | up to 60 minutes before the start (`app.reservation.cancel-cutoff-minutes`); after that only a manager can |
| Approval of reservations | off, they are approved immediately (`app.reservation.auto-approve=true`) |
| Approval of vehicles | on, a manager approves each new vehicle (`VEHICLE_AUTO_APPROVE=true` turns it off) |
| Space check | a reservation is allowed when the busiest moment in its window is below capacity, so bookings in different hours do not block each other |
| No-show | the reservation ends and the vehicle never entered: 1 warning (students only) |
| Too many no-shows | at 3 warnings the user can no longer reserve (`app.penalty.no-show-ban-threshold`) |
| Overstay | still inside 15 minutes after the reservation ends: cannot reserve or enter for 3 days (`app.penalty.overstay-suspension-days`) |
| Faculty and personnel | never get warnings, suspensions or bans. A missed reservation is still marked No show. |
| Visitors | name, vehicle type, plate number and the reason for the visit are all required |
| Logs kept | 30 days, cleaned every night at 3 AM (`app.logs.retention-days`) |
| Wrong passwords | 5 tries, then the account is locked for 15 minutes |

### Faculty parking

Faculty and personnel have their own area in the lot, counted separately from the general lot. Students and visitors always use the general lot. Faculty and personnel use the faculty area for any vehicle type that has spaces there. By default that is cars only. A faculty member with a motorcycle uses the general lot, because the faculty number for motorcycles is 0. The super admin can change any of these numbers under Vehicles > Capacity. The area is decided when the reservation is made and stays with it.

## Gate hardware

The gate reader is a Python script that uses two webcams, one for the entrance (IN) and one for the exit (OUT). It reads plates by itself, with no key presses. A plate is sent to the backend only after it is read the same way twice in a row, and the same plate is ignored for 15 seconds after that, so a car waiting in front of the camera is not sent again and again. The screen shows OPEN BARRIER or KEEP CLOSED.

Each camera has its own device id (`gate-entry`, `gate-exit`) and shows as Online on the Gate page when the backend has heard from it in the last 30 seconds.

The full step-by-step guide (device key, Arduino IDE, wiring, running, one or two laptops, problems) is `docs/GATE_SETUP.md`. Quick start:

1. Choose a secret and set it as `DEVICE_API_KEY` on the backend. Without it the hardware endpoints stay off.
2. Install Python 3.12 and the libraries (`docs/PYTHON_SETUP.md` has every step for Windows and IntelliJ).
3. Start the backend, then run `python tools/plate_gate.py`. Set the same key in the `DEVICE_API_KEY` environment variable, or the script asks for it.

Camera 0 is the entrance and camera 1 is the exit. If your laptop camera and USB camera come out the wrong way round, run `python tools/plate_gate.py --list-cameras`, then `--in-cam 1 --out-cam 0`. With only the laptop camera, run it with `--out-cam -1` and press I to switch between IN and OUT.

With an ESP32 doing the physical part, run it with `--esp32 COM5`. The plates go to the board over USB, and the board asks the backend, lifts the servo barrier, lights the LEDs and sends the heartbeat. `esp32/gate_controller/gate_controller.ino` is the sketch and `docs/HARDWARE.md` (section 6) has the wiring notes. We have one ESP32, so entry and exit share one barrier.

Other files:

- `camera_test.py` checks that the camera and the plate reading work. It does not talk to the backend.
- `tools/fake_gate.py` pretends to be the hardware, so the gate rules can be tried without a camera or an ESP32.
- `docs/HARDWARE.md` describes the device API and has an example ESP32 sketch.

## Penalties

**No-show.** The reservation stays active until it ends. The driver can come in at any time from 15 minutes before the start until the end. If the reservation ends and the vehicle never entered, it becomes No show, the space is free again, and a student gets 1 warning. At 3 warnings the student can no longer reserve, and their other active reservations are cancelled. A reservation that was used, even for a few minutes, is never a no-show. Warnings do not expire. The super admin removes them with Clear penalties, which also lifts a suspension or ban. The history stays in the Penalties list.

**Overstay.** A visit stays open in the log until someone closes it, so a vehicle left behind keeps its space. 15 minutes after the reservation ends the check marks the visit Overstay. The dashboard counts it, the Gate page shows a badge, the student gets an Overstay penalty (one per reservation), is suspended for 3 days, and loses their other active reservations. The system cannot move a vehicle. Security checks the lot and presses Record exit when it is gone. That does not add a second penalty. While a vehicle is recorded inside, the same plate cannot enter again.

Visitors have no reservation, so they are not flagged automatically. A visitor who never leaves stays in the table until the exit is recorded.

**Manual override.** The Gate page has one manual plate entry, for when the system is down or there is an emergency. It needs a reason. The log marks the entry as Manual override with the reason. A registered plate keeps its owner and area, and an unregistered plate needs a vehicle type.

## Parking log download

Activity > Download saves every vehicle that parked between two dates as a `.csv` table: entry and exit time, minutes parked, plate, name, student / faculty / personnel / visitor, vehicle type, parking area, the reservation window, the visit reason, and whether it was a manual override or an overstay. It opens in Excel, Google Sheets and LibreOffice. Visits older than 30 days are deleted by the nightly cleanup, so download before then. Managers and the super admin can download (`GET /api/manager/logs/export?from=&to=`).

## Accounts and passwords

- The super admin creates managers under Users > Staff accounts. A temporary password is shown once. The manager must change it at the first login.
- Only the super admin resets passwords, for users and for managers. A reset also clears a login lockout.
- Deactivating an account blocks sign-in at once, even for a session that is already open, and cancels that person's active reservations. A super admin account cannot be changed from the app.
- If the super admin forgets the password, stop the app and start it once with `SEED_ADMIN_RESET=true` and `SEED_ADMIN_PASSWORD=<new password, 8+ characters>`. Sign in, change it when asked, then remove both settings. This only works for someone who can restart the app on the machine.

## Reservation approval (optional)

Approval is off by default, so reservations are approved immediately and the app has no Approvals page. The backend can still do it. With `app.reservation.auto-approve=false`, a new reservation shows as Waiting for approval and cannot be used at the gate. A manager approves or rejects it through the API (`/api/manager/reservations/pending`, `/{id}/approve`, `/{id}/reject`). Approving checks the space again at that moment. A request nobody reviewed before its window ended becomes Expired. `ReservationApprovalTest` covers this with approval turned on.

## Running it

### Requirements

Everyone on the team needs the same versions.

| Tool | Version |
|---|---|
| JDK | 21 (check the IDE project JDK and the Maven JDK) |
| Maven | 3.9+, or the Maven wrapper |
| Database | MySQL 8 or MariaDB 10.6+ for real use (the whole team uses the same one) |
| Python | 3.12, only for the camera scripts |

### Quick run with H2 (nothing to install)

H2 is the default profile and the data is lost when the app stops, so use it only for quick tests.

```
mvn spring-boot:run
```

On first start the console prints the super admin username and a random password, once. Copy it. Then open http://localhost:8080.

### Run with MySQL (profile `local`)

Data is kept between runs. `docs/DATABASE_SETUP.md` goes through installing MySQL on each laptop, step by step. In short:

1. Create the database and a user that is not root:

   ```sql
   CREATE DATABASE parking_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'parking_app'@'localhost' IDENTIFIED BY 'choose-a-password';
   GRANT ALL PRIVILEGES ON parking_db.* TO 'parking_app'@'localhost';
   ```

2. Copy `src/main/resources/application-example.properties` to `application-local.properties` in the same folder and put your password in it. This file is git-ignored. Never commit it.
3. Run with the profile: `mvn spring-boot:run -Dspring-boot.run.profiles=local`. In IntelliJ, put `local` in Active profiles of the run configuration.

Flyway builds the tables on startup. Nobody creates tables by hand. Each laptop has its own database, so data is not shared between teammates.

### Settings

Set these as environment variables (IntelliJ: Run > Edit Configurations > Environment variables) or in `application-local.properties`.

| Setting | Meaning |
|---|---|
| `JWT_SECRET` | Token signing secret, 32+ characters. If empty, a random one is used and everyone is logged out on every restart. |
| `SEED_ADMIN_USERNAME` | First super admin username (default `superadmin`). |
| `SEED_ADMIN_PASSWORD` | First super admin password. If empty, a random one is printed once in the console. |
| `SEED_ADMIN_RESET` | `true` with `SEED_ADMIN_PASSWORD` resets a forgotten super admin password. |
| `DEVICE_API_KEY` | Secret the gate hardware sends as `X-Device-Key`. Empty means the hardware endpoints are off. |
| `VEHICLE_AUTO_APPROVE` | `true` approves new vehicles automatically. |
| `DEMO_SEED_DATA` | `true` adds sample accounts and history at startup (see below). |
| `DEMO_TIME_CONTROL` | `true` adds the Demo time page for the super admin. |

The seeded admin must change the password at the first login.

## Demo helpers

Both are off by default. Never use them outside a demo.

- `DEMO_SEED_DATA=true` adds sample accounts, vehicles, reservations and history at startup. The console prints the shared demo password. Accounts: `demo.manager`, `demo.juan`, `demo.maria`, `demo.pedro`, `demo.ana`, `demo.rica`. It is skipped if they already exist.
- `DEMO_TIME_CONTROL=true` adds Demo time to the super admin menu. Move the system clock forward (15 minutes, 1 hour, 1 day) and press Run no-show and overstay check now. It resets when the app restarts. Logins are not affected.

Demo script for the panel (about 5 minutes):

1. Start with both settings on. Sign in as `demo.juan`, whose reservation is running now.
2. As `demo.manager`, open Gate > Manual override, type `MOTO101`, give any reason, and press Record override entry.
3. Open the Dashboard. One motorcycle is inside.
4. As the super admin, open Demo time, move forward 3 hours (within 2 hours of starting the app), then run the check. Juan's visit is now an overstay. Look at Users > Penalties, Users and the dashboard counter.
5. `demo.juan` tries to reserve and is refused, because he is suspended. The super admin clears it in Users.
6. For a no-show, `demo.pedro` has a reservation that starts 3 hours after startup and never arrives. Move the clock forward another 3 hours (6 in total) and run the check. He gets a warning.

## Try the whole flow (about 3 minutes)

1. Start the app and sign in as the super admin. Change the password when asked.
2. In a second browser or a private window, create an account, open My vehicle and register a plate, for example `ABC 1234`. It shows Waiting for approval.
3. As the parking manager, open Vehicles and approve it. (The super admin can approve too.)
4. As the user, open Reservations, keep the default time and reserve.
5. As the manager, open Gate, type `ABC1234` under Manual override with a reason, and record the entry. A green Allowed appears. Try a plate that is not registered to see a red Denied.
6. The Dashboard now shows 1 car in use, and the user's Log book shows the visit.
7. On the Gate page, press Record exit. The visit closes.

## API summary

| Who | Endpoints |
|---|---|
| Public | `POST /api/auth/register`, `POST /api/auth/login` |
| Any signed-in user | `GET /api/users/me`, `POST /api/auth/change-password`, `GET /api/capacity`, `GET /api/spaces`, `GET /api/reservations/availability`, `GET /api/time` |
| User | `GET/POST/PUT /api/vehicles/me`, `POST /api/reservations`, `GET /api/reservations/me`, `POST /api/reservations/{id}/cancel`, `GET /api/logs/me`, `GET /api/penalties/me` |
| Manager and super admin | under `/api/manager`: `dashboard`, `vehicles` (+ `/{id}/approve`, `/reject`, `/revoke`), `reservations` (+ `/pending`, `/{id}/approve`, `/reject`, `/cancel`), `logs`, `logs/export`, `denied`, `penalties`, `users?q=` |
| Parking manager only | `/api/manager/gate/*`: verify, visitor-entry, override, inside, exit |
| Super admin only | `/api/manager/users/{id}/reset-password`, `/deactivate`, `/activate`, `/clear-penalties`; `PUT /api/admin/capacity/{area}/{type}` (area is `GENERAL` or `FACULTY`); `/api/admin/staff` and its reset-password, deactivate, activate |
| Gate hardware (header `X-Device-Key`, only when `DEVICE_API_KEY` is set) | `POST /api/device/gate`, `POST /api/device/heartbeat` |
| Super admin, only when `DEMO_TIME_CONTROL=true` | `/api/admin/demo/time`, `/time/advance`, `/time/reset`, `/run-penalty-check` |

Staff accounts have no vehicle, so the user endpoints refuse them on purpose.

## The front end

It is plain HTML, CSS and JavaScript served by the Spring Boot app, so there is nothing extra to install and no second server. Start the backend and open http://localhost:8080.

```
src/main/resources/static/
  index.html         sign in and create account
  app.html           the signed-in app (menu and pages)
  css/style.css      all styles; colours are variables at the top (light theme, then dark)
  js/shared.js       light/dark theme and the Show / Hide password button
  js/api.js          the one place that calls the backend (token and error handling)
  js/ui.js           shared helpers (safe element builder, forms, tables, dates, rings)
  js/auth.js         sign in and create account
  js/pages-user.js   my vehicle, reservations and calendar, log book, warnings
  js/pages-staff.js  dashboard, gate, vehicle review, reservations, activity, capacity, penalties
  js/pages-admin.js  staff accounts, users, demo time
  js/app.js          sidebar, account menu, routing, role filtering, settings
```

To add a page, write `Pages.something = function (me, root) { ... }` in one of the pages files and add one line to the `PAGES` list in `js/app.js` (id, label, roles, function). The menu, routing and role filtering follow from that. A page can return a cleanup function, which is used to stop timers when you leave it.

Show server text with `textContent` or the `el()` helper, never `innerHTML`, so a script cannot be injected. Hiding a menu item is only for convenience. The real permission check is always on the server.

If you change a file in `static/` and the browser still shows the old one, hard refresh with Ctrl+F5.

## Tests

```
mvn test
python tools/test_plate_logic.py
```

- `AuthFlowTest`: registration, login, lockout, the role-injection attempt, 401 responses.
- `StaticPagesTest`: pages are public, `/api` is not.
- `ParkingFlowTest`: vehicle to approval to reservation, gate in and out, capacity limit, visitors, the dashboard, and the security rules (a user cannot use staff endpoints, cancel someone else's reservation or change capacity).
- `LogExportTest`: the CSV download works for staff only, checks the date range, and keeps commas, quotes and a leading `=` safe.
- `ReservationApprovalTest`: with approval turned on, a reservation waits, only a manager approves, deciding twice is refused, one reservation per day, a rejection shows its reason.
- `AdminAndPenaltyTest`: creating managers, forced password change, resetting and deactivating users, no-show warnings and the ban, overstay, manual override, faculty and general parking, visitors, and faculty and personnel never being penalised. It moves the demo clock forward instead of waiting.
- `DeviceApiTest`: the hardware endpoints need the device key, the key opens nothing else, the hardware follows the same rules as the manual gate, and the dashboard shows devices online.
- `ConcurrencyTest`: the busiest-moment capacity calculation.
- `tools/test_plate_logic.py`: picking a plate out of what the OCR read, and not sending the same plate twice.

## Rules for the team

- A schema change is a new migration file, `V9__something.sql` next (V1 to V8 exist). Never edit a migration that is already merged, because Flyway will refuse to start. Never let Hibernate create tables.
- Migrations hold structure and starting data only. No passwords or secrets.
- Never commit `application-local.properties`, `.env`, `.idea/` or `target/`. Do not type the device key into a file that gets committed.
- Use the injected `Clock` for the current time, never `LocalDateTime.now()`.
- Responses use DTOs, never entities, so password hashes cannot leak.
- Every new endpoint needs a decision on which roles may call it, and an ownership check on the server.

## When it works on one laptop but not another

1. Check the JDK in the IDE project settings and in Maven. Both must be 21.
2. Reload the Maven project and run a clean build (`mvn clean package`).
3. Try from a terminal with `mvn spring-boot:run`. If that works, it is an IDE setting.
4. Make sure `application-local.properties` exists and has your own password.
5. Everyone uses the same database product and version. MySQL and MariaDB differ.
6. Port 8080 busy? Add `server.port=8081` to your local properties.
7. When asking for help, send the first error line in the log, not the last.

## Project layout

```
src/main/java/com/mabini/parking/
  config/     security, clock, typed settings
  common/     enums, error handling
  auth/       register, login, JWT
  user/       accounts, profile, admin seeding
  vehicle/  capacity/  reservation/  log/  gate/  dashboard/
  penalty/    no-show and overstay rules, background check
  demo/       demo sample data and clock (off by default)
  device/     gate hardware endpoints and online status
src/main/resources/static/           the front end
src/main/resources/db/migration/     Flyway migrations (the schema)
docs/DATABASE_SETUP.md               MySQL on each laptop
docs/GATE_SETUP.md                   gate setup step by step (ESP32, cameras, one or two laptops)
docs/HARDWARE.md                     the device API for the hardware team
docs/PYTHON_SETUP.md                 Python, camera and OCR setup
esp32/gate_controller/               ESP32 sketch: asks the backend, moves the barrier
tools/plate_gate.py                  two-camera automatic plate reader
tools/plate_logic.py                 plate picking and de-duplication
tools/test_plate_logic.py            tests for the reader
tools/fake_gate.py                   pretend to be the gate hardware
camera_test.py                       webcam and OCR check
```
