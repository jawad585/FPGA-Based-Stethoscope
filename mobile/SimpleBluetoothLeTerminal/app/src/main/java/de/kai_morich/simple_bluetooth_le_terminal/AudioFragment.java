package de.kai_morich.simple_bluetooth_le_terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AudioFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected { False, Pending, True }

    // === Audio Settings ===
    private static final int FPGA_SAMPLE_RATE = 7000;  // FPGA sample rate at 230400 baud
    private int playbackSampleRate = 7000;  // Android-supported sample rate (configurable)
    private static final int CHANNELS = 1;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    
    // Resampling ratio: playbackSampleRate / 7000
    private double resampleRatio = (double) playbackSampleRate / FPGA_SAMPLE_RATE;
    private double sampleAccumulator = 0.0;
    private boolean useSimpleDuplication = true; // Use simple pass-through for 7000 Hz (no resampling needed)
    
    // Settings manager
    private AppSettings appSettings;

    private String deviceAddress;
    private SerialService service;
    private TextView statusText;
    private TextView sampleRateDisplay;
    private WaveformView waveformView;
    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean isPlaying = false;

    // Heartbeat detection
    private HeartbeatDetector heartbeatDetector;
    private TextView bpmDisplay;
    private TextView debugValuesDisplay;
    
    // Update counter for debug display (update every N samples)
    private int debugUpdateCounter = 0;
    private static final int DEBUG_UPDATE_INTERVAL = 10;  // Update every 10 sample batches

    // Audio playback
    private AudioTrack audioTrack;
    private ByteBuffer audioBuffer;
    private int bufferSize = 16384; // Configurable buffer for continuous audio (16KB)
    private float waveformWindowSeconds = 4.5f; // Configurable waveform display window
    
    // Diagnosis mode
    private boolean diagnosisMode = false;
    private int patientId = -1;
    private String patientName = "";
    private Button recordBtn;
    private boolean isRecording = false;
    private List<Byte> recordedData;
    private long recordingStartTime = 0;
    private int currentHeartRate = 0;
    private PatientDatabaseHelper dbHelper;

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
        
        // Initialize settings
        appSettings = new AppSettings(getActivity());
        
        // Load saved settings
        playbackSampleRate = appSettings.getPlaybackSampleRate();
        bufferSize = appSettings.getBufferSize();
        waveformWindowSeconds = appSettings.getWaveformWindow();
        useSimpleDuplication = appSettings.getUseSimpleDuplication();
        resampleRatio = (double) playbackSampleRate / FPGA_SAMPLE_RATE;
        
        // Check if in diagnosis mode
        if (getArguments() != null) {
            diagnosisMode = getArguments().getBoolean("diagnosis_mode", false);
            if (diagnosisMode) {
                patientId = getArguments().getInt("patient_id", -1);
                patientName = getArguments().getString("patient_name", "");
                dbHelper = new PatientDatabaseHelper(getActivity());
                recordedData = new ArrayList<>();
            }
        }
        
        initAudioTrack();
        
        // Initialize heartbeat detector
        heartbeatDetector = new HeartbeatDetector();
        heartbeatDetector.setSampleRate(playbackSampleRate);
        
        // Load heartbeat detector settings
        heartbeatDetector.setMovingAverageSize(appSettings.getMovingAvgWindow());
        heartbeatDetector.setThreshold(appSettings.getThresholdInt());
        heartbeatDetector.setMinIntervalSeconds(appSettings.getMinIntervalSeconds());
        
        heartbeatDetector.setBPMListener(bpm -> {
            if (getActivity() != null) {
                currentHeartRate = bpm;
                getActivity().runOnUiThread(() -> updateBPMDisplay(bpm));
            }
        });
        heartbeatDetector.setPeakListener(() -> {
            if (getActivity() != null && waveformView != null) {
                getActivity().runOnUiThread(() -> waveformView.showPeakDetected());
            }
        });
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        stopAudio();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class));
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null && getActivity() != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed() && getActivity() != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_audio, container, false);
        statusText = view.findViewById(R.id.audio_status_text);
        waveformView = view.findViewById(R.id.waveform_view);
        waveformView.setSampleRate(playbackSampleRate);
        waveformView.setDisplayDuration(waveformWindowSeconds);
        
        // Heartbeat UI elements
        bpmDisplay = view.findViewById(R.id.bpm_display);
        debugValuesDisplay = view.findViewById(R.id.debug_values_display);
        
        // Sample rate display
        sampleRateDisplay = view.findViewById(R.id.sample_rate_display);
        updateSampleRateDisplay();
        
        View playBtn = view.findViewById(R.id.play_btn);
        playBtn.setOnClickListener(v -> toggleAudio());
        
        // Record button (only visible in diagnosis mode)
        recordBtn = view.findViewById(R.id.record_btn);
        if (diagnosisMode) {
            recordBtn.setVisibility(View.VISIBLE);
            recordBtn.setOnClickListener(v -> toggleRecording());
            if (getActivity() != null) {
                getActivity().setTitle(patientName + " - Diagnosis");
            }
        }
        
        return view;
    }
    

    

    
    private void updateBPMDisplay(int bpm) {
        if (bpmDisplay != null) {
            if (bpm == 0) {
                bpmDisplay.setText("--");
            } else {
                bpmDisplay.setText(String.valueOf(bpm));
            }
        }
    }
    
    private void updateDebugDisplay() {
        if (debugValuesDisplay != null && heartbeatDetector != null) {
            short sample = heartbeatDetector.getLastSample();
            short baseline = heartbeatDetector.getBaseline();
            int deviation = Math.abs(heartbeatDetector.getCurrentDeviation());
            int threshold = heartbeatDetector.getThreshold();
            
            String debugText = String.format(
                "Sample: %d  |  Baseline: %d  |  Dev: %d  |  Thresh: %d",
                sample, baseline, deviation, threshold
            );
            
            // Add indicator when deviation exceeds threshold
            if (deviation > threshold) {
                debugText += "  🔴 PEAK!";
            }
            
            debugValuesDisplay.setText(debugText);
        }
    }
    
    private void applySampleRate(int newRate) {
        // Validate sample rate (Android supports 4000 Hz minimum)
        if (newRate < 4000 || newRate > 192000) {
            Toast.makeText(getActivity(), "Sample rate must be between 4000 and 192000 Hz (Android limitation)", 
                    Toast.LENGTH_LONG).show();
            return;
        }
        
        if (newRate != playbackSampleRate) {
            boolean wasPlaying = isPlaying;
            if (wasPlaying) {
                stopAudio();
            }
            
            try {
                playbackSampleRate = newRate;
                resampleRatio = (double) playbackSampleRate / FPGA_SAMPLE_RATE;
                
                // Use simple duplication for exact multiples (14000=2x, 28000=4x) for cleaner audio
                useSimpleDuplication = (playbackSampleRate == 7000 || playbackSampleRate == 14000 || 
                                       playbackSampleRate == 21000 || playbackSampleRate == 28000);
                appSettings.setUseSimpleDuplication(useSimpleDuplication);
                
                initAudioTrack();
                
                // Update waveform view sample rate
                if (waveformView != null) {
                    waveformView.setSampleRate(newRate);
                }
                
                // Update heartbeat detector sample rate
                if (heartbeatDetector != null) {
                    heartbeatDetector.setSampleRate(newRate);
                }
                
                // Verify AudioTrack was initialized successfully
                if (audioTrack == null || audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                    throw new IllegalStateException("AudioTrack failed to initialize with " + newRate + " Hz");
                }
                
                updateSampleRateDisplay();
                String mode = useSimpleDuplication ? " (clean duplication)" : " (interpolation)";
                status("Sample rate changed to " + newRate + " Hz" + mode);
                
                if (wasPlaying && connected == Connected.True) {
                    startAudio();
                }
            } catch (Exception e) {
                Toast.makeText(getActivity(), "Failed to set " + newRate + " Hz. Using 8000 Hz instead.", 
                        Toast.LENGTH_LONG).show();
                playbackSampleRate = 8000;
                resampleRatio = (double) playbackSampleRate / FPGA_SAMPLE_RATE;
                initAudioTrack();
                updateSampleRateDisplay();
                status("ERROR: " + e.getMessage() + ". Reverted to 8000 Hz");
            }
        }
    }
    
    private void updateSampleRateDisplay() {
        if (sampleRateDisplay != null) {
            double ratio = (double) playbackSampleRate / FPGA_SAMPLE_RATE;
            String ratioStr = String.format("%.1fx", ratio);
            sampleRateDisplay.setText("Sample Rate: " + playbackSampleRate + " Hz (" + ratioStr + " FPGA rate)");
        }
    }
    
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_audio, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.audio_settings) {
            showSettingsDialog();
            return true;
        } else if (id == R.id.clear_audio) {
            if (audioTrack != null) {
                audioTrack.pause();
                audioTrack.flush();
                if (isPlaying) {
                    audioTrack.play();
                }
            }
            if (waveformView != null) {
                waveformView.clear();
            }
            if (heartbeatDetector != null) {
                heartbeatDetector.reset();
                updateBPMDisplay(0);
            }
            status("Audio buffer cleared");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_audio_settings, null);
        
        // Get references to dialog views
        AutoCompleteTextView dialogSampleRateInput = dialogView.findViewById(R.id.dialog_sample_rate_input);
        EditText dialogBufferSizeInput = dialogView.findViewById(R.id.dialog_buffer_size_input);
        EditText dialogWaveformWindowInput = dialogView.findViewById(R.id.dialog_waveform_window_input);
        EditText dialogMaSizeInput = dialogView.findViewById(R.id.dialog_ma_size_input);
        EditText dialogThresholdInput = dialogView.findViewById(R.id.dialog_threshold_input);
        EditText dialogMinIntervalInput = dialogView.findViewById(R.id.dialog_min_interval_input);
        
        // Setup sample rate suggestions
        String[] sampleRateSuggestions = {"7000", "8000", "11025", "14000", "16000", "22050", "44100", "48000"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), 
                android.R.layout.simple_dropdown_item_1line, sampleRateSuggestions);
        dialogSampleRateInput.setAdapter(adapter);
        dialogSampleRateInput.setThreshold(1);
        
        // Set current values
        dialogSampleRateInput.setText(String.valueOf(playbackSampleRate));
        dialogBufferSizeInput.setText(String.valueOf(bufferSize));
        dialogWaveformWindowInput.setText(String.valueOf(waveformWindowSeconds));
        if (heartbeatDetector != null) {
            dialogMaSizeInput.setText(String.valueOf(heartbeatDetector.getMovingAverageSize()));
            dialogThresholdInput.setText(String.valueOf(heartbeatDetector.getThreshold()));
            dialogMinIntervalInput.setText(String.valueOf(heartbeatDetector.getMinIntervalSeconds()));
        }
        
        builder.setView(dialogView)
            .setTitle("Audio & Heartbeat Settings")
            .setPositiveButton("Apply", (dialog, which) -> {
                try {
                    // Apply sample rate
                    int newRate = Integer.parseInt(dialogSampleRateInput.getText().toString().trim());
                    applySampleRate(newRate);
                    appSettings.setPlaybackSampleRate(newRate);
                    
                    // Apply buffer size
                    int newBufferSize = Integer.parseInt(dialogBufferSizeInput.getText().toString().trim());
                    if (newBufferSize >= 1024 && newBufferSize <= 65536) {
                        bufferSize = newBufferSize;
                        appSettings.setBufferSize(newBufferSize);
                        initAudioTrack();
                        status("Buffer size updated to " + bufferSize + " bytes");
                    } else {
                        Toast.makeText(getActivity(), "Buffer size must be between 1024 and 65536 bytes", Toast.LENGTH_LONG).show();
                    }
                    
                    // Apply waveform window duration
                    float newWindowDuration = Float.parseFloat(dialogWaveformWindowInput.getText().toString().trim());
                    if (newWindowDuration >= 0.1f && newWindowDuration <= 10.0f) {
                        waveformWindowSeconds = newWindowDuration;
                        appSettings.setWaveformWindow(newWindowDuration);
                        if (waveformView != null) {
                            waveformView.setDisplayDuration(newWindowDuration);
                        }
                        status("Waveform window updated to " + newWindowDuration + " seconds");
                    } else {
                        Toast.makeText(getActivity(), "Waveform window must be between 0.1 and 10.0 seconds", Toast.LENGTH_LONG).show();
                    }
                    
                    // Apply heartbeat parameters
                    if (heartbeatDetector != null) {
                        int maSize = Integer.parseInt(dialogMaSizeInput.getText().toString().trim());
                        heartbeatDetector.setMovingAverageSize(maSize);
                        appSettings.setMovingAvgWindow(maSize);
                        
                        int threshold = Integer.parseInt(dialogThresholdInput.getText().toString().trim());
                        heartbeatDetector.setThreshold(threshold);
                        appSettings.setThresholdInt(threshold);
                        
                        double minInterval = Double.parseDouble(dialogMinIntervalInput.getText().toString().trim());
                        heartbeatDetector.setMinIntervalSeconds(minInterval);
                        appSettings.setMinIntervalSeconds(minInterval);
                        
                        status("Settings saved and applied successfully");
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(getActivity(), "Invalid parameter value", Toast.LENGTH_SHORT).show();
                } catch (IllegalArgumentException e) {
                    Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .create()
            .show();
    }

    /*
     * Audio Setup
     */
    private void initAudioTrack() {
        // Release existing AudioTrack if any
        if (audioTrack != null) {
            audioTrack.release();
        }
        
        int minBufferSize = AudioTrack.getMinBufferSize(playbackSampleRate, 
                AudioFormat.CHANNEL_OUT_MONO, 
                ENCODING);

        // Ensure buffer is large enough for continuous playback with resampling
        int audioTrackBufferSize = Math.max(minBufferSize, bufferSize * 2);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        AudioFormat audioFormat = new AudioFormat.Builder()
                .setSampleRate(playbackSampleRate)
                .setEncoding(ENCODING)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();

        audioTrack = new AudioTrack(audioAttributes, audioFormat, audioTrackBufferSize,
                AudioTrack.MODE_STREAM, android.media.AudioManager.AUDIO_SESSION_ID_GENERATE);

        audioBuffer = ByteBuffer.allocate(bufferSize);
        audioBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    private void toggleAudio() {
        if (isPlaying) {
            stopAudio();
        } else {
            startAudio();
        }
    }

    private void startAudio() {
        if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            audioTrack.play();
            isPlaying = true;
            status("Audio playback started");
        } else {
            Toast.makeText(getActivity(), "Audio not initialized", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopAudio() {
        if (audioTrack != null) {
            if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack.pause();
                audioTrack.flush();
            }
            audioTrack.release();
        }
        isPlaying = false;
        status("Audio playback stopped");
    }

    /*
     * Serial + Audio Processing
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("Connecting...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        stopAudio();
        service.disconnect();
    }

    private void processAudioData(byte[] data) {
        if (audioTrack == null || !isPlaying) {
            return;
        }
        
        // Capture raw data if recording
        if (isRecording && diagnosisMode) {
            for (byte b : data) {
                recordedData.add(b);
            }
        }

        // Convert 8-bit unsigned [0-255] to signed 16-bit samples
        short[] inputSamples = new short[data.length];
        for (int i = 0; i < data.length; i++) {
            int unsignedValue = data[i] & 0xFF;
            inputSamples[i] = (short)((unsignedValue - 128) << 8);
        }
        
        short[] resampledData;
        
        if (useSimpleDuplication) {
            // Simple duplication for exact multiples (7000=1x, 14000=2x, 28000=4x, etc.)
            // This produces cleaner audio without interpolation artifacts
            int duplicateFactor = (int) Math.round(resampleRatio);
            resampledData = new short[inputSamples.length * duplicateFactor];
            
            for (int i = 0; i < inputSamples.length; i++) {
                for (int j = 0; j < duplicateFactor; j++) {
                    resampledData[i * duplicateFactor + j] = inputSamples[i];
                }
            }
        } else {
            // Linear interpolation for non-exact multiples
            int outputLength = (int) Math.ceil(data.length * resampleRatio);
            resampledData = new short[outputLength];
            
            for (int i = 0; i < outputLength; i++) {
                // Calculate source position in input array
                double srcPos = i / resampleRatio;
                int srcIndex = (int) srcPos;
                double fraction = srcPos - srcIndex;
                
                if (srcIndex < inputSamples.length - 1) {
                    // Linear interpolation between two samples
                    short sample1 = inputSamples[srcIndex];
                    short sample2 = inputSamples[srcIndex + 1];
                    resampledData[i] = (short) (sample1 + fraction * (sample2 - sample1));
                } else if (srcIndex < inputSamples.length) {
                    // Last sample, no interpolation
                    resampledData[i] = inputSamples[srcIndex];
                }
            }
        }
        
        // Convert to byte array for AudioTrack
        byte[] audioData = new byte[resampledData.length * 2];
        for (int i = 0; i < resampledData.length; i++) {
            audioData[i * 2] = (byte)(resampledData[i] & 0xFF);
            audioData[i * 2 + 1] = (byte)((resampledData[i] >> 8) & 0xFF);
        }

        // Update waveform view with resampled data
        if (waveformView != null) {
            waveformView.addSamples(resampledData);
        }
        
        // Process heartbeat detection on resampled data
        if (heartbeatDetector != null) {
            heartbeatDetector.processSamples(resampledData);
            
            // Update debug display periodically (not every sample to reduce UI overhead)
            debugUpdateCounter++;
            if (debugUpdateCounter >= DEBUG_UPDATE_INTERVAL) {
                debugUpdateCounter = 0;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(this::updateDebugDisplay);
                }
            }
        }

        // Write to AudioTrack
        audioTrack.write(audioData, 0, audioData.length);
    }
    
    /*
     * Recording functionality
     */
    private void toggleRecording() {
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }
    
    private void startRecording() {
        if (!isPlaying) {
            Toast.makeText(getActivity(), "Please start audio playback first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        recordedData.clear();
        recordingStartTime = System.currentTimeMillis();
        isRecording = true;
        recordBtn.setText("⏹ Stop");
        recordBtn.setBackgroundColor(0xFFFF0000);
        status("Recording started...");
    }
    
    private void stopRecording() {
        isRecording = false;
        recordBtn.setText("⏺ Record");
        recordBtn.setBackgroundColor(0xFF6200EE);
        
        long duration = (System.currentTimeMillis() - recordingStartTime) / 1000;
        status("Recording stopped. Duration: " + duration + "s");
        
        // Save recording
        saveRecording(duration);
    }
    
    private void saveRecording(long durationSeconds) {
        if (recordedData.isEmpty()) {
            Toast.makeText(getActivity(), "No data recorded", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            // Create recordings directory
            File recordingsDir = new File(getActivity().getExternalFilesDir(null), "recordings");
            if (!recordingsDir.exists()) {
                recordingsDir.mkdirs();
            }
            
            // Create filename with timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String timestamp = sdf.format(new Date(recordingStartTime));
            String filename = "recording_" + patientId + "_" + timestamp + ".raw";
            File file = new File(recordingsDir, filename);
            
            // Write raw audio data
            FileOutputStream fos = new FileOutputStream(file);
            byte[] dataArray = new byte[recordedData.size()];
            for (int i = 0; i < recordedData.size(); i++) {
                dataArray[i] = recordedData.get(i);
            }
            fos.write(dataArray);
            fos.close();
            
            // Save to database
            DiagnosisRecord record = new DiagnosisRecord();
            record.setPatientId(patientId);
            record.setRecordingPath(file.getAbsolutePath());
            record.setHeartRate(currentHeartRate);
            record.setDurationSeconds((int) durationSeconds);
            record.setNotes("");
            record.setSampleRate(FPGA_SAMPLE_RATE); // Save the actual FPGA sample rate, not the resampled rate
            
            long recordId = dbHelper.addDiagnosisRecord(record);
            
            status("Recording saved successfully (ID: " + recordId + ")");
            Toast.makeText(getActivity(), "Diagnosis recorded", Toast.LENGTH_SHORT).show();
            
        } catch (IOException e) {
            status("ERROR: Failed to save recording - " + e.getMessage());
            Toast.makeText(getActivity(), "Failed to save recording", Toast.LENGTH_LONG).show();
        }
    }

    private void status(String str) {
        if (statusText != null && getActivity() != null) {
            getActivity().runOnUiThread(() -> 
                statusText.append(str + "\n")
            );
        }
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("Connected to FPGA");
        connected = Connected.True;
        // Auto-start audio playback on connection
        startAudio();
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("Connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        // Process incoming audio data
        processAudioData(data);
    }

    @Override
    public void onSerialRead(ArrayDeque<byte[]> datas) {
        // Process multiple chunks
        for (byte[] data : datas) {
            processAudioData(data);
        }
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("Connection lost: " + e.getMessage());
        disconnect();
    }
}
