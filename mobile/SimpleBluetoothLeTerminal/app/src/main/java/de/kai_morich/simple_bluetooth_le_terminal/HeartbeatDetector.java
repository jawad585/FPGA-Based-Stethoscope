package de.kai_morich.simple_bluetooth_le_terminal;

/**
 * Real-time heartbeat detection from audio signal
 * Implements moving average baseline tracking and peak detection
 * Calculates BPM (beats per minute) from inter-beat intervals
 */
public class HeartbeatDetector {
    
    // Configurable parameters
    private int movingAverageSize = 50;
    private int threshold = 40;
    private double minIntervalSeconds = 0.4;  // Minimum time between beats
    
    // Moving average state
    private short[] maBuffer;
    private int maIndex = 0;
    private long maSum = 0;
    private short baseline = 0;
    
    // Peak detection state
    private long cycleCounter = 0;
    private long lastPeak = 0;
    private long prevPeak = 0;
    private long interval = 0;
    
    // Sample rate (needed for timing calculations)
    private int sampleRate = 7000;  // Default
    
    // BPM result
    private int currentBPM = 0;
    private long lastBPMUpdate = 0;
    
    // Debug values
    private int currentDeviation = 0;
    private short lastSample = 0;
    
    // Callback for BPM updates
    public interface BPMListener {
        void onBPMUpdate(int bpm);
    }
    
    public interface PeakListener {
        void onPeakDetected();
    }
    
    private BPMListener listener;
    private PeakListener peakListener;
    
    public HeartbeatDetector() {
        maBuffer = new short[movingAverageSize];
        for (int i = 0; i < movingAverageSize; i++) {
            maBuffer[i] = 0;
        }
    }
    
    /**
     * Set the sample rate (must match the audio sample rate)
     */
    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }
    
    /**
     * Set moving average window size (default: 50 samples)
     */
    public void setMovingAverageSize(int size) {
        if (size < 10 || size > 500) {
            throw new IllegalArgumentException("Moving average size must be between 10 and 500");
        }
        this.movingAverageSize = size;
        
        // Reinitialize buffer
        short[] newBuffer = new short[size];
        int copySize = Math.min(size, maBuffer.length);
        System.arraycopy(maBuffer, 0, newBuffer, 0, copySize);
        maBuffer = newBuffer;
        
        // Reset index if needed
        if (maIndex >= size) {
            maIndex = 0;
        }
    }
    
    /**
     * Set peak detection threshold (default: 40)
     * This is the minimum amplitude above baseline to detect a peak
     */
    public void setThreshold(int threshold) {
        if (threshold < 10 || threshold > 1000) {
            throw new IllegalArgumentException("Threshold must be between 10 and 1000");
        }
        this.threshold = threshold;
    }
    
    /**
     * Set minimum interval between beats in seconds (default: 0.4s)
     * This prevents detecting multiple peaks for a single heartbeat
     */
    public void setMinIntervalSeconds(double seconds) {
        if (seconds < 0.1 || seconds > 3.0) {
            throw new IllegalArgumentException("Min interval must be between 0.1 and 3.0 seconds");
        }
        this.minIntervalSeconds = seconds;
    }
    
    /**
     * Set the BPM update listener
     */
    public void setBPMListener(BPMListener listener) {
        this.listener = listener;
    }
    
    /**
     * Set the peak detection listener
     */
    public void setPeakListener(PeakListener listener) {
        this.peakListener = listener;
    }
    
    /**
     * Get the current BPM value
     */
    public int getCurrentBPM() {
        return currentBPM;
    }
    
    /**
     * Reset all detection state
     */
    public void reset() {
        cycleCounter = 0;
        lastPeak = 0;
        prevPeak = 0;
        interval = 0;
        currentBPM = 0;
        maSum = 0;
        maIndex = 0;
        lastBPMUpdate = 0;
        
        for (int i = 0; i < maBuffer.length; i++) {
            maBuffer[i] = 0;
        }
    }
    
    /**
     * Process a batch of audio samples for heartbeat detection
     * @param samples Array of 16-bit signed audio samples
     */
    public void processSamples(short[] samples) {
        if (samples == null || samples.length == 0) {
            return;
        }
        
        long minIntervalSamples = (long) (minIntervalSeconds * sampleRate);
        
        for (short sample : samples) {
            cycleCounter++;
            lastSample = sample;
            
            // -------------------------
            // Moving average computation
            // -------------------------
            maSum = maSum - maBuffer[maIndex] + sample;
            maBuffer[maIndex] = sample;
            maIndex = (maIndex + 1) % movingAverageSize;
            baseline = (short) (maSum / movingAverageSize);
            
            // -------------------------
            // Peak detection
            // -------------------------
            int deviation = sample - baseline;
            currentDeviation = deviation;  // Store for debugging
            
            if (deviation > threshold) {
                long timeSinceLastPeak = cycleCounter - lastPeak;
                
                if (timeSinceLastPeak > minIntervalSamples) {
                    // Valid peak detected
                    prevPeak = lastPeak;
                    lastPeak = cycleCounter;
                    
                    // Notify peak listener
                    if (peakListener != null) {
                        peakListener.onPeakDetected();
                    }
                    
                    if (prevPeak != 0) {
                        // Calculate interval between peaks
                        interval = lastPeak - prevPeak;
                        
                        if (interval > 0) {
                            // Calculate BPM = (60 * sampleRate) / interval
                            long numerator = 60L * sampleRate;
                            int bpm = (int) (numerator / interval);
                            
                            // Clamp to reasonable range (30-250 BPM)
                            if (bpm < 30) {
                                bpm = 30;
                            } else if (bpm > 250) {
                                bpm = 250;
                            }
                            
                            currentBPM = bpm;
                            lastBPMUpdate = cycleCounter;
                            
                            // Notify listener
                            if (listener != null) {
                                listener.onBPMUpdate(currentBPM);
                            }
                        }
                    }
                }
            }
        }
        
        // If no peak detected for a long time, reset BPM to 0
        long timeSinceUpdate = cycleCounter - lastBPMUpdate;
        if (timeSinceUpdate > sampleRate * 5) {  // 5 seconds timeout
            if (currentBPM != 0) {
                currentBPM = 0;
                if (listener != null) {
                    listener.onBPMUpdate(0);
                }
            }
        }
    }
    
    /**
     * Get current baseline value
     */
    public short getBaseline() {
        return baseline;
    }
    
    /**
     * Get current moving average size
     */
    public int getMovingAverageSize() {
        return movingAverageSize;
    }
    
    /**
     * Get current threshold
     */
    public int getThreshold() {
        return threshold;
    }
    
    /**
     * Get current minimum interval in seconds
     */
    public double getMinIntervalSeconds() {
        return minIntervalSeconds;
    }
    
    /**
     * Get current deviation (sample - baseline)
     */
    public int getCurrentDeviation() {
        return currentDeviation;
    }
    
    /**
     * Get last sample value
     */
    public short getLastSample() {
        return lastSample;
    }
    
    /**
     * Get time since last peak in seconds
     */
    public double getTimeSinceLastPeak() {
        if (lastPeak == 0) return 0;
        return (cycleCounter - lastPeak) / (double) sampleRate;
    }
}
