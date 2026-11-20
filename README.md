# IoT-Based Parking Management System (Backend)

Mabini Colleges Inc. Java / Spring Boot backend. The gate hardware (ESP32) connects through a device
endpoint (see `docs/HARDWARE.md`). The manual Gate page works without any hardware, and is the fallback
when the hardware is down.

## What is built so far

**Step 1 (foundation):** project setup, profiles, Flyway migrations for all core tables, register /
login (JWT) / change password, role checks, login lockout, JSON errors, first super admin.

**Step 2 (vehicles, reservations, monitoring):**
- **Vehicles:** a user registers and edits ONE vehicle. A manager approves, rejects or revokes it
  (or set `VEHICLE_AUTO_APPROVE=true` to approve automatically).
- **Reservations:** availability by vehicle type, create, cancel, and a manager view per day.
- **Manual gate:** visitor walk-in, manual override (the one manual plate entry), "inside now" with a Record exit button. Works with or without hardware.
- **Dashboard:** spaces in use per vehicle type against capacity, today's totals, latest activity.
  Refreshes itself every 10 seconds.
- **Logs:** each user's own log book; staff see all entries, exits and refused entries.
- **Capacity:** the super admin can edit the capacity numbers.

**Step 3 (accounts, penalties, areas):**
- **Staff accounts:** the super admin creates parking managers from inside the app (they get a temporary
  password and must change it at first login). Managers can look users up; only the super admin can
  reset a user's password, deactivate or reactivate a user, and clear penalties.
- **Faculty parking:** faculty and personnel have their own parking area (12 car spaces to start).
- **Penalties:** no-show warnings and overstay suspensions, found by a background check every minute.
- **Visitors** must have a plate number and a reason for the visit.
- **Manual override** at the gate, always marked in the log with a reason.
- **Log cleanup:** visits older than 30 days are deleted every night at 3 AM.
- **Demo helpers (off by default):** sample data, and a clock the super admin can move forward.

**Step 4 (hardware):** the gate hardware (ESP32) can call the backend with a device key, using the same rules
as the manual gate. The dashboard shows which devices are online. The manual **Gate** page stays as the
fallback. See `docs/HARDWARE.md` (API, setup, an example ESP32 sketch) and `tools/fake_gate.py` (pretend to be
the hardware, no ESP32 needed).

**Step 5 (approval, limits and a new look):** a reservation now waits for a parking manager's approval
and a user can reserve **once per day** (stops spamming). Password fields have a **Show / Hide** button. The
whole front end was redesigned (warm neutral colours, serif headings, a sidebar with an account menu) and
**Settings** lets everyone pick **Light, Dark or System** appearance.

## Rules the system applies (all in `application.properties` unless noted)

