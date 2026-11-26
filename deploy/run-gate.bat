@echo off
REM ==============================================================
REM  Starts the plate reader for the gate laptop (no IntelliJ needed).
REM  Run it AFTER the backend (run-server.bat) is running.
REM  Edit the CHANGE_ME key and the phone address if used.
REM  Needs: Python 3 with the packages from docs/PYTHON_SETUP.md.
REM ==============================================================

set DEVICE_API_KEY=GateKey2026Mabini
set PARKING_BACKEND=http://localhost:8080

cd /d "%~dp0"
python "%~dp0..\tools\plate_gate.py" --in-cam 0 --out-cam 1
pause
