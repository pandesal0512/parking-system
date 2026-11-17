# IoT-Based Parking Management System (Backend)

Mabini Colleges Inc. Java / Spring Boot backend. No hardware is connected: the gate is simulated
through the API.

## What is built so far

**Step 1 (foundation):** project setup, profiles, Flyway migrations for all core tables, register /
login (JWT) / change password, role checks, login lockout, JSON errors, first super admin.

**Step 2 (vehicles, reservations, monitoring):**
- **Vehicles:** a user registers and edits ONE vehicle. A manager approves, rejects or revokes it
  (or set `VEHICLE_AUTO_APPROVE=true` to approve automatically).
- **Reservations:** availability by vehicle type, create, cancel, and a manager view per day.
- **Gate simulator:** stands in for the camera. Plate in / out, visitor walk-in, "inside now".
- **Dashboard:** spaces in use per vehicle type against capacity, today's totals, latest activity.
  Refreshes itself every 10 seconds.
- **Logs:** each user's own log book; staff see all entries, exits and refused entries.
- **Capacity:** the super admin can edit the capacity numbers.

Not built yet: no-show warnings and suspensions, overstay penalties, manual override, staff account
creation by the super admin, the 30-day log cleanup job.

## Rules the system applies (all in `application.properties` unless noted)

| Rule | Value |
|---|---|
| Capacity (survey numbers, editable by super admin) | motorcycle 60, car 33, tricycle/e-trike 8 |
| Longest reservation | 10 hours (`app.reservation.max-hours`) |
| How far ahead | 7 days (`max-days-ahead`) |
| Early entry before the start | 15 minutes (`grace-minutes`) |
| Cancel until | 60 minutes before the start (`cancel-cutoff-minutes`) |
| New vehicles | need manager approval (`VEHICLE_AUTO_APPROVE`) |
| Reservation is allowed when | the BUSIEST moment in that window is below capacity (bookings in different hours don't block each other) |
| Changing plate or type | needs approval again, and is blocked while a reservation is active |
| Visitors | no account, no reservation; allowed while their vehicle type has room |
| Entry at the gate needs | registered, approved, owner active and not suspended, not already inside, a reservation valid right now |

## Try the whole flow (about 3 minutes)

1. Start the app and sign in as the super admin (the console prints the password the first time).
   Change the password when asked. The super admin can use every staff page.
2. Open a second browser (or a private window), **Create account**, then **My vehicle** and register
   a plate, for example `ABC 1234`. It shows "Waiting for approval".
3. Back as admin: **Vehicles** shows it waiting. Click **Approve**.
4. As the user: **Reservations**, keep the default time (it starts soon), click **Reserve parking**.
5. As admin: **Gate**, type `ABC1234`, click **Vehicle enters**. A green **Allowed** appears.
   Try a plate that is not registered to see a red **Denied**.
6. **Dashboard** now shows 1 car in use. The user's **Log book** shows the visit.
7. **Gate**, **Vehicle leaves**, and the visit closes.

## API summary

| Who | Endpoints |
|---|---|
| Public | `POST /api/auth/register`, `POST /api/auth/login` |
| Any logged-in user | `GET /api/users/me`, `POST /api/auth/change-password`, `GET /api/capacity`, `GET /api/reservations/availability` |
| User | `GET/POST/PUT /api/vehicles/me`, `POST /api/reservations`, `GET /api/reservations/me`, `POST /api/reservations/{id}/cancel`, `GET /api/logs/me` |
| Manager / super admin | `GET /api/manager/dashboard`, `/vehicles` (+ `/{id}/approve`, `/reject`, `/revoke`), `/reservations` (+ `/{id}/cancel`), `/logs`, `/denied`, `/gate/verify`, `/gate/visitor-entry`, `/gate/inside`, `/gate/exit/{id}` |
| Super admin | `PUT /api/admin/capacity/{type}` |

Staff accounts have no vehicle, so the user-only endpoints refuse them on purpose.

## The front end

Plain HTML, CSS and JavaScript served by the Spring Boot app itself, so there is nothing extra to
install and no second server. Start the backend, then open **http://localhost:8080** in a browser.

```
src/main/resources/static/
  index.html      sign in / create account
  app.html        the signed-in app (menu + pages)
  css/style.css   all styles (colours are variables at the top)
  js/api.js          the ONE place that calls the backend (token + error handling)
  js/ui.js           shared helpers (safe element builder, forms, tables, dates)
  js/auth.js         sign in / create account logic
  js/pages-user.js   my vehicle, reservations, log book
  js/pages-staff.js  dashboard, gate, vehicle review, reservations, activity, capacity
  js/app.js          menu, routing, role filtering, profile, change password
```

**Adding a page for a new feature:** write `Pages.something = function (me, root) { ... }` in a pages
file and add one line to the `PAGES` list in `js/app.js` (id, label, roles allowed, function). The menu,
routing and role filtering update by themselves. A page may return a cleanup function (used to stop
timers when you leave the page).

Rules: show server text with `textContent` / the `el()` helper, never `innerHTML` (prevents script
injection). Hiding a menu item is only for convenience: the real permission check is always on the
server.

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
- `ConcurrencyTest`: the "busiest moment" capacity calculation (no Spring needed).

## Rules for the team

- **Schema changes = a NEW migration file** `V6__something.sql`. Never edit a migration that has
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
  (next)     penalty/  (no-show and overstay rules)
src/main/resources/db/migration/   Flyway migrations (the schema)
```