| Rule | Value |
|---|---|
| Capacity, general lot (editable by super admin) | motorcycle 60, car 33, tricycle/e-trike 8 |
| Capacity, faculty parking (editable by super admin) | car 12 (motorcycle and tricycle 0, see "Faculty parking" below) |
| Longest reservation | 10 hours (`app.reservation.max-hours`) |
| How far ahead | 7 days (`max-days-ahead`) |
| Early entry before the start | 15 minutes (`grace-minutes`) |
| Cancel until | 60 minutes before the start (`cancel-cutoff-minutes`). A request still **waiting for approval** can be withdrawn at any time |
| Reservations per day | **1 per user per day**, by the day the reservation starts (`app.reservation.max-per-day`). Cancelled, rejected and expired requests don't count, so a user can try again |
| New reservations | start as **Waiting for approval** and hold no space. A manager approves or rejects (`app.reservation.auto-approve=true` skips approval) |
| New vehicles | need manager approval (`VEHICLE_AUTO_APPROVE`) |
| Reservation is allowed when | the BUSIEST moment in that window is below capacity (bookings in different hours don't block each other) |
| Changing plate or type | needs approval again, and is blocked while a reservation is active |
| Visitors | no account, no reservation; name, vehicle type, **plate number** and **reason for the visit** are all required; they use the general lot while their vehicle type has room |
| No-show | the reservation ends and the vehicle never entered: 1 warning |
| Too many no-shows | at 3 warnings (`app.penalty.no-show-ban-threshold`) the user can no longer reserve |
| Overstay | still inside 15 minutes after the reservation ends: cannot reserve or enter for 3 days (`app.penalty.overstay-suspension-days`) |
| Logs kept | 30 days (`app.logs.retention-days`) |
| Entry at the gate needs | registered, approved, owner active and not suspended, not already inside, a reservation valid right now |

## Faculty parking

Faculty and personnel have their own area in the real lot (about 12 spaces, an estimate). It is
counted separately from the general lot, so students never take faculty spaces and the other way round.

- Students always use the **general lot**. Visitors too.
- Faculty and personnel use the **faculty parking** for any vehicle type that has spaces there.
  By default only **cars** have faculty spaces (12). A faculty member with a motorcycle or tricycle uses the
  general lot, because the faculty number for those types is 0.
- The super admin changes any number in **Vehicles > Capacity** (one number per area and vehicle type). If the faculty
  parking also has motorcycle spaces, set the faculty motorcycle number above 0 and faculty motorcycles move there.
  Setting a faculty number to 0 sends that vehicle type back to the general lot.
- The area is decided when the reservation is made and stays with it, so changing a number later never
  moves an existing reservation. The dashboard shows a separate bar for faculty parking.

## Reservation approval

1. The user sends a request. It shows as **Waiting for approval** and cannot be used at the gate yet.
2. A manager opens **Approvals** (a number next to it shows how many are waiting, and the dashboard shows the
   count too) and presses **Approve** or **Reject**. A rejection can carry a reason, which the user sees.
3. **Approve** checks the space again at that moment. If the lot got full in the meantime, it says so and the
   manager can reject it or cancel another reservation first. It is also refused if the user has since been
   banned, suspended or deactivated, or the vehicle is no longer approved.
4. Only **approved** reservations count toward capacity, and only they let a vehicle in at the gate.
5. A request nobody reviewed before its time window **ended** becomes **Expired** automatically (the same
   background check that finds no-shows).
6. The one-per-day limit is checked at the same time as the capacity check, so two quick clicks can't both pass.
7. A ban, suspension, deactivation or revoked vehicle also cancels the user's waiting requests.

To go back to instant reservations while testing: `app.reservation.auto-approve=true` (and e.g.
`app.reservation.max-per-day=5`) in `application-local.properties`.

## What happens when...

### ...someone reserves but never shows up (no-show)

1. The reservation stays **Active**. The driver can still come in at any time from 15 minutes before the
   start until the end of the reservation.
2. The background check runs every minute. Once the reservation has **ended** and the vehicle never entered,
   the reservation becomes **No show** (the user sees it in Reservations). Its space is free for others again.
3. The user gets **1 warning**. They see it under **Warnings** in their menu and on **My profile**. Staff see
   it under **Users > Penalties** and on **Users**.
4. At **3 warnings** (`app.penalty.no-show-ban-threshold`) the user can **no longer reserve** parking
   ("Reservations are now disabled"). Their other active reservations are cancelled at that moment so the spaces
   go back to everyone. They can still sign in and see their history.
5. Warnings **do not expire by themselves.** The **super admin** removes them from **Users > Clear penalties** (super admin only)
   (warnings, suspension and the ban all go back to zero). The history stays in the Penalties list.
6. To avoid a warning, the user cancels **at least 60 minutes before the start** (`cancel-cutoff-minutes`).
   Later than that, the reservation can only be cancelled by a manager.
7. A reservation that was **used** (the vehicle entered, even for a few minutes) is never a no-show.

### ...someone parks and does not leave (vehicle left behind, overstay)

1. The visit stays open in the log, so the vehicle still counts as **inside** and keeps its space taken on
   the dashboard. Nothing closes the visit by itself.
2. **15 minutes after the reservation ends** (`grace-minutes`) the next background check marks the visit
   **Overstay**. From then on:
   - the dashboard shows **Vehicles inside past their reservation**, and **Gate > Inside now** shows an
     **Overstay** badge on that vehicle;
   - the owner gets an **Overstay** penalty (one per reservation, never doubled);
   - the owner is **suspended for 3 days** (`app.penalty.overstay-suspension-days`): they cannot reserve, and
     the gate refuses their plate ("suspended until ...");
   - the owner's **other active reservations are cancelled**.
3. **Someone must deal with the vehicle.** The system cannot tow or remove it. Security checks the lot and,
   when the vehicle is gone (or has been moved), presses **Record exit** in **Gate > Inside now** (or
   **Vehicle leaves** with the plate). The recorded exit time is the time it was pressed. This does not add a
   second penalty.
4. If the driver comes back and drives out normally, security presses **Vehicle leaves**. If the exit is
   recorded late and the background check had not run yet, the overstay penalty is added at that moment.
5. While a vehicle is still recorded inside, the same plate **cannot enter again** ("already recorded
   inside"). Record the old exit first.
6. The super admin can lift a suspension early from **Users > Clear penalties**.
7. **Visitors** have no reservation, so they have no end time and are **not flagged automatically**. A
   visitor who never leaves stays in **Inside now** (and counts against the general lot) until security
   records the exit. Their reason for the visit is shown there to help security decide. (Improvement idea if
   the team wants it: a maximum visitor stay, for example 10 hours, that flags them too.)
8. Open visits are never deleted by the 30-day cleanup, only finished ones.

### ...the system is down or there is an emergency (manual override)

**Gate > Manual override** (open by default) lets a vehicle in without the normal checks. A reason is required. The log marks
the entry as **Manual override** with the reason and who recorded it. A registered plate keeps its owner and
area. An unregistered plate needs a vehicle type. The same plate cannot be let in twice.

## Staff accounts and passwords

- People can register only as **users** (student, faculty, personnel). The register screen cannot make
  staff, and the server ignores any role sent with it.
- The **super admin** creates parking managers: **Staff accounts > Create manager account**. A temporary
  password is shown **once**. Give it to the person. They must change it at first login.
- Forgot password: only the **super admin** resets passwords: a **user's** (**Users > Accounts > Reset password**) or a
  **manager's** (**Staff accounts**). Managers can look users up but cannot change them. Both give a new temporary password and clear a login lockout.
- If the **super admin** forgets the password: stop the app and start it once with `SEED_ADMIN_RESET=true`
  and `SEED_ADMIN_PASSWORD=<a new password, 8+ characters>`. Sign in with it, change it when asked, then remove
  both settings. This works only for someone who can restart the app on the machine, not from the website.
- **Deactivating** an account blocks sign-in at once (even a session that is already open) and cancels that
  person's active reservations. A super admin account cannot be changed from the app.

## Demo helpers (never use outside a demo)

Both are **off** by default.

| Setting | What it does |
|---|---|
| `DEMO_SEED_DATA=true` | At startup adds sample accounts, vehicles, reservations and history. The console prints the shared demo password. Accounts: `demo.manager`, `demo.juan`, `demo.maria`, `demo.pedro`, `demo.ana`, `demo.rica`. Skipped if they already exist. |
| `DEMO_TIME_CONTROL=true` | Adds **Demo time** to the super admin's menu. Move the system clock forward (15 min, 1 hour, 1 day...) and press **Run no-show and overstay check now**. It resets when the app restarts. Logins are not affected. |

Set them as environment variables, or in `application-local.properties`
(`app.demo.seed-data=true`, `app.demo.time-control=true`). In IntelliJ: Run > Edit Configurations >
Environment variables.

**Demo script for the panel (about 5 minutes)**

(Reservations you make during the demo wait for approval: approve them in **Approvals**.)

1. Start with `DEMO_SEED_DATA=true` and `DEMO_TIME_CONTROL=true`. Sign in as `demo.juan` (his reservation is
   running now).
2. As `demo.manager` open **Gate > Manual override**, type `MOTO101`, any reason (for example "demo"), **Record override entry**.
3. Open **Dashboard**: one motorcycle is inside.
4. As the super admin, **Demo time**: move forward 3 hours (do all of this within 2 hours of starting the app), then **Run no-show and overstay check now**.
   Juan's visit is now an overstay: look at **Users > Penalties**, **Users** and the dashboard counter.
5. `demo.juan` tries to reserve: refused, he is suspended. The super admin clears it in **Users**.
6. For a no-show: `demo.pedro` has a reservation that starts 3 hours after startup and never arrives. Move the
   clock forward another 3 hours (6 in total) and run the check: he gets a warning.

## Try the whole flow (about 3 minutes)

1. Start the app and sign in as the super admin (the console prints the password the first time).
   Change the password when asked. The super admin can use every staff page.
2. Open a second browser (or a private window), **Create account**, then **My vehicle** and register
   a plate, for example `ABC 1234`. It shows "Waiting for approval".
3. Back as admin: **Vehicles** shows it waiting. Click **Approve**.
4. As the user: **Reservations**, keep the default time (it starts soon), click **Reserve parking**.
5. As admin: **Gate > Manual override**, type `ABC1234`, give a reason, **Record override entry**. A green **Allowed** appears.
   Try a plate that is not registered to see a red **Denied**.
6. **Dashboard** now shows 1 car in use. The user's **Log book** shows the visit.
7. **Gate**, **Vehicle leaves**, and the visit closes.

## API summary

| Who | Endpoints |
|---|---|
| Public | `POST /api/auth/register`, `POST /api/auth/login` |
| Any logged-in user | `GET /api/users/me`, `POST /api/auth/change-password`, `GET /api/capacity`, `GET /api/reservations/availability` |
| User | `GET/POST/PUT /api/vehicles/me`, `POST /api/reservations`, `GET /api/reservations/me`, `POST /api/reservations/{id}/cancel`, `GET /api/logs/me` |
| Any logged-in user | also `GET /api/time` (server time) |
| User | also `GET /api/penalties/me` |
| Manager / super admin | `GET /api/manager/dashboard`, `/vehicles` (+ `/{id}/approve`, `/reject`, `/revoke`), `/reservations` (+ `/pending`, `/{id}/approve`, `/{id}/reject`, `/{id}/cancel`), `/logs`, `/denied`, `/penalties`, `/gate/verify`, `/gate/visitor-entry`, `/gate/override`, `/gate/inside`, `/gate/exit/{id}`, `GET /users?q=` (all under `/api/manager`). **Super admin only:** `/users/{id}/reset-password`, `/deactivate`, `/activate`, `/clear-penalties` |
| Super admin | `PUT /api/admin/capacity/{area}/{type}` (area: `GENERAL` or `FACULTY`), `POST/GET /api/admin/staff`, `/api/admin/staff/{id}/reset-password`, `/deactivate`, `/activate` |
| Gate hardware (header `X-Device-Key`, only when `DEVICE_API_KEY` is set) | `POST /api/device/gate`, `POST /api/device/heartbeat` |
| Super admin (only when `DEMO_TIME_CONTROL=true`) | `GET /api/admin/demo/time`, `POST /api/admin/demo/time/advance`, `/time/reset`, `/run-penalty-check` |

Staff accounts have no vehicle, so the user-only endpoints refuse them on purpose.

## The front end

Plain HTML, CSS and JavaScript served by the Spring Boot app itself, so there is nothing extra to
install and no second server. Start the backend, then open **http://localhost:8080** in a browser.

```
src/main/resources/static/
  index.html      sign in / create account
  app.html        the signed-in app (menu + pages)
  css/style.css   all styles (colours are variables at the top: light theme, then the dark theme)
  js/shared.js       light/dark theme (applied before the page paints) and the Show/Hide password button
  js/api.js          the ONE place that calls the backend (token + error handling)
  js/ui.js           shared helpers (safe element builder, forms, tables, dates)
  js/auth.js         sign in / create account logic
  js/pages-user.js   my vehicle, reservations, log book
  js/pages-staff.js  dashboard, gate, vehicle review, approvals, reservations, activity, capacity, penalties
  js/pages-admin.js  staff accounts, users, demo time
  js/app.js          sidebar, account menu, routing, role filtering, Settings (appearance, profile, password)
```

**Adding a page for a new feature:** write `Pages.something = function (me, root) { ... }` in a pages
file and add one line to the `PAGES` list in `js/app.js` (id, label, roles allowed, function). The menu,
routing and role filtering update by themselves. A page may return a cleanup function (used to stop
timers when you leave the page).

Rules: show server text with `textContent` / the `el()` helper, never `innerHTML` (prevents script
injection). Hiding a menu item is only for convenience: the real permission check is always on the
server.

**Light and dark mode:** open the account menu (bottom of the sidebar) > **Settings** > **Appearance**.
The choice is remembered in that browser only. The colours for both themes are the variables at the top of
`css/style.css`.

If you change a file in `static/` and the browser still shows the old version, hard refresh
(Ctrl+F5), or rebuild if you run from a packaged jar.

## Requirements (everyone must match)

| Tool | Version |
|---|---|
| JDK | **21** (check BOTH your IDE project JDK and your Maven JDK) |
| Maven | 3.9+ (or use the wrapper, see below) |
| Database | MySQL 8 **or** MariaDB 10.6+ (the whole team uses the same product) |

The build fails with a clear message if the JDK is older than 21.

### Maven wrapper (do this once, one person, with internet)

```
mvn -N wrapper:wrapper
```

Commit the generated `mvnw`, `mvnw.cmd` and `.mvn/` folder. After that everyone runs `./mvnw`
(Windows: `mvnw.cmd`) and needs no Maven install.

## Run it the quick way (no database install): profile `h2`

`h2` is the default profile. Data is lost when the app stops, so use it for quick testing only.

```
mvn spring-boot:run
```

Watch the console on first start: it prints the super admin username and a random password
**once**. Copy it.

## Run it on MySQL/MariaDB: profile `local`

1. Create the database and a NON-root user (run in MySQL as an admin):

   ```sql
   CREATE DATABASE parking_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'parking_app'@'localhost' IDENTIFIED BY 'choose-a-password';
   GRANT ALL PRIVILEGES ON parking_db.* TO 'parking_app'@'localhost';
   ```

2. Copy `src/main/resources/application-example.properties` to
   `src/main/resources/application-local.properties` and put your password in it.
   This file is git-ignored. Never commit it.

3. Run with the profile:

   ```
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

   In an IDE: set the active profile to `local` in the run configuration.

On startup Flyway builds the tables automatically. Nobody creates tables by hand.

## Settings (environment variables or application-local.properties)

| Setting | Meaning |
|---|---|
| `JWT_SECRET` | Token signing secret, 32+ characters. If empty, a random one is used (logins reset on every restart). |
| `SEED_ADMIN_USERNAME` | First super admin username (default `superadmin`). |
| `SEED_ADMIN_PASSWORD` | First super admin password. If empty, a random one is printed once in the console. |
| `DEVICE_API_KEY` | Secret the gate hardware sends as `X-Device-Key`. Empty = hardware endpoints OFF. See `docs/HARDWARE.md`. |
| `SEED_ADMIN_RESET` | `true` together with `SEED_ADMIN_PASSWORD` resets a forgotten super admin password (see Staff accounts). |

The seeded admin must change the password at first login.

## Try it (curl)

```
# Register (anyone). "role" in the body is ignored on purpose.
curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"username":"juan.dela","fullName":"Juan Dela Cruz","password":"my-password-1","userType":"STUDENT"}'

# Login -> returns a token
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"juan.dela","password":"my-password-1"}'

