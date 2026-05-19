package de.kai_morich.simple_bluetooth_le_terminal;

import android.content.Context;
import android.content.SharedPreferences;

public class AppSettings {
    
    private static final String PREFS_NAME = "StethoscopeAppPreferences";
    
    // Audio settings keys
    private static final String KEY_PLAYBACK_SAMPLE_RATE = "playback_sample_rate";
    private static final String KEY_BUFFER_SIZE = "buffer_size";
    private static final String KEY_WAVEFORM_WINDOW = "waveform_window";
    private static final String KEY_USE_SIMPLE_DUPLICATION = "use_simple_duplication";
    
    // Heartbeat detection settings keys
    private static final String KEY_MOVING_AVG_WINDOW = "moving_avg_window";
    private static final String KEY_PEAK_THRESHOLD = "peak_threshold";
    private static final String KEY_THRESHOLD_INT = "threshold_int";
    private static final String KEY_MIN_PEAK_DISTANCE = "min_peak_distance";
    private static final String KEY_MIN_INTERVAL_SECONDS = "min_interval_seconds";
    private static final String KEY_BPM_SMOOTHING = "bpm_smoothing";
    
    // Default values
    private static final int DEFAULT_PLAYBACK_SAMPLE_RATE = 8000;  // Changed from 7000 to Android-supported rate
    private static final int DEFAULT_BUFFER_SIZE = 2048;  // Reduced from 16384 for lower latency at 8kHz (~256ms instead of ~2s)
    private static final float DEFAULT_WAVEFORM_WINDOW = 4.5f;
    private static final boolean DEFAULT_USE_SIMPLE_DUPLICATION = true;
    private static final int DEFAULT_MOVING_AVG_WINDOW = 50;
    private static final double DEFAULT_PEAK_THRESHOLD = 0.5;
    private static final int DEFAULT_MIN_PEAK_DISTANCE = 25;
    private static final int DEFAULT_BPM_SMOOTHING = 5;
    private static final int DEFAULT_THRESHOLD_INT = 500; // HeartbeatDetector uses int (10-1000)
    private static final double DEFAULT_MIN_INTERVAL_SECONDS = 0.3; // 0.3 seconds between beats
    
    private SharedPreferences prefs;
    
    public AppSettings(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    // Audio settings
    public int getPlaybackSampleRate() {
        int rate = prefs.getInt(KEY_PLAYBACK_SAMPLE_RATE, DEFAULT_PLAYBACK_SAMPLE_RATE);
        return validateSampleRate(rate);
    }
    
    public void setPlaybackSampleRate(int sampleRate) {
        int validRate = validateSampleRate(sampleRate);
        prefs.edit().putInt(KEY_PLAYBACK_SAMPLE_RATE, validRate).apply();
    }
    
    private int validateSampleRate(int rate) {
        // Android AudioTrack supports specific sample rates
        // Common rates: 8000, 11025, 16000, 22050, 44100, 48000
        int[] supportedRates = {8000, 11025, 16000, 22050, 44100, 48000};
        
        // If rate is already supported, return it
        for (int supported : supportedRates) {
            if (rate == supported) return rate;
        }
        
        // Find closest supported rate
        int closest = supportedRates[0];
        int minDiff = Math.abs(rate - closest);
        for (int supported : supportedRates) {
            int diff = Math.abs(rate - supported);
            if (diff < minDiff) {
                minDiff = diff;
                closest = supported;
            }
        }
        return closest;
    }
    
    public int getBufferSize() {
        return prefs.getInt(KEY_BUFFER_SIZE, DEFAULT_BUFFER_SIZE);
    }
    
    public void setBufferSize(int bufferSize) {
        prefs.edit().putInt(KEY_BUFFER_SIZE, bufferSize).apply();
    }
    
    public float getWaveformWindow() {
        return prefs.getFloat(KEY_WAVEFORM_WINDOW, DEFAULT_WAVEFORM_WINDOW);
    }
    
    public void setWaveformWindow(float windowSeconds) {
        prefs.edit().putFloat(KEY_WAVEFORM_WINDOW, windowSeconds).apply();
    }
    
    public boolean getUseSimpleDuplication() {
        return prefs.getBoolean(KEY_USE_SIMPLE_DUPLICATION, DEFAULT_USE_SIMPLE_DUPLICATION);
    }
    
    public void setUseSimpleDuplication(boolean use) {
        prefs.edit().putBoolean(KEY_USE_SIMPLE_DUPLICATION, use).apply();
    }
    
    // Heartbeat detection settings
    public int getMovingAvgWindow() {
        return prefs.getInt(KEY_MOVING_AVG_WINDOW, DEFAULT_MOVING_AVG_WINDOW);
    }
    
    public void setMovingAvgWindow(int window) {
        prefs.edit().putInt(KEY_MOVING_AVG_WINDOW, window).apply();
    }
    
    public double getPeakThreshold() {
        return Double.longBitsToDouble(prefs.getLong(KEY_PEAK_THRESHOLD, 
            Double.doubleToLongBits(DEFAULT_PEAK_THRESHOLD)));
    }
    
    public void setPeakThreshold(double threshold) {
        prefs.edit().putLong(KEY_PEAK_THRESHOLD, Double.doubleToLongBits(threshold)).apply();
    }
    
    public int getThresholdInt() {
        return prefs.getInt(KEY_THRESHOLD_INT, DEFAULT_THRESHOLD_INT);
    }
    
    public void setThresholdInt(int threshold) {
        prefs.edit().putInt(KEY_THRESHOLD_INT, threshold).apply();
    }
    
    public int getMinPeakDistance() {
        return prefs.getInt(KEY_MIN_PEAK_DISTANCE, DEFAULT_MIN_PEAK_DISTANCE);
    }
    
    public void setMinPeakDistance(int distance) {
        prefs.edit().putInt(KEY_MIN_PEAK_DISTANCE, distance).apply();
    }
    
    public double getMinIntervalSeconds() {
        return Double.longBitsToDouble(prefs.getLong(KEY_MIN_INTERVAL_SECONDS, 
            Double.doubleToLongBits(DEFAULT_MIN_INTERVAL_SECONDS)));
    }
    
    public void setMinIntervalSeconds(double seconds) {
        prefs.edit().putLong(KEY_MIN_INTERVAL_SECONDS, Double.doubleToLongBits(seconds)).apply();
    }
    
    public int getBpmSmoothing() {
        return prefs.getInt(KEY_BPM_SMOOTHING, DEFAULT_BPM_SMOOTHING);
    }
    
    public void setBpmSmoothing(int smoothing) {
        prefs.edit().putInt(KEY_BPM_SMOOTHING, smoothing).apply();
    }
    
    // Reset to defaults
    public void resetToDefaults() {
        prefs.edit().clear().apply();
    }
}
