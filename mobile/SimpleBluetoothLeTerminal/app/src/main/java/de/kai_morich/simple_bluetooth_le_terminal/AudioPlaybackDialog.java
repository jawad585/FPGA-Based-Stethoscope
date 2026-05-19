package de.kai_morich.simple_bluetooth_le_terminal;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;
import androidx.core.content.FileProvider;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class AudioPlaybackDialog extends Dialog {
    
    private DiagnosisRecord record;
    private PatientDatabaseHelper dbHelper;
    private Context context;
    
    private TextView titleText;
    private TextView infoText;
    private ImageButton playPauseButton;
    private Button analyzeButton;
    private Button shareButton;
    private ProgressBar progressBar;
    private TextView analysisStatusText;
    private EditText analysisStartTime;
    private EditText analysisEndTime;
    
    private AudioTrack playbackTrack;
    private boolean isPlaying = false;
    private boolean isPaused = false;
    private Thread playbackThread;
    
    public AudioPlaybackDialog(@NonNull Context context, DiagnosisRecord record) {
        super(context);
        this.context = context;
        this.record = record;
        this.dbHelper = new PatientDatabaseHelper(context);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_audio_playback);
        
        // Set dialog width to 95% of screen width
        if (getWindow() != null) {
            android.view.WindowManager.LayoutParams params = getWindow().getAttributes();
            android.view.Display display = getWindow().getWindowManager().getDefaultDisplay();
            android.graphics.Point size = new android.graphics.Point();
            display.getSize(size);
            params.width = (int)(size.x * 0.95);
            getWindow().setAttributes(params);
        }
        
        titleText = findViewById(R.id.playback_title);
        infoText = findViewById(R.id.playback_info);
        playPauseButton = findViewById(R.id.play_pause_button);
        analyzeButton = findViewById(R.id.analyze_button);
        shareButton = findViewById(R.id.share_button);
        progressBar = findViewById(R.id.analysis_progress);
        analysisStatusText = findViewById(R.id.analysis_status);
        analysisStartTime = findViewById(R.id.analysis_start_time);
        analysisEndTime = findViewById(R.id.analysis_end_time);
        
        titleText.setText("Audio Recording");
        infoText.setText("Heart Rate: " + record.getHeartRate() + " BPM\nDuration: " + record.getDurationSeconds() + " seconds");
        
        // Check if already analyzed
        if (record.getAnalysisPath() != null && !record.getAnalysisPath().isEmpty()) {
            File analysisFile = new File(record.getAnalysisPath());
            if (analysisFile.exists()) {
                analysisStatusText.setText("Analysis available");
                analysisStatusText.setVisibility(View.VISIBLE);
                analyzeButton.setText("View Analysis");
            }
        }
        
        playPauseButton.setOnClickListener(v -> togglePlayback());
        analyzeButton.setOnClickListener(v -> analyzeAudio());
        shareButton.setOnClickListener(v -> shareRecording());
        
        Button closeButton = findViewById(R.id.close_button);
        closeButton.setOnClickListener(v -> {
            stopPlayback();
            dismiss();
        });
    }
    
    private void togglePlayback() {
        if (isPlaying) {
            pausePlayback();
        } else {
            startPlayback();
        }
    }
    
    private void startPlayback() {
        if (isPaused && playbackTrack != null) {
            // Resume playback
            playbackTrack.play();
            isPlaying = true;
            isPaused = false;
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
            return;
        }
        
        File recordingFile = new File(record.getRecordingPath());
        if (!recordingFile.exists()) {
            Toast.makeText(context, "Recording file not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        playbackThread = new Thread(() -> {
            try {
                // Read raw audio data
                FileInputStream fis = new FileInputStream(recordingFile);
                byte[] rawData = new byte[(int) recordingFile.length()];
                fis.read(rawData);
                fis.close();
                
                // Convert 8-bit unsigned to 16-bit signed PCM
                short[] audioSamples = new short[rawData.length];
                for (int i = 0; i < rawData.length; i++) {
                    int unsignedValue = rawData[i] & 0xFF;
                    audioSamples[i] = (short)((unsignedValue - 128) << 8);
                }
                
                int originalSampleRate = record.getSampleRate();
                if (originalSampleRate <= 0) {
                    originalSampleRate = 7000; // default (new FPGA rate at 230400 baud)
                }
                
                // Resample if needed (for backward compatibility with old 3500 Hz recordings)
                short[] playbackSamples;
                int playbackSampleRate;
                
                if (originalSampleRate == 3500) {
                    // Old recordings at 115200 baud - resample to 7000 Hz
                    playbackSampleRate = 7000;
                    playbackSamples = new short[audioSamples.length * 2];
                    for (int i = 0; i < audioSamples.length; i++) {
                        playbackSamples[i * 2] = audioSamples[i];
                        playbackSamples[i * 2 + 1] = audioSamples[i];
                    }
                } else {
                    playbackSampleRate = originalSampleRate;
                    playbackSamples = audioSamples;
                }
                
                int bufferSize = AudioTrack.getMinBufferSize(playbackSampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);
                
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
                
                AudioFormat audioFormat = new AudioFormat.Builder()
                        .setSampleRate(playbackSampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build();
                
                playbackTrack = new AudioTrack(audioAttributes, audioFormat,
                        Math.max(bufferSize, 4096),
                        AudioTrack.MODE_STREAM,
                        android.media.AudioManager.AUDIO_SESSION_ID_GENERATE);
                
                isPlaying = true;
                
                ((android.app.Activity) context).runOnUiThread(() -> {
                    playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
                    Toast.makeText(context, "Playing...", Toast.LENGTH_SHORT).show();
                });
                
                playbackTrack.play();
                
                // Convert to bytes for playback
                byte[] playbackData = new byte[playbackSamples.length * 2];
                for (int i = 0; i < playbackSamples.length; i++) {
                    playbackData[i * 2] = (byte)(playbackSamples[i] & 0xFF);
                    playbackData[i * 2 + 1] = (byte)((playbackSamples[i] >> 8) & 0xFF);
                }
                
                playbackTrack.write(playbackData, 0, playbackData.length);
                
                // Wait for playback to finish
                Thread.sleep(record.getDurationSeconds() * 1000);
                
                stopPlayback();
                
                ((android.app.Activity) context).runOnUiThread(() -> {
                    playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                });
                
            } catch (IOException | InterruptedException e) {
                stopPlayback();
                ((android.app.Activity) context).runOnUiThread(() -> 
                    Toast.makeText(context, "Playback error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        });
        playbackThread.start();
    }
    
    private void pausePlayback() {
        if (playbackTrack != null && isPlaying) {
            playbackTrack.pause();
            isPlaying = false;
            isPaused = true;
            playPauseButton.setImageResource(android.R.drawable.ic_media_play);
        }
    }
    
    private void stopPlayback() {
        isPlaying = false;
        isPaused = false;
        if (playbackTrack != null) {
            playbackTrack.stop();
            playbackTrack.release();
            playbackTrack = null;
        }
        if (playbackThread != null) {
            playbackThread.interrupt();
            playbackThread = null;
        }
    }
    
    private void analyzeAudio() {
        // Check if already analyzed
        if (record.getAnalysisPath() != null && !record.getAnalysisPath().isEmpty()) {
            File analysisFile = new File(record.getAnalysisPath());
            if (analysisFile.exists()) {
                // Open existing analysis
                openAnalysisResult(record.getAnalysisPath());
                return;
            }
        }
        
        // Get time range
        double startTime = 0;
        double endTime = -1; // -1 means full duration
        
        try {
            String startStr = analysisStartTime.getText().toString().trim();
            if (!startStr.isEmpty()) {
                startTime = Double.parseDouble(startStr);
            }
            
            String endStr = analysisEndTime.getText().toString().trim();
            if (!endStr.isEmpty()) {
                endTime = Double.parseDouble(endStr);
            }
            
            // Validate range
            if (startTime < 0) startTime = 0;
            if (endTime > 0 && endTime <= startTime) {
                Toast.makeText(context, "End time must be greater than start time", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(context, "Invalid time format", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Perform new analysis
        progressBar.setVisibility(View.VISIBLE);
        analysisStatusText.setText("Analyzing...");
        analysisStatusText.setVisibility(View.VISIBLE);
        analyzeButton.setEnabled(false);
        
        final double finalStartTime = startTime;
        final double finalEndTime = endTime;
        
        new Thread(() -> {
            try {
                AudioAnalyzer.AnalysisResult result = AudioAnalyzer.analyzeAudioSegment(
                    context, record.getRecordingPath(), record.getId(), finalStartTime, finalEndTime);
                
                // Store analysis path (using waveform image path as marker)
                String analysisPath = result.waveformImagePath;
                dbHelper.updateDiagnosisRecordAnalysis(record.getId(), analysisPath);
                record.setAnalysisPath(analysisPath);
                
                ((android.app.Activity) context).runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    analysisStatusText.setText("Analysis complete");
                    analyzeButton.setText("View Analysis");
                    analyzeButton.setEnabled(true);
                    
                    // Open analysis result
                    openAnalysisResult(analysisPath);
                });
                
            } catch (IOException e) {
                ((android.app.Activity) context).runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    analysisStatusText.setText("Analysis failed");
                    analyzeButton.setEnabled(true);
                    Toast.makeText(context, "Analysis error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    private void openAnalysisResult(String analysisPath) {
        Intent intent = new Intent(context, AnalysisResultActivity.class);
        intent.putExtra("record_id", record.getId());
        intent.putExtra("analysis_path", analysisPath);
        context.startActivity(intent);
    }
    
    private void shareRecording() {
        File recordingFile = new File(record.getRecordingPath());
        if (!recordingFile.exists()) {
            Toast.makeText(context, "Recording file not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(context, "Converting to WAV...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            try {
                // Convert raw audio to WAV format
                File wavFile = convertRawToWav(recordingFile);
                
                ((android.app.Activity) context).runOnUiThread(() -> {
                    try {
                        Uri fileUri = FileProvider.getUriForFile(context, 
                            context.getPackageName() + ".provider", wavFile);
                        
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("audio/wav");
                        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Heart Sound Recording");
                        shareIntent.putExtra(Intent.EXTRA_TEXT, 
                            "Heart Rate: " + record.getHeartRate() + " BPM\n" +
                            "Duration: " + record.getDurationSeconds() + " seconds\n" +
                            "Recorded: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date(record.getRecordedAt())));
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        
                        context.startActivity(Intent.createChooser(shareIntent, "Share Recording"));
                    } catch (Exception e) {
                        Toast.makeText(context, "Error sharing file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                ((android.app.Activity) context).runOnUiThread(() -> {
                    Toast.makeText(context, "Error converting audio: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private File convertRawToWav(File rawFile) throws IOException {
        // Read raw audio data
        FileInputStream fis = new FileInputStream(rawFile);
        byte[] rawData = new byte[(int) rawFile.length()];
        fis.read(rawData);
        fis.close();
        
        // Create WAV file in cache directory
        File cacheDir = context.getCacheDir();
        File wavFile = new File(cacheDir, "heart_recording_" + record.getId() + ".wav");
        
        // WAV parameters
        int sampleRate = record.getSampleRate();
        int numChannels = 1;
        int bitsPerSample = 8;
        
        // Write WAV file
        FileOutputStream fos = new FileOutputStream(wavFile);
        
        // RIFF header
        fos.write("RIFF".getBytes());
        writeInt(fos, 36 + rawData.length); // ChunkSize
        fos.write("WAVE".getBytes());
        
        // fmt subchunk
        fos.write("fmt ".getBytes());
        writeInt(fos, 16); // Subchunk1Size (16 for PCM)
        writeShort(fos, (short) 1); // AudioFormat (1 = PCM)
        writeShort(fos, (short) numChannels);
        writeInt(fos, sampleRate);
        writeInt(fos, sampleRate * numChannels * bitsPerSample / 8); // ByteRate
        writeShort(fos, (short) (numChannels * bitsPerSample / 8)); // BlockAlign
        writeShort(fos, (short) bitsPerSample);
        
        // data subchunk
        fos.write("data".getBytes());
        writeInt(fos, rawData.length);
        fos.write(rawData);
        
        fos.close();
        
        return wavFile;
    }
    
    private void writeInt(FileOutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }
    
    private void writeShort(FileOutputStream out, short value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        stopPlayback();
    }
    
    @Override
    public void dismiss() {
        stopPlayback();
        if (dbHelper != null) {
            dbHelper.close();
        }
        super.dismiss();
    }
}
