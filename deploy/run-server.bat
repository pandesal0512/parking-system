@echo off
REM NOTE: values with & (like the database URL) must stay inside quotes.
REM ==============================================================
REM  Starts the parking system backend (no IntelliJ needed).
REM  1. Fill in the CHANGE_ME values once (keep this file private).
REM  2. Double-click this file. Keep the window open while in use.
REM  Needs: Java 21 (Java runtime) and MySQL with the parking_db database.
REM ==============================================================

set SPRING_PROFILES_ACTIVE=local
set "SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/parking_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Manila"
set SPRING_DATASOURCE_USERNAME=parking_app
set SPRING_DATASOURCE_PASSWORD=admin12345
REM At least 32 random characters. Keeps logins valid across restarts.
set JWT_SECRET=ParkingSys2026KeyXq7mRt9LwVb4ZnHc8

REM Password for the first super admin account (used only the first time).
set SEED_ADMIN_PASSWORD=admin12345
set DEMO_TIME_CONTROL=true

REM Must be the same value you give to the gate script (run-gate.bat).
set DEVICE_API_KEY=GateKey2026Mabini

if "%JWT_SECRET%"=="CHANGE_ME_TO_A_LONG_RANDOM_STRING_OF_32_OR_MORE_CHARACTERS" (
  echo Please edit run-server.bat and set your own values first.
  pause
  exit /b 1
)

cd /d "%~dp0"
java -jar parking-system-0.1.0.jar
pause
