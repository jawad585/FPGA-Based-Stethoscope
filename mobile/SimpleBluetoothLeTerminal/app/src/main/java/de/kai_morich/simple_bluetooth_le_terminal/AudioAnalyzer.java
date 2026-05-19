package de.kai_morich.simple_bluetooth_le_terminal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AudioAnalyzer {
    
    public static class AnalysisResult {
        public int sampleRate;
        public float duration;
        public int numBeats;
        public String waveformImagePath;
        public Map<String, String> bandImages;
        public Map<String, String> bandDescriptions;
        public String filteredAudioPath;
        
        public AnalysisResult() {
            bandImages = new HashMap<>();
            bandDescriptions = new HashMap<>();
        }
    }
    
    public static class FrequencyBand {
        String name;
        int lowFreq;
        int highFreq;
        String description;
        
        FrequencyBand(String name, int lowFreq, int highFreq, String description) {
            this.name = name;
            this.lowFreq = lowFreq;
            this.highFreq = highFreq;
            this.description = description;
        }
    }
    
    private static FrequencyBand[] getFrequencyBands() {
        return new FrequencyBand[] {
            new FrequencyBand("S1", 20, 100, "First heart sound — closure of mitral and tricuspid valves (\"lub\")."),
            new FrequencyBand("S2", 50, 200, "Second heart sound — closure of aortic and pulmonary valves (\"dub\")."),
            new FrequencyBand("S3", 10, 50, "Third heart sound — low-frequency sound that may indicate heart failure (pathological)."),
            new FrequencyBand("S4", 10, 40, "Fourth heart sound — may indicate a stiff left ventricle (pathological)."),
            new FrequencyBand("Murmur", 100, 500, "Turbulent blood flow — possible valvular or septal abnormality (higher frequencies).")
        };
    }
    
    public static AnalysisResult analyzeAudio(Context context, String audioFilePath, int recordId) throws IOException {
        File audioFile = new File(audioFilePath);
        if (!audioFile.exists()) {
            throw new IOException("Audio file not found");
        }
        
        // Read raw audio data
        FileInputStream fis = new FileInputStream(audioFile);
        byte[] rawData = new byte[(int) audioFile.length()];
        fis.read(rawData);
        fis.close();
        
        // Convert 8-bit unsigned to float
        float[] audioData = new float[rawData.length];
        for (int i = 0; i < rawData.length; i++) {
            int unsignedValue = rawData[i] & 0xFF;
            audioData[i] = (unsignedValue - 128) / 128.0f;
        }
        
        int sampleRate = 7000; // Default sample rate
        float duration = audioData.length / (float) sampleRate;
        
        // Apply bandpass filter (20-1000 Hz)
        float[] filtered = butterBandpassFilter(audioData, 20, 1000, sampleRate);
        
        // Detect heartbeats
        float[] envelope = extractEnvelope(filtered);
        List<Integer> peaks = detectHeartbeats(envelope, sampleRate);
        int numBeats = peaks.size();
        
        // Create results directory
        File resultsDir = new File(context.getFilesDir(), "analysis_results");
        resultsDir.mkdirs();
        
        // Save filtered audio
        String filteredAudioPath = new File(resultsDir, "filtered_" + recordId + ".raw").getAbsolutePath();
        saveFilteredAudio(filtered, filteredAudioPath);
        
        // Generate waveform image
        String waveformPath = new File(resultsDir, "waveform_" + recordId + ".png").getAbsolutePath();
        generateWaveformImage(audioData, filtered, envelope, peaks, sampleRate, waveformPath);
        
        // Generate FFT and band images
        Map<String, String> bandImages = new HashMap<>();
        Map<String, String> bandDescriptions = new HashMap<>();
        
        double[] fftMagnitude = computeFFT(filtered);
        double[] freqs = new double[fftMagnitude.length];
        for (int i = 0; i < freqs.length; i++) {
            freqs[i] = i * sampleRate / (2.0 * fftMagnitude.length);
        }
        
        // Normalize FFT
        double maxMag = 0;
        for (double mag : fftMagnitude) {
            if (mag > maxMag) maxMag = mag;
        }
        if (maxMag > 0) {
            for (int i = 0; i < fftMagnitude.length; i++) {
                fftMagnitude[i] /= maxMag;
            }
        }
        
        FrequencyBand[] bands = getFrequencyBands();
        for (FrequencyBand band : bands) {
            String imagePath = new File(resultsDir, "fft_" + band.name + "_" + recordId + ".png").getAbsolutePath();
            try {
                generateBandFFTImage(freqs, fftMagnitude, band, imagePath);
                bandImages.put(band.name, imagePath);
                bandDescriptions.put(band.name, band.description);
                android.util.Log.d("AudioAnalyzer", "Generated FFT image: " + imagePath);
            } catch (Exception e) {
                android.util.Log.e("AudioAnalyzer", "Failed to generate FFT image for " + band.name, e);
            }
        }
        
        AnalysisResult result = new AnalysisResult();
        result.sampleRate = sampleRate;
        result.duration = duration;
        result.numBeats = numBeats;
        result.waveformImagePath = waveformPath;
        result.bandImages = bandImages;
        result.bandDescriptions = bandDescriptions;
        result.filteredAudioPath = filteredAudioPath;
        
        return result;
    }
    
    /**
     * Analyze a segment of an audio file
     * @param startTime Start time in seconds (0 = beginning)
     * @param endTime End time in seconds (-1 = until end)
     */
    public static AnalysisResult analyzeAudioSegment(Context context, String audioFilePath, int recordId, 
                                                      double startTime, double endTime) throws IOException {
        File audioFile = new File(audioFilePath);
        if (!audioFile.exists()) {
            throw new IOException("Audio file not found");
        }
        
        // Read raw audio data
        FileInputStream fis = new FileInputStream(audioFile);
        byte[] rawData = new byte[(int) audioFile.length()];
        fis.read(rawData);
        fis.close();
        
        int sampleRate = 7000; // Default sample rate
        
        // Calculate sample indices for the segment
        int startSample = (int)(startTime * sampleRate);
        int endSample = (endTime > 0) ? (int)(endTime * sampleRate) : rawData.length;
        
        // Validate and clamp indices
        startSample = Math.max(0, Math.min(startSample, rawData.length - 1));
        endSample = Math.max(startSample + 1, Math.min(endSample, rawData.length));
        
        // Extract segment
        int segmentLength = endSample - startSample;
        byte[] segmentData = new byte[segmentLength];
        System.arraycopy(rawData, startSample, segmentData, 0, segmentLength);
        
        // Convert 8-bit unsigned to float
        float[] audioData = new float[segmentData.length];
        for (int i = 0; i < segmentData.length; i++) {
            int unsignedValue = segmentData[i] & 0xFF;
            audioData[i] = (unsignedValue - 128) / 128.0f;
        }
        
        float duration = audioData.length / (float) sampleRate;
        
        // Apply bandpass filter (20-1000 Hz)
        float[] filtered = butterBandpassFilter(audioData, 20, 1000, sampleRate);
        
        // Detect heartbeats
        float[] envelope = extractEnvelope(filtered);
        List<Integer> peaks = detectHeartbeats(envelope, sampleRate);
        int numBeats = peaks.size();
        
        // Create results directory
        File resultsDir = new File(context.getFilesDir(), "analysis_results");
        resultsDir.mkdirs();
        
        // Save filtered audio
        String filteredAudioPath = new File(resultsDir, "filtered_" + recordId + "_segment.raw").getAbsolutePath();
        saveFilteredAudio(filtered, filteredAudioPath);
        
        // Generate waveform image
        String waveformPath = new File(resultsDir, "waveform_" + recordId + "_segment.png").getAbsolutePath();
        generateWaveformImage(audioData, filtered, envelope, peaks, sampleRate, waveformPath);
        
        // Generate FFT and band images
        Map<String, String> bandImages = new HashMap<>();
        Map<String, String> bandDescriptions = new HashMap<>();
        
        double[] fftMagnitude = computeFFT(filtered);
        double[] freqs = new double[fftMagnitude.length];
        for (int i = 0; i < freqs.length; i++) {
            freqs[i] = i * sampleRate / (2.0 * fftMagnitude.length);
        }
        
        // Normalize FFT
        double maxMag = 0;
        for (double mag : fftMagnitude) {
            if (mag > maxMag) maxMag = mag;
        }
        if (maxMag > 0) {
            for (int i = 0; i < fftMagnitude.length; i++) {
                fftMagnitude[i] /= maxMag;
            }
        }
        
        FrequencyBand[] bands = getFrequencyBands();
        for (FrequencyBand band : bands) {
            String imagePath = new File(resultsDir, "fft_" + band.name + "_" + recordId + "_segment.png").getAbsolutePath();
            try {
                generateBandFFTImage(freqs, fftMagnitude, band, imagePath);
                bandImages.put(band.name, imagePath);
                bandDescriptions.put(band.name, band.description);
                android.util.Log.d("AudioAnalyzer", "Generated FFT segment image: " + imagePath);
            } catch (Exception e) {
                android.util.Log.e("AudioAnalyzer", "Failed to generate FFT segment image for " + band.name, e);
            }
        }
        
        AnalysisResult result = new AnalysisResult();
        result.sampleRate = sampleRate;
        result.duration = duration;
        result.numBeats = numBeats;
        result.waveformImagePath = waveformPath;
        result.bandImages = bandImages;
        result.bandDescriptions = bandDescriptions;
        result.filteredAudioPath = filteredAudioPath;
        
        return result;
    }
    
    private static float[] butterBandpassFilter(float[] data, int lowcut, int highcut, int fs) {
        // Simple bandpass filter implementation
        // For production, consider using a proper DSP library
        float[] filtered = new float[data.length];
        
        // Apply a simple moving average as a low-pass component
        int windowSize = Math.max(1, fs / highcut);
        for (int i = 0; i < data.length; i++) {
            float sum = 0;
            int count = 0;
            for (int j = Math.max(0, i - windowSize / 2); j < Math.min(data.length, i + windowSize / 2); j++) {
                sum += data[j];
                count++;
            }
            filtered[i] = sum / count;
        }
        
        // High-pass filter: subtract low-frequency component
        int hpWindowSize = Math.max(1, fs / lowcut);
        float[] result = new float[data.length];
        for (int i = 0; i < data.length; i++) {
            float sum = 0;
            int count = 0;
            for (int j = Math.max(0, i - hpWindowSize / 2); j < Math.min(data.length, i + hpWindowSize / 2); j++) {
                sum += filtered[j];
                count++;
            }
            result[i] = filtered[i] - (sum / count);
        }
        
        return result;
    }
    
    private static float[] extractEnvelope(float[] data) {
        float[] envelope = new float[data.length];
        
        // Compute Hilbert transform approximation (envelope detection)
        for (int i = 0; i < data.length; i++) {
            float sum = 0;
            int windowSize = 50; // Envelope smoothing window
            int count = 0;
            for (int j = Math.max(0, i - windowSize); j < Math.min(data.length, i + windowSize); j++) {
                sum += Math.abs(data[j]);
                count++;
            }
            envelope[i] = sum / count;
        }
        
        return envelope;
    }
    
    private static List<Integer> detectHeartbeats(float[] envelope, int sampleRate) {
        List<Integer> peaks = new ArrayList<>();
        
        // Find maximum envelope value
        float maxEnv = 0;
        for (float val : envelope) {
            if (val > maxEnv) maxEnv = val;
        }
        
        float threshold = maxEnv * 0.3f;
        int minDistance = (int) (0.3 * sampleRate); // Minimum 0.3s between beats
        
        for (int i = minDistance; i < envelope.length - minDistance; i++) {
            if (envelope[i] > threshold) {
                // Check if this is a local maximum
                boolean isMax = true;
                for (int j = i - minDistance; j < i + minDistance; j++) {
                    if (j >= 0 && j < envelope.length && envelope[j] > envelope[i]) {
                        isMax = false;
                        break;
                    }
                }
                if (isMax) {
                    peaks.add(i);
                    i += minDistance; // Skip ahead to avoid duplicate detections
                }
            }
        }
        
        return peaks;
    }
    
    private static void saveFilteredAudio(float[] filtered, String outputPath) throws IOException {
        // Convert float back to 8-bit unsigned
        byte[] output = new byte[filtered.length];
        for (int i = 0; i < filtered.length; i++) {
            int value = (int) ((filtered[i] * 127) + 128);
            output[i] = (byte) Math.max(0, Math.min(255, value));
        }
        
        FileOutputStream fos = new FileOutputStream(outputPath);
        fos.write(output);
        fos.close();
    }
    
    private static void generateWaveformImage(float[] original, float[] filtered, float[] envelope, 
                                             List<Integer> peaks, int sampleRate, String outputPath) {
        int width = 1200;
        int height = 600;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        
        // Background
        canvas.drawColor(Color.WHITE);
        
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStrokeWidth(2);
        
        int samples = filtered.length;
        float xScale = (float) width / samples;
        float yScale = height / 4.0f;
        float yOffset = height / 2.0f;
        
        // Draw filtered signal
        paint.setColor(Color.BLUE);
        for (int i = 1; i < samples; i++) {
            float x1 = (i - 1) * xScale;
            float y1 = yOffset - filtered[i - 1] * yScale;
            float x2 = i * xScale;
            float y2 = yOffset - filtered[i] * yScale;
            canvas.drawLine(x1, y1, x2, y2, paint);
        }
        
        // Draw envelope
        paint.setColor(Color.rgb(0, 128, 0));
        for (int i = 1; i < samples; i++) {
            float x1 = (i - 1) * xScale;
            float y1 = yOffset - envelope[i - 1] * yScale;
            float x2 = i * xScale;
            float y2 = yOffset - envelope[i] * yScale;
            canvas.drawLine(x1, y1, x2, y2, paint);
        }
        
        // Draw peak markers
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.FILL);
        for (int peak : peaks) {
            float x = peak * xScale;
            float y = yOffset - filtered[peak] * yScale;
            canvas.drawCircle(x, y, 5, paint);
        }
        
        // Save bitmap
        try {
            FileOutputStream out = new FileOutputStream(outputPath);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static double[] computeFFT(float[] signal) {
        int n = signal.length;
        
        // Simple magnitude spectrum (not a true FFT, but adequate for visualization)
        // For production, consider using a proper FFT library like JTransforms
        int numFreqs = 512;
        double[] magnitude = new double[numFreqs];
        
        for (int k = 0; k < numFreqs; k++) {
            double real = 0;
            double imag = 0;
            
            for (int t = 0; t < n; t++) {
                double angle = 2 * Math.PI * k * t / n;
                real += signal[t] * Math.cos(angle);
                imag += signal[t] * Math.sin(angle);
            }
            
            magnitude[k] = Math.sqrt(real * real + imag * imag);
        }
        
        return magnitude;
    }
    
    private static void generateBandFFTImage(double[] freqs, double[] magnitude, FrequencyBand band, String outputPath) {
        try {
            android.util.Log.d("AudioAnalyzer", "Generating FFT image for " + band.name + " at " + outputPath);
            
            int width = 1000;
            int height = 400;
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            
            canvas.drawColor(Color.WHITE);
            
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setStrokeWidth(2);
            
            int maxPlotFreq = 500;
            float xScale = (float) width / maxPlotFreq;
            float yScale = height * 0.8f;
            float yOffset = height * 0.9f;
            
            // Draw FFT magnitude
            paint.setColor(Color.BLUE);
            paint.setAlpha(150);
            for (int i = 1; i < freqs.length && freqs[i] < maxPlotFreq; i++) {
                float x1 = (float) (freqs[i - 1] * xScale);
                float y1 = yOffset - (float) (magnitude[i - 1] * yScale);
                float x2 = (float) (freqs[i] * xScale);
                float y2 = yOffset - (float) (magnitude[i] * yScale);
                canvas.drawLine(x1, y1, x2, y2, paint);
            }
            
            // Highlight band range
            paint.setColor(Color.rgb(255, 165, 0));
            paint.setAlpha(60);
            paint.setStyle(Paint.Style.FILL);
            float bandStartX = band.lowFreq * xScale;
            float bandEndX = band.highFreq * xScale;
            canvas.drawRect(bandStartX, 0, bandEndX, height, paint);
            
            // Draw text
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.BLACK);
            paint.setAlpha(255);
            paint.setTextSize(24);
            canvas.drawText(band.name + " (" + band.lowFreq + "-" + band.highFreq + " Hz)", 20, 30, paint);
            
            // Save bitmap
            File outputFile = new File(outputPath);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            FileOutputStream out = new FileOutputStream(outputPath);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
            out.close();
            
            android.util.Log.d("AudioAnalyzer", "Successfully saved FFT image: " + outputPath + " (size: " + outputFile.length() + " bytes)");
        } catch (Exception e) {
            android.util.Log.e("AudioAnalyzer", "Error generating FFT image for " + band.name, e);
            throw new RuntimeException("Failed to generate FFT image", e);
        }
    }
}
