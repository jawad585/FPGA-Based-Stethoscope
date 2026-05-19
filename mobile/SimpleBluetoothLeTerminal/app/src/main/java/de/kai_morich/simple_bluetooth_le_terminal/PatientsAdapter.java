package de.kai_morich.simple_bluetooth_le_terminal;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PatientsAdapter extends RecyclerView.Adapter<PatientsAdapter.PatientViewHolder> {

    private List<Patient> patients;
    private OnPatientClickListener clickListener;
    private OnPatientDeleteListener deleteListener;

    public interface OnPatientClickListener {
        void onPatientClick(Patient patient);
    }

    public interface OnPatientDeleteListener {
        void onPatientDelete(Patient patient);
    }

    public PatientsAdapter(List<Patient> patients, OnPatientClickListener clickListener, 
                          OnPatientDeleteListener deleteListener) {
        this.patients = patients;
        this.clickListener = clickListener;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public PatientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.patient_list_item, parent, false);
        return new PatientViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PatientViewHolder holder, int position) {
        Patient patient = patients.get(position);
        holder.bind(patient);
    }

    @Override
    public int getItemCount() {
        return patients.size();
    }

    class PatientViewHolder extends RecyclerView.ViewHolder {
        TextView nameText;
        TextView infoText;
        TextView dateText;
        ImageButton deleteBtn;

        public PatientViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.patient_name);
            infoText = itemView.findViewById(R.id.patient_info);
            dateText = itemView.findViewById(R.id.patient_date);
            deleteBtn = itemView.findViewById(R.id.delete_patient_btn);
        }

        public void bind(Patient patient) {
            nameText.setText(patient.getName());
            infoText.setText("Age: " + patient.getAge() + " | Gender: " + patient.getGender());
            
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            String date = sdf.format(new Date(patient.getCreatedAt()));
            dateText.setText("Added: " + date);
            
            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onPatientClick(patient);
                }
            });
            
            deleteBtn.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onPatientDelete(patient);
                }
            });
        }
    }
}
