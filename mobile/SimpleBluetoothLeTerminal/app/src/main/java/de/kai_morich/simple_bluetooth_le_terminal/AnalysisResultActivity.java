package de.kai_morich.simple_bluetooth_le_terminal;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

public class AnalysisResultActivity extends AppCompatActivity {
    
    private int recordId;
    private String analysisPath;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis_result);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Analysis Results");
        }
        
        recordId = getIntent().getIntExtra("record_id", -1);
        analysisPath = getIntent().getStringExtra("analysis_path");
        
        if (analysisPath == null || analysisPath.isEmpty()) {
            Toast.makeText(this, "No analysis data available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        loadAnalysisResults();
    }
    
    private void loadAnalysisResults() {
        // Extract directory from waveform path
        File waveformFile = new File(analysisPath);
        File resultsDir = waveformFile.getParentFile();
        
        if (resultsDir == null || !resultsDir.exists()) {
            Toast.makeText(this, "Analysis files not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Load waveform image
        ImageView waveformImage = findViewById(R.id.waveform_image);
        if (waveformFile.exists()) {
            waveformImage.setImageBitmap(BitmapFactory.decodeFile(waveformFile.getAbsolutePath()));
        }
        
        // Load band images
        LinearLayout bandContainer = findViewById(R.id.band_images_container);
        bandContainer.removeAllViews();
        
        String[] bands = {"S1", "S2", "S3", "S4", "Murmur"};
        String[] descriptions = {
            "First heart sound — closure of mitral and tricuspid valves (\"lub\").",
            "Second heart sound — closure of aortic and pulmonary valves (\"dub\").",
            "Third heart sound — low-frequency sound that may indicate heart failure (pathological).",
            "Fourth heart sound — may indicate a stiff left ventricle (pathological).",
            "Turbulent blood flow — possible valvular or septal abnormality (higher frequencies)."
        };
        
        for (int i = 0; i < bands.length; i++) {
            String band = bands[i];
            String description = descriptions[i];
            
            // Try both regular and segment filenames
            File bandImageFile = new File(resultsDir, "fft_" + band + "_" + recordId + ".png");
            if (!bandImageFile.exists()) {
                bandImageFile = new File(resultsDir, "fft_" + band + "_" + recordId + "_segment.png");
            }
            
            if (bandImageFile.exists()) {
                // Create section for this band
                LinearLayout bandSection = new LinearLayout(this);
                bandSection.setOrientation(LinearLayout.VERTICAL);
                bandSection.setPadding(0, 32, 0, 32);
                
                // Band title
                TextView bandTitle = new TextView(this);
                bandTitle.setText(band);
                bandTitle.setTextSize(18);
                bandTitle.setTextColor(getResources().getColor(android.R.color.black));
                bandTitle.setPadding(0, 0, 0, 8);
                android.text.SpannableString title = new android.text.SpannableString(band);
                title.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, band.length(), 0);
                bandTitle.setText(title);
                bandSection.addView(bandTitle);
                
                // Band description
                TextView bandDesc = new TextView(this);
                bandDesc.setText(description);
                bandDesc.setTextSize(14);
                bandDesc.setTextColor(getResources().getColor(android.R.color.darker_gray));
                bandDesc.setPadding(0, 0, 0, 16);
                bandSection.addView(bandDesc);
                
                // Band image
                ImageView bandImage = new ImageView(this);
                bandImage.setAdjustViewBounds(true);
                bandImage.setImageBitmap(BitmapFactory.decodeFile(bandImageFile.getAbsolutePath()));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                bandImage.setLayoutParams(params);
                bandSection.addView(bandImage);
                
                bandContainer.addView(bandSection);
            }
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
