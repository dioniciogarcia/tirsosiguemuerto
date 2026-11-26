@echo off
REM ============================================================
REM  Builds WorkspaceHelper.exe from workspacehelper.py
REM  Double-click this file on your Windows PC to run it.
REM ============================================================

echo Installing the tools needed to build...
python -m pip install --upgrade pip
python -m pip install -r requirements.txt pyinstaller

echo.
echo Building WorkspaceHelper.exe ...
REM  --noconsole = no black window, runs in the tray only
REM  --onefile   = a single .exe
pyinstaller --noconsole --onefile --name WorkspaceHelper workspacehelper.py

echo.
echo ============================================================
echo  Done. Your program is here:
echo     dist\WorkspaceHelper.exe
echo.
echo  IMPORTANT: keep config.json in the SAME folder as the .exe
echo ============================================================
pause
