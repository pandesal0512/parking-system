# Database setup for every group member's laptop

**Read this first:** MySQL Workbench is only a *viewer* (a window into a database). The database itself is
**MySQL Server**. Every laptop needs both. The simplest way is the MySQL Installer, which installs both.

## How the team works

Everyone has their **own separate database on their own laptop**. Nothing is shared over the network.

- The code is shared through Git (GitHub). The database is not.
- Every laptop builds the same tables automatically (Flyway), so everyone's database has the same structure.
- Each person's data is their own, so testing on one laptop never breaks another.
- Passwords stay on each laptop (`application-local.properties` is git-ignored and never uploaded).
- For the presentation, one laptop is chosen and loaded with the demo data (see the end).

## Steps for each person (about 20 minutes)

### 1. Install MySQL Server and Workbench

**Windows:** download the *MySQL Installer* from dev.mysql.com/downloads/installer. Choose **Custom** and add
**MySQL Server 8.4 (LTS)** (add Workbench too if you don't have it yet). The newest Workbench (26.x) works best with
server 8.4 or newer, so don't pick an old 8.0 server. Keep the defaults: port **3306**, strong password
encryption. When it asks for the **root password**, choose one and **write it down**. Let it start MySQL as a
Windows service.

**Mac:** install the MySQL Server `.dmg` and the Workbench `.dmg`. Note the root password the installer shows or asks
for.

### 2. Create the database and the app's own user

1. Open Workbench. If it says `<no connections>`, click the **+** next to **Database Connections** and fill in:
   Name `Local`, Host `127.0.0.1`, Port `3306`, Username `root`. Press **Test Connection**, enter the root
   password, then **OK**. Double-click the new `Local` connection to open it.
2. Open a new SQL tab (**File > New Query Tab**), paste this, change the password, and press the lightning bolt:

   ```sql
   CREATE DATABASE parking_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'parking_app'@'localhost' IDENTIFIED BY 'choose-a-password';
   GRANT ALL PRIVILEGES ON parking_db.* TO 'parking_app'@'localhost';
   FLUSH PRIVILEGES;
   ```

   The app uses `parking_app`, never `root`. Do not put the root password in the project.

3. Check it worked: in the left panel click the refresh icon next to **Schemas**. You should see `parking_db`.

### 3. Get the project and open it in IntelliJ

1. Install **JDK 21** (IntelliJ can download it: **File > Project Structure > SDK > Add SDK > Download JDK**,
   version 21).
2. Clone the team's repository (**File > New > Project from Version Control**) or unzip the project, then open
   the folder as a Maven project. Wait for Maven to download everything.
3. In `src/main/resources`, copy `application-example.properties` to a new file named
   **`application-local.properties`** (same folder). Open it and put your `parking_app` password in
   `spring.datasource.password`. This file stays on your laptop only.

### 4. Run it

1. **Run > Edit Configurations**, select the `ParkingApplication` configuration (create one with **+ > Spring
   Boot** if there is none).
2. Set **Active profiles** to `local` (if you don't see the field, click **Modify options > Add VM options**
   and use `-Dspring.profiles.active=local`).
3. Set **Environment variables** (click the small box at the end of the field), separated by `;`:

   ```
   SEED_ADMIN_PASSWORD=choose-a-strong-password;JWT_SECRET=any-long-random-text-of-32-or-more-characters;DEMO_SEED_DATA=true
   ```

   `DEMO_SEED_DATA=true` adds sample accounts so you have something to click. Add `DEMO_TIME_CONTROL=true` too if
   you want the Demo time page.
4. Press Run. The first start creates all the tables. Open **http://localhost:8080** and sign in as
   `superadmin` with the password you chose.

### 5. Look at your data in Workbench

Right-click `parking_db` > refresh, then open **Tables**. Click the small table icon next to `users` or
`reservations` to see the rows. Look, but don't edit rows by hand while the app is running.

## If something goes wrong

| Message | Fix |
|---|---|
| `Access denied for user 'parking_app'` | The password in `application-local.properties` does not match step 2, or the user wasn't created. Run step 2 again. |
| `Communications link failure` / `Connection refused` | MySQL isn't running. Windows: open **Services**, find the MySQL service (named like `MySQL84`), press Start. |
| `Unknown database 'parking_db'` | Step 2's first line was not run. |
| `Unsupported class file major version` or the build fails | IntelliJ is not using JDK 21. Check **File > Project Structure**. |
| Port 8080 already in use | Another copy of the app is still running. Stop it (the red square in IntelliJ). |
| `Flyway ... checksum mismatch` | Someone edited an old migration file. Never edit `V1` to `V8`. Ask the team. If it's only your own test data, reset (below). |

## Starting over (your own laptop only)

In Workbench run:

```sql
DROP DATABASE parking_db;
CREATE DATABASE parking_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Then run the app again: the tables are rebuilt and the demo data is loaded.

## Rules for working together

- **Never edit** `V1` to `V8` in `db/migration`. To change a table, add a new file (`V9__...sql`) and commit it with
  the code that needs it. Everyone's database then updates itself when they pull and run.
- Never commit `application-local.properties` or any password.
- Pull before you start working (`git pull`) so you have the latest migrations.

## The presentation laptop

1. Pick **one** laptop and set it up with the steps above.
2. Start the app once with `DEMO_SEED_DATA=true` (and `DEMO_TIME_CONTROL=true` if you will show penalties). The
   console prints the demo password.
3. **Back up the clean demo state** so you can return to it: Workbench > **Server > Data Export**, choose
   `parking_db`, **Export to Self-Contained File**, save e.g. `demo-clean.sql`.
4. To reset before the presentation: **Server > Data Import**, choose **Import from Self-Contained File**, pick
   `demo-clean.sql`, **Target schema: parking_db**, Start Import. (Stop the app first.)
5. The gate hardware connects to this laptop's address on the same Wi-Fi. See `docs/HARDWARE.md`.

Everything runs on this one laptop. No internet is needed during the presentation, so keep the project, the
MySQL service and the backup file on the laptop in advance.
