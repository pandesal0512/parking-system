# Python setup for the plate reader (Windows)

This guide sets up Python, the camera and OCR libraries, and IntelliJ so that `camera_test.py` and `tools/plate_gate.py` run. It covers the problems the team hit, so follow it in order.

The plate reader needs **Python 3.12**. Do not use the newest Python (3.13 or 3.15) for this project. Some of the libraries it depends on have no ready-made package for the newest versions, so pip tries to build them from source, which needs a C++ compiler and fails on a normal laptop.

## 1. Install Python 3.12

Open PowerShell and run:

```
winget install Python.Python.3.12
```

If `winget` is not recognised, download **Python 3.12** from python.org instead and tick **Add python.exe to PATH** on the first screen of the installer.

Close and reopen PowerShell, then check the version:

```
py -3.12 --version
```

It should print `Python 3.12.x`. If it says the version is not found, install Python 3.12 again.

## 2. Create a separate environment for the camera

A virtual environment keeps the camera libraries separate from everything else on the laptop, and it stays outside the project folder so nothing is added to the code.

```
py -3.12 -m venv C:\Users\Asus\pyenv\camera
```

Check that it is the right Python:

```
C:\Users\Asus\pyenv\camera\Scripts\python.exe --version
```

It should print `Python 3.12.x`.

## 3. Install the libraries

Run this on **one line**:

```
C:\Users\Asus\pyenv\camera\Scripts\python.exe -m pip install opencv-python easyocr
```

This takes several minutes, because PyTorch is large (about 130 MB). Wait for the prompt to come back. The end of the output should say `Successfully installed ... easyocr ...`. The red text earlier in the output is normal.

Do **not** run the `activate` script, and do not paste pip's output back into PowerShell. Pasting the output makes PowerShell try to run each line as a command, which causes a stream of errors.

## 4. Check that everything loads

Run this on one line:

```
C:\Users\Asus\pyenv\camera\Scripts\python.exe -c "import cv2, easyocr; print('ready')"
```

The first run can take 30 to 90 seconds, because it loads PyTorch. When it prints `ready`, the libraries are installed correctly.

If the prompt looks stuck with no output, wait. If it's still blank after three minutes, press **Ctrl+C** once and run the command again.

## 5. Point IntelliJ at the environment

IntelliJ must use the same Python as the one you just set up, or the script will fail with `No module named cv2`.

1. Open the project in IntelliJ.
2. Go to **File > Settings > Project: parking-system > Python Interpreter**. If you can't see **Project: parking-system**, clear the search box first and scroll the left panel.
3. Click the gear icon and choose **Add Interpreter > Add Local Interpreter**.
4. Select **Select existing**.
5. Set **Type** to **Python**, and in **Python path** enter:
   `C:\Users\Asus\pyenv\camera\Scripts\python.exe`
6. Click **OK**.

Do not choose **Generate new** or **Virtualenv**. That creates a new empty environment inside the project, and the camera libraries won't be in it.

## 6. Create a run configuration

1. Open the `camera_test.py` file, or any Python file in `tools`.
2. Click the run configuration dropdown at the top right, then **Edit Configurations**.
3. Click **+** and choose **Python**.
4. Set:
   - **Name:** `camera_test`
   - **Interpreter:** the `C:\Users\Asus\pyenv\camera\Scripts\python.exe` entry
   - **Script path:** the full path to `camera_test.py` in the project root
   - **Working directory:** the project folder, `C:\Users\Asus\Downloads\parking-system\parking-system`
5. Click **OK**, then press the green **Run** arrow.

Make a second configuration the same way for `tools\plate_gate.py`, with its own name.

## 7. First run: downloads

The first time EasyOCR runs, it downloads its reading models (about 100 MB in total). The console shows `Downloading detection model` and `Downloading recognition model`. Wait for both to reach 100%.

Later runs start much faster, because the models are kept on the laptop.

The `UserWarning` about `torch.quantize_per_tensor` is harmless. It is a deprecation notice from PyTorch, not an error.

## Problems we hit, and fixes

| What you see | Cause | Fix |
|---|---|---|
| `pip : The term 'pip' is not recognized` | Python is not on PATH | Use `py -3.12 -m pip ...`, or reinstall Python 3.12 with **Add to PATH** ticked |
| `'py' is not recognized` | The Python launcher is not installed | Install Python 3.12 from python.org or with winget |
| `Preparing metadata ... error` for scikit-image, mentioning `Unknown compiler` | Python 3.15 is too new, so pip tries to build from source | Use Python 3.12 (step 1) |
| `Invalid requirement: 'easyocrC:\...activate'` | The `activate` command was pasted onto the same line as pip | Run one command per line, and skip `activate` |
| `unknown option --version` or `usage: ... python.exe` | Commands were pasted together in the terminal | Press Ctrl+C, then paste one command at a time |
| `Error: Please specify a Python SDK` in the run window | IntelliJ has no interpreter set | Set the interpreter (step 5) |
| `No module named cv2` or `No module named easyocr` | IntelliJ is using a different Python from the one with the libraries | Set the interpreter to `pyenv\camera\Scripts\python.exe` (step 5) |
| `CERTIFICATE_VERIFY_FAILED` during the model download | Python can't verify the download server's certificate, often because antivirus intercepts HTTPS | Run `C:\Users\Asus\pyenv\camera\Scripts\python.exe -m pip install --upgrade certifi`, then run again. If it still fails, turn off the antivirus HTTPS scanning briefly |
| `Camera not found` | Another app is using the webcam | Close Zoom, the Windows Camera app, and any browser tab that uses the camera. If you have more than one camera, change `VideoCapture(0)` to `VideoCapture(1)` |
| The camera window is black | Windows is blocking desktop apps from the camera | Go to **Settings > Privacy & security > Camera** and allow desktop apps |
| `(nothing read)` | OCR can't find text in the frame | Move the plate closer so it fills the frame, and improve the light |

## Quick checklist

- [ ] `py -3.12 --version` shows 3.12
- [ ] `pyenv\camera\Scripts\python.exe --version` shows 3.12
- [ ] The `import cv2, easyocr` check prints `ready`
- [ ] IntelliJ's interpreter is `pyenv\camera\Scripts\python.exe`
- [ ] The run configuration uses that interpreter and the project folder as its working directory


## Cameras: laptop camera and USB camera

`tools/plate_gate.py` uses two cameras by default: camera **0** is the entrance (IN) and camera **1** is the exit (OUT).

- **Laptop camera and a USB camera:** the laptop camera is usually 0 and the USB camera 1. Run `python tools/plate_gate.py --list-cameras` to see which numbers work. If they are the wrong way round, run it with `--in-cam 1 --out-cam 0`.
- **Only the laptop camera:** run `python tools/plate_gate.py --out-cam -1`, or just run it normally: if camera 1 is not found, the script carries on with the one that works. With one camera, press **I** in the window to switch it between IN and OUT.
- **A phone as the second camera:** give the phone app's video address instead of a number, for example `--out-cam http://192.168.1.23:8080/video`. The phone and the laptop must be on the same Wi-Fi. See `GATE_SETUP.md`, "Using a phone as one of the cameras".

## Using the ESP32

If an ESP32 moves the barrier, install `pyserial` too (`python.exe -m pip install pyserial`, the same environment) and run
`tools\plate_gate.py` with the parameter `--esp32 COM5` (use the port shown in Device Manager). See `docs/HARDWARE.md`, section 6.
