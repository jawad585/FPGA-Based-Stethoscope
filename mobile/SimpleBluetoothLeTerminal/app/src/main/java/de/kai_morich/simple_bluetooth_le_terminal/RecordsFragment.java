package de.kai_morich.simple_bluetooth_le_terminal;

import android.app.AlertDialog;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public class RecordsFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView emptyText;
    private RecordsAdapter adapter;
    private PatientDatabaseHelper dbHelper;
    private List<DiagnosisRecord> records;
    private int patientId;
    private String patientName;
    private AudioTrack playbackTrack;
    private boolean isPlaying = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            patientId = getArguments().getInt("patient_id");
            patientName = getArguments().getString("patient_name");
        }
        dbHelper = new PatientDatabaseHelper(getActivity());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_records, container, false);

        TextView title = view.findViewById(R.id.records_title);
        title.setText(patientName + " - Records");

        recyclerView = view.findViewById(R.id.records_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        emptyText = view.findViewById(R.id.empty_records_text);

        loadRecords();

        return view;
    }

    private void loadRecords() {
        records = dbHelper.getPatientRecords(patientId);
        
        if (records.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyText.setVisibility(View.GONE);
            adapter = new RecordsAdapter(records, this::onRecordClick, this::onRecordDelete);
            recyclerView.setAdapter(adapter);
        }
    }
    
    private void onRecordClick(DiagnosisRecord record) {
        // Show playback dialog with play/pause and analyze options
        AudioPlaybackDialog dialog = new AudioPlaybackDialog(getActivity(), record);
        dialog.show();
    }

    private void onRecordDelete(DiagnosisRecord record) {
        new AlertDialog.Builder(getActivity())
                .setTitle("Delete Record")
                .setMessage("Are you sure you want to delete this diagnosis record?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    dbHelper.deleteDiagnosisRecord(record.getId());
                    loadRecords();
                    Toast.makeText(getActivity(), "Record deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void playRecording(DiagnosisRecord record) {
        if (isPlaying) {
            Toast.makeText(getActivity(), "Already playing a recording", Toast.LENGTH_SHORT).show();
            return;
        }
        
        File recordingFile = new File(record.getRecordingPath());
        if (!recordingFile.exists()) {
            Toast.makeText(getActivity(), "Recording file not found", Toast.LENGTH_LONG).show();
            return;
        }
        
        new Thread(() -> {
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
                
                // Get the original sample rate from the recording
                int originalSampleRate = record.getSampleRate();
                if (originalSampleRate <= 0) {
                    originalSampleRate = 7000; // fallback (new default at 230400 baud)
                }
                
                // If sample rate is 3500 Hz (old FPGA rate at 115200 baud), resample to 7000 Hz for playback
                short[] playbackSamples;
                int playbackSampleRate;
                
                if (originalSampleRate == 3500) {
                    // Resample 3500 Hz to 7000 Hz (2x duplication)
                    playbackSampleRate = 7000;
                    playbackSamples = new short[audioSamples.length * 2];
                    for (int i = 0; i < audioSamples.length; i++) {
                        playbackSamples[i * 2] = audioSamples[i];
                        playbackSamples[i * 2 + 1] = audioSamples[i];
                    }
                } else {
                    // Use as-is for other sample rates
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
                
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> 
                        Toast.makeText(getActivity(), "Playing recording...", Toast.LENGTH_SHORT).show()
                    );
                }
                
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
                
                if (playbackTrack != null) {
                    playbackTrack.stop();
                    playbackTrack.release();
                    playbackTrack = null;
                }
                isPlaying = false;
                
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> 
                        Toast.makeText(getActivity(), "Playback finished", Toast.LENGTH_SHORT).show()
                    );
                }
                
            } catch (IOException | InterruptedException e) {
                if (playbackTrack != null) {
                    playbackTrack.stop();
                    playbackTrack.release();
                    playbackTrack = null;
                }
                isPlaying = false;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> 
                        Toast.makeText(getActivity(), "Playback error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                }
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        if (playbackTrack != null) {
            playbackTrack.stop();
            playbackTrack.release();
            playbackTrack = null;
        }
        if (dbHelper != null) {
            dbHelper.close();
        }
        super.onDestroy();
    }
}
