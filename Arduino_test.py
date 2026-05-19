import serial
import struct
import pyaudio
import threading
from collections import deque

# === SETTINGS ===
PORT = "COM10"
BAUD = 230400
SAMPLE_RATE = 8000
CHUNK = 512

# Ring buffer (~1 second)
audio_buffer = deque(maxlen=8000)

# Serial
ser = serial.Serial(PORT, BAUD, timeout=None)
ser.read(4096)  # flush startup junk

def serial_reader():
    while True:
        data = ser.read(256)
        for b in data:
            audio_buffer.append((b - 128) << 8)

threading.Thread(target=serial_reader, daemon=True).start()

# Audio
p = pyaudio.PyAudio()

dc = 0
alpha = 0.995  # DC blocker strength

def callback(in_data, frame_count, time_info, status):
    global dc
    out = []

    for _ in range(frame_count):
        if audio_buffer:
            x = audio_buffer.popleft()
        else:
            x = 0

        # DC removal (high-pass)
        dc = alpha * dc + (1 - alpha) * x
        out.append(x - int(dc))

    return (struct.pack('<' + 'h'*frame_count, *out),
            pyaudio.paContinue)

stream = p.open(format=pyaudio.paInt16,
                channels=1,
                rate=SAMPLE_RATE,
                output=True,
                frames_per_buffer=CHUNK,
                stream_callback=callback)

stream.start_stream()
print("8kHz audio streaming (clean & stable). Ctrl+C to stop.")

try:
    while stream.is_active():
        pass
except KeyboardInterrupt:
    pass

stream.stop_stream()
stream.close()
p.terminate()
ser.close()