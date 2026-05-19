package de.kai_morich.simple_bluetooth_le_terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.LinkedList;

public class WaveformView extends View {
    
    private int displayDurationMs = 4500; // Configurable display duration
    private static final int MAX_SAMPLES = 16000; // Max samples to store (for up to ~44kHz)
    
    private LinkedList<Short> samples = new LinkedList<>();
    private Paint wavePaint;
    private Paint gridPaint;
    private Paint textPaint;
    private Paint centerLinePaint;
    private Paint peakPaint;
    private Path wavePath;
    private int sampleRate = 7000; // Default, will be updated
    private int maxSamplesToDisplay;
    private boolean showPeakIndicator = false;
    private int peakIndicatorPosition = 0;
    
    public WaveformView(Context context) {
        super(context);
        init();
    }
    
    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public WaveformView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        wavePaint = new Paint();
        wavePaint.setColor(Color.rgb(0, 150, 255)); // Blue waveform
        wavePaint.setStrokeWidth(2f);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setAntiAlias(true);
        
        gridPaint = new Paint();
        gridPaint.setColor(Color.rgb(200, 200, 200)); // Light gray grid
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setAlpha(100);
        
        centerLinePaint = new Paint();
        centerLinePaint.setColor(Color.rgb(100, 100, 100)); // Darker center line
        centerLinePaint.setStrokeWidth(1.5f);
        centerLinePaint.setStyle(Paint.Style.STROKE);
        
        peakPaint = new Paint();
        peakPaint.setColor(Color.RED);
        peakPaint.setStyle(Paint.Style.FILL);
        peakPaint.setAntiAlias(true);
        
        textPaint = new Paint();
        textPaint.setColor(Color.rgb(80, 80, 80)); // Dark gray text
        textPaint.setTextSize(28f);
        textPaint.setAntiAlias(true);
        
        wavePath = new Path();
        
        updateMaxSamples();
    }
    
    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
        updateMaxSamples();
    }
    
    private void updateMaxSamples() {
        // Calculate how many samples to display based on duration
        maxSamplesToDisplay = (sampleRate * displayDurationMs) / 1000;
        maxSamplesToDisplay = Math.min(maxSamplesToDisplay, MAX_SAMPLES);
    }
    
    public void setDisplayDuration(float seconds) {
        this.displayDurationMs = (int)(seconds * 1000);
        updateMaxSamples();
    }
    
    public float getDisplayDuration() {
        return displayDurationMs / 1000f;
    }
    
    public void addSamples(short[] newSamples) {
        if (newSamples == null || newSamples.length == 0) {
            return;
        }
        
        synchronized (samples) {
            // Add new samples
            for (short sample : newSamples) {
                samples.addLast(sample);
                
                // Remove old samples if we exceed the display window
                if (samples.size() > maxSamplesToDisplay) {
                    samples.removeFirst();
                }
            }
        }
        
        // Trigger redraw on UI thread
        postInvalidate();
    }
    
    public void clear() {
        synchronized (samples) {
            samples.clear();
        }
        showPeakIndicator = false;
        postInvalidate();
    }
    
    public void showPeakDetected() {
        showPeakIndicator = true;
        peakIndicatorPosition = samples.size() - 1;
        postInvalidate();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int width = getWidth();
        int height = getHeight();
        
        if (width == 0 || height == 0) {
            return;
        }
        
        // Draw background
        canvas.drawColor(Color.rgb(250, 250, 250)); // Light background
        
        // Draw grid lines
        int numHorizontalLines = 5;
        for (int i = 0; i <= numHorizontalLines; i++) {
            float y = height * i / (float) numHorizontalLines;
            canvas.drawLine(0, y, width, y, gridPaint);
        }
        
        // Draw center line (0 amplitude)
        float centerY = height / 2f;
        canvas.drawLine(0, centerY, width, centerY, centerLinePaint);
        
        // Draw vertical grid lines (time markers)
        int numVerticalLines = 4; // One line per second approximately
        for (int i = 0; i <= numVerticalLines; i++) {
            float x = width * i / (float) numVerticalLines;
            canvas.drawLine(x, 0, x, height, gridPaint);
        }
        
        // Draw time labels
        float timeRange = displayDurationMs / 1000f; // in seconds
        for (int i = 0; i <= numVerticalLines; i++) {
            float x = width * i / (float) numVerticalLines;
            float time = timeRange * (numVerticalLines - i) / numVerticalLines;
            String label = String.format("%.1fs", time);
            canvas.drawText(label, x + 5, 25, textPaint);
        }
        
        // Draw waveform
        synchronized (samples) {
            if (samples.size() < 2) {
                return; // Need at least 2 samples to draw
            }
            
            wavePath.reset();
            
            int numSamples = samples.size();
            float xStep = width / (float) Math.max(1, numSamples - 1);
            
            // Start path
            Short firstSample = samples.getFirst();
            float firstY = centerY - (firstSample / 32768f) * (height / 2.2f); // Scale to view
            wavePath.moveTo(0, firstY);
            
            // Draw rest of waveform
            int index = 0;
            for (Short sample : samples) {
                float x = index * xStep;
                float y = centerY - (sample / 32768f) * (height / 2.2f); // Scale and center
                
                if (index == 0) {
                    wavePath.moveTo(x, y);
                } else {
                    wavePath.lineTo(x, y);
                }
                index++;
            }
            
            canvas.drawPath(wavePath, wavePaint);
        }
        
        // Draw peak indicator (red dot)
        if (showPeakIndicator && peakIndicatorPosition >= 0 && peakIndicatorPosition < samples.size()) {
            synchronized (samples) {
                int index = 0;
                float xStep = width / (float) Math.max(1, samples.size() - 1);
                for (Short sample : samples) {
                    if (index == peakIndicatorPosition) {
                        float x = index * xStep;
                        float y = centerY - (sample / 32768f) * (height / 2.2f);
                        canvas.drawCircle(x, y, 8f, peakPaint);
                        break;
                    }
                    index++;
                }
            }
            // Auto-hide after drawing
            showPeakIndicator = false;
        }
        
        // Draw sample count info
        String info = String.format("Samples: %d / %d", samples.size(), maxSamplesToDisplay);
        canvas.drawText(info, 10, height - 10, textPaint);
    }
}
