# APK Rebuild and Install (USB)

This file contains the requirements and commands to rebuild and install the app APK from PowerShell.

## Requirements

- Windows PowerShell
- Java JDK 17 or newer
- Android SDK installed in `%LOCALAPPDATA%\Android\Sdk`
- USB cable connected to Android phone
- Developer options and USB debugging enabled on phone
- Device authorized for `adb` (accept RSA prompt on phone)

Note: USB tethering is not required for `adb` install. USB debugging is required.

## Project Path

```powershell
Set-Location "D:\fypFPGA\mega\sixth\fourth\mobile\SimpleBluetoothLeTerminal"
```

## 1) Fresh Rebuild

```powershell
.\gradlew.bat clean assembleDebug --no-daemon
```

Expected APK:

- `app\build\outputs\apk\debug\app-debug.apk`

## 2) Verify Device Connection

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adb devices
```

You should see your device listed as `device`.

## 3) Install APK

```powershell
$apk = '.\app\build\outputs\apk\debug\app-debug.apk'
& $adb install -r $apk
```

## If Install Fails With Signature Mismatch

Error example:

- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

Fix:

```powershell
$pkg = 'de.kai_morich.simple_bluetooth_le_terminal'
& $adb uninstall $pkg
& $adb install $apk
```

## Optional: Verify Installed Package

```powershell
& $adb shell pm list packages | Select-String 'de.kai_morich.simple_bluetooth_le_terminal'
```
