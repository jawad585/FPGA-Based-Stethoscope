package de.kai_morich.simple_bluetooth_le_terminal;

public class DiagnosisRecord {
    private int id;
    private int patientId;
    private String recordingPath;
    private int heartRate;
    private String notes;
    private long recordedAt;
    private int durationSeconds;
    private int sampleRate;
    private String analysisPath;

    public DiagnosisRecord() {
        this.recordedAt = System.currentTimeMillis();
        this.sampleRate = 7000; // default
    }

    public DiagnosisRecord(int id, int patientId, String recordingPath, int heartRate, 
                          String notes, long recordedAt, int durationSeconds, int sampleRate) {
        this.id = id;
        this.patientId = patientId;
        this.recordingPath = recordingPath;
        this.heartRate = heartRate;
        this.notes = notes;
        this.recordedAt = recordedAt;
        this.durationSeconds = durationSeconds;
        this.sampleRate = sampleRate;
    }

    public DiagnosisRecord(int id, int patientId, String recordingPath, int heartRate, 
                          String notes, long recordedAt, int durationSeconds, int sampleRate, String analysisPath) {
        this.id = id;
        this.patientId = patientId;
        this.recordingPath = recordingPath;
        this.heartRate = heartRate;
        this.notes = notes;
        this.recordedAt = recordedAt;
        this.durationSeconds = durationSeconds;
        this.sampleRate = sampleRate;
        this.analysisPath = analysisPath;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPatientId() {
        return patientId;
    }

    public void setPatientId(int patientId) {
        this.patientId = patientId;
    }

    public String getRecordingPath() {
        return recordingPath;
    }

    public void setRecordingPath(String recordingPath) {
        this.recordingPath = recordingPath;
    }

    public int getHeartRate() {
        return heartRate;
    }

    public void setHeartRate(int heartRate) {
        this.heartRate = heartRate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public long getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(long recordedAt) {
        this.recordedAt = recordedAt;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public String getAnalysisPath() {
        return analysisPath;
    }

    public void setAnalysisPath(String analysisPath) {
        this.analysisPath = analysisPath;
    }
}
