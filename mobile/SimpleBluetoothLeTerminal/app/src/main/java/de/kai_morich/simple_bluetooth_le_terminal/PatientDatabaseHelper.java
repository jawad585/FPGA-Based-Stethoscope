package de.kai_morich.simple_bluetooth_le_terminal;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class PatientDatabaseHelper extends SQLiteOpenHelper {
    
    private static final String DATABASE_NAME = "heartbeat_monitor.db";
    private static final int DATABASE_VERSION = 3;
    
    // Patients table
    private static final String TABLE_PATIENTS = "patients";
    private static final String COL_PATIENT_ID = "id";
    private static final String COL_PATIENT_NAME = "name";
    private static final String COL_PATIENT_AGE = "age";
    private static final String COL_PATIENT_GENDER = "gender";
    private static final String COL_PATIENT_NOTES = "notes";
    private static final String COL_PATIENT_CREATED = "created_at";
    
    // Diagnosis records table
    private static final String TABLE_RECORDS = "diagnosis_records";
    private static final String COL_RECORD_ID = "id";
    private static final String COL_RECORD_PATIENT_ID = "patient_id";
    private static final String COL_RECORD_PATH = "recording_path";
    private static final String COL_RECORD_HEART_RATE = "heart_rate";
    private static final String COL_RECORD_NOTES = "notes";
    private static final String COL_RECORD_RECORDED_AT = "recorded_at";
    private static final String COL_RECORD_DURATION = "duration_seconds";
    private static final String COL_RECORD_SAMPLE_RATE = "sample_rate";
    private static final String COL_RECORD_ANALYSIS_PATH = "analysis_path";

    public PatientDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createPatientsTable = "CREATE TABLE " + TABLE_PATIENTS + " (" +
                COL_PATIENT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_PATIENT_NAME + " TEXT NOT NULL, " +
                COL_PATIENT_AGE + " INTEGER, " +
                COL_PATIENT_GENDER + " TEXT, " +
                COL_PATIENT_NOTES + " TEXT, " +
                COL_PATIENT_CREATED + " INTEGER)";
        
        String createRecordsTable = "CREATE TABLE " + TABLE_RECORDS + " (" +
                COL_RECORD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_RECORD_PATIENT_ID + " INTEGER NOT NULL, " +
                COL_RECORD_PATH + " TEXT, " +
                COL_RECORD_HEART_RATE + " INTEGER, " +
                COL_RECORD_NOTES + " TEXT, " +
                COL_RECORD_RECORDED_AT + " INTEGER, " +
                COL_RECORD_DURATION + " INTEGER, " +
                COL_RECORD_SAMPLE_RATE + " INTEGER DEFAULT 7000, " +
                COL_RECORD_ANALYSIS_PATH + " TEXT, " +
                "FOREIGN KEY(" + COL_RECORD_PATIENT_ID + ") REFERENCES " + 
                TABLE_PATIENTS + "(" + COL_PATIENT_ID + ") ON DELETE CASCADE)";
        
        db.execSQL(createPatientsTable);
        db.execSQL(createRecordsTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Add sample_rate column if upgrading from version 1
            db.execSQL("ALTER TABLE " + TABLE_RECORDS + " ADD COLUMN " + 
                    COL_RECORD_SAMPLE_RATE + " INTEGER DEFAULT 7000");
        }
        if (oldVersion < 3) {
            // Add analysis_path column if upgrading from version 2
            db.execSQL("ALTER TABLE " + TABLE_RECORDS + " ADD COLUMN " + 
                    COL_RECORD_ANALYSIS_PATH + " TEXT");
        }
    }

    // Patient operations
    public long addPatient(Patient patient) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PATIENT_NAME, patient.getName());
        values.put(COL_PATIENT_AGE, patient.getAge());
        values.put(COL_PATIENT_GENDER, patient.getGender());
        values.put(COL_PATIENT_NOTES, patient.getNotes());
        values.put(COL_PATIENT_CREATED, patient.getCreatedAt());
        
        long id = db.insert(TABLE_PATIENTS, null, values);
        db.close();
        return id;
    }

    public Patient getPatient(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_PATIENTS, null, COL_PATIENT_ID + "=?",
                new String[]{String.valueOf(id)}, null, null, null);
        
        Patient patient = null;
        if (cursor != null && cursor.moveToFirst()) {
            patient = new Patient(
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_PATIENT_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_PATIENT_NAME)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_PATIENT_AGE)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_PATIENT_GENDER)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_PATIENT_NOTES)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COL_PATIENT_CREATED))
            );
            cursor.close();
        }
        db.close();
        return patient;
    }

    public List<Patient> getAllPatients() {
        List<Patient> patients = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_PATIENTS, null, null, null, null, null, 
                COL_PATIENT_CREATED + " DESC");
        
        if (cursor.moveToFirst()) {
            do {
                Patient patient = new Patient(
                    cursor.getInt(cursor.getColumnIndexOrThrow(COL_PATIENT_ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_PATIENT_NAME)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COL_PATIENT_AGE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_PATIENT_GENDER)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_PATIENT_NOTES)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COL_PATIENT_CREATED))
                );
                patients.add(patient);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return patients;
    }

    public int updatePatient(Patient patient) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PATIENT_NAME, patient.getName());
        values.put(COL_PATIENT_AGE, patient.getAge());
        values.put(COL_PATIENT_GENDER, patient.getGender());
        values.put(COL_PATIENT_NOTES, patient.getNotes());
        
        int rows = db.update(TABLE_PATIENTS, values, COL_PATIENT_ID + "=?",
                new String[]{String.valueOf(patient.getId())});
        db.close();
        return rows;
    }

    public void deletePatient(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_PATIENTS, COL_PATIENT_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
    }

    // Diagnosis record operations
    public long addDiagnosisRecord(DiagnosisRecord record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_RECORD_PATIENT_ID, record.getPatientId());
        values.put(COL_RECORD_PATH, record.getRecordingPath());
        values.put(COL_RECORD_HEART_RATE, record.getHeartRate());
        values.put(COL_RECORD_NOTES, record.getNotes());
        values.put(COL_RECORD_RECORDED_AT, record.getRecordedAt());
        values.put(COL_RECORD_DURATION, record.getDurationSeconds());
        values.put(COL_RECORD_SAMPLE_RATE, record.getSampleRate());
        values.put(COL_RECORD_ANALYSIS_PATH, record.getAnalysisPath());
        
        long id = db.insert(TABLE_RECORDS, null, values);
        db.close();
        return id;
    }

    public List<DiagnosisRecord> getPatientRecords(int patientId) {
        List<DiagnosisRecord> records = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_RECORDS, null, COL_RECORD_PATIENT_ID + "=?",
                new String[]{String.valueOf(patientId)}, null, null, 
                COL_RECORD_RECORDED_AT + " DESC");
        
        if (cursor.moveToFirst()) {
            do {
                int sampleRate = 7000; // default
                int sampleRateIndex = cursor.getColumnIndex(COL_RECORD_SAMPLE_RATE);
                if (sampleRateIndex != -1) {
                    sampleRate = cursor.getInt(sampleRateIndex);
                }
                
                String analysisPath = null;
                int analysisPathIndex = cursor.getColumnIndex(COL_RECORD_ANALYSIS_PATH);
                if (analysisPathIndex != -1) {
                    analysisPath = cursor.getString(analysisPathIndex);
                }
                
                DiagnosisRecord record = new DiagnosisRecord(
                    cursor.getInt(cursor.getColumnIndexOrThrow(COL_RECORD_ID)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COL_RECORD_PATIENT_ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_RECORD_PATH)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COL_RECORD_HEART_RATE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_RECORD_NOTES)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COL_RECORD_RECORDED_AT)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COL_RECORD_DURATION)),
                    sampleRate,
                    analysisPath
                );
                records.add(record);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return records;
    }

    public void deleteDiagnosisRecord(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_RECORDS, COL_RECORD_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void updateDiagnosisRecordAnalysis(int recordId, String analysisPath) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_RECORD_ANALYSIS_PATH, analysisPath);
        db.update(TABLE_RECORDS, values, COL_RECORD_ID + "=?", new String[]{String.valueOf(recordId)});
        db.close();
    }
}
