@echo off
REM Starts the server, waits for it to start, then starts the gate.
REM Start the phone's IP Webcam server first if the phone is the exit camera.
cd /d "%~dp0"

start "Parking Server" cmd /k call "%~dp0run-server.bat"

echo Waiting for the server to start...
timeout /t 30 /nobreak >nul

start "Gate Camera" cmd /k call "%~dp0run-gate.bat"
exit