# FPGA Audio Streaming - Android App Implementation

## Summary
The Android app has been successfully modified to receive and play audio data from the FPGA via Bluetooth Low Energy, matching the functionality of your Python script.

## What Was Added

### 1. **AudioFragment.java**
A new fragment that handles real-time audio streaming from the FPGA:

- **Audio Configuration:**
  - Sample Rate: 3500 Hz (matches your Python script)
  - Format: Converts 8-bit unsigned data to 16-bit signed PCM
  - Uses Android's `AudioTrack` for real-time audio playback

- **Data Processing:**
  ```java
  // Matches Python: sample_signed = (raw[0] - 128) << 8
  int unsignedValue = data[i] & 0xFF;
  short sampleSigned = (short)((unsignedValue - 128) << 8);
  ```

- **Features:**
  - Auto-starts audio playback when connected to FPGA
  - Real-time status updates
  - Start/Stop audio control
  - Buffer management

### 2. **fragment_audio.xml**
User interface for the audio streaming mode:
- Status display showing connection and playback information
- Sample rate and format information
- Play/Stop button for manual control
- Clean, simple layout

### 3. **menu_audio.xml**
Menu with option to clear the audio buffer

### 4. **DevicesFragment.java** (Modified)
Added mode selection dialog when connecting to a device:
- **Terminal Mode**: Original text-based terminal
- **Audio Stream Mode**: New audio playback mode

## How to Use

1. **Build and Install the App**
   - Open the project in Android Studio
   - Build and install to your Android device

2. **Connect to FPGA**
   - Launch the app
   - Scan for Bluetooth devices
   - Select your FPGA/HC-05 device
   - Choose "Audio Stream" from the dialog

3. **Audio Playback**
   - The app will automatically start playing audio when connected
   - Use the "Start Audio" button to manually control playback
   - Status messages show connection state and playback info

## Technical Details

### Data Flow
```
FPGA → Bluetooth → Android App → AudioTrack → Speaker
        (8-bit)                    (16-bit PCM)
```

### Conversion Logic
The app matches your Python script exactly:
- Receives 8-bit unsigned data [0-255] from FPGA
- Converts to signed: `value - 128`
- Shifts left 8 bits to create 16-bit signed samples [-32768, 32767]
- Writes to AudioTrack for playback

### Performance
- Uses `AudioTrack.MODE_STREAM` for low-latency real-time playback
- Buffer size automatically optimized for your sample rate
- Handles incoming data asynchronously via `SerialListener`

## Differences from Python Script

| Python Script | Android App |
|--------------|-------------|
| pySerial (UART) | Bluetooth LE |
| PyAudio | AudioTrack |
| Blocking reads | Event-driven (SerialListener) |
| Manual start/stop | Auto-start on connect |

## Notes

- The sample rate is set to 3500 Hz to match your FPGA configuration
- If you need to change the sample rate, modify the `SAMPLE_RATE` constant in `AudioFragment.java`
- The app maintains the same audio quality as your Python script
- All Bluetooth permissions are already configured in AndroidManifest.xml

## Testing

To verify it works correctly:
1. Ensure your FPGA is sending the same data format (8-bit unsigned samples)
2. Connect via Bluetooth and select "Audio Stream"
3. You should hear the same audio output as with the Python script