# Use the token
curl http://localhost:8080/api/users/me -H "Authorization: Bearer PASTE_TOKEN_HERE"
```

(Postman works the same way. Save the requests in a shared collection.)

## Tests

```
mvn test
```

- `AuthFlowTest`: registration, login, lockout, the role-injection attempt, 401 responses.
- `StaticPagesTest`: pages are public, `/api` is not.
- `ParkingFlowTest`: vehicle registration to approval, reservation, gate in and out, capacity limit,
  visitors, dashboard, and the security rules (a user can't use staff endpoints, can't cancel someone
  else's reservation, can't change capacity).
- `ReservationApprovalTest`: a new reservation waits, only a manager can approve, deciding twice is refused,
  one reservation per day, a rejection shows its reason and lets the user try again.
- `AdminAndPenaltyTest`: creating managers and forced password change, resetting and deactivating users,
  no-show warnings and the ban, overstay (background check and late exit, never punished twice), manual
  override, faculty vs general parking, visitors needing a plate and a reason. It moves the demo clock
  forward instead of waiting.
- `DeviceApiTest`: the hardware endpoints need the device key, the key opens nothing else, the hardware follows
  the same rules as the manual gate, and the dashboard shows devices online.
- `ConcurrencyTest`: the "busiest moment" capacity calculation (no Spring needed).

## Rules for the team

- **Schema changes = a NEW migration file** `V8__something.sql` (V1 to V7 exist). Never edit a migration that has
  already been merged (Flyway will refuse to start). Never use Hibernate to create tables.
- Migrations contain structure and starting data only. **No passwords or secrets.**
- Never commit `application-local.properties`, `.env`, `.idea/` or `target/`.
- Use the injected `Clock` for the current time, never `LocalDateTime.now()` directly.
- Responses use DTOs, never entities (so password hashes can't leak).
- Every new endpoint: decide which roles may call it, and check ownership on server side.

## Troubleshooting "works on my machine"

1. Check the JDK in the IDE project settings AND the Maven/Gradle settings (must be 21).
2. Reload/refresh the Maven project, then run a clean build (`mvn clean package`).
3. Try from the terminal: `mvn spring-boot:run`. If that works, it is an IDE setting.
4. Make sure `application-local.properties` exists and has your own password.
5. Same database product and version across the team (MySQL vs MariaDB differ).
6. Port 8080 busy? Add `server.port=8081` to your local properties.
7. Send the FIRST error line in the log, not the last.

## Project layout

```
src/main/java/com/mabini/parking/
  config/    security, clock, typed settings
  common/    enums, error handling
  auth/      register, login, JWT
  user/      accounts, profile, admin seeding
  vehicle/ capacity/ reservation/ log/ gate/ dashboard/
  penalty/   no-show and overstay rules, background check
  demo/      demo sample data, demo clock (off by default)
  device/    gate hardware endpoints and online status
docs/HARDWARE.md   guide for the hardware team
tools/fake_gate.py pretend to be the gate hardware
src/main/resources/db/migration/   Flyway migrations (the schema)
```
