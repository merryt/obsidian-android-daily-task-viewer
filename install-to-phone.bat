@echo off
setlocal
set "ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB_PATH%" (
    set "ADB_PATH=adb"
)

echo ========================================================
echo  Installing Obsidian Daily Note Viewer to Device
echo ========================================================
echo Checking connected devices...
"%ADB_PATH%" devices -l

echo.
echo Installing APK (app-debug.apk)...
"%ADB_PATH%" install -r "%~dp0app\build\outputs\apk\debug\app-debug.apk"

if %ERRORLEVEL% equ 0 (
    echo.
    echo [SUCCESS] App installed successfully!
    echo Launching app...
    "%ADB_PATH%" shell am start -n com.obsidian.dailynote/.MainActivity
) else (
    echo.
    echo [FAILED] Installation failed. Ensure your phone is connected with USB Debugging enabled.
)

pause
