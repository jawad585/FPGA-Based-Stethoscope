package de.kai_morich.simple_bluetooth_le_terminal;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class PatientsFragment extends Fragment {

    private RecyclerView recyclerView;
    private PatientsAdapter adapter;
    private PatientDatabaseHelper dbHelper;
    private List<Patient> patients;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        dbHelper = new PatientDatabaseHelper(getActivity());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_patients, container, false);
        
        recyclerView = view.findViewById(R.id.patients_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        
        FloatingActionButton addBtn = view.findViewById(R.id.add_patient_btn);
        addBtn.setOnClickListener(v -> showAddPatientDialog());
        
        loadPatients();
        
        return view;
    }

    private void loadPatients() {
        patients = dbHelper.getAllPatients();
        adapter = new PatientsAdapter(patients, this::onPatientClick, this::onPatientDelete);
        recyclerView.setAdapter(adapter);
    }

    private void onPatientClick(Patient patient) {
        // Navigate to patient options (Diagnose/Records)
        Bundle args = new Bundle();
        args.putInt("patient_id", patient.getId());
        args.putString("patient_name", patient.getName());
        
        PatientOptionsFragment fragment = new PatientOptionsFragment();
        fragment.setArguments(args);
        
        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment, fragment, "patient_options")
                .addToBackStack(null)
                .commit();
    }

    private void onPatientDelete(Patient patient) {
        new AlertDialog.Builder(getActivity())
                .setTitle("Delete Patient")
                .setMessage("Are you sure you want to delete " + patient.getName() + 
                           " and all their records?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    dbHelper.deletePatient(patient.getId());
                    loadPatients();
                    Toast.makeText(getActivity(), "Patient deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddPatientDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_patient, null);
        
        TextInputEditText nameInput = dialogView.findViewById(R.id.dialog_patient_name);
        TextInputEditText ageInput = dialogView.findViewById(R.id.dialog_patient_age);
        RadioGroup genderGroup = dialogView.findViewById(R.id.dialog_patient_gender);
        TextInputEditText notesInput = dialogView.findViewById(R.id.dialog_patient_notes);
        
        builder.setView(dialogView)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String ageStr = ageInput.getText().toString().trim();
                    
                    if (name.isEmpty()) {
                        Toast.makeText(getActivity(), "Please enter patient name", 
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    int age = 0;
                    if (!ageStr.isEmpty()) {
                        try {
                            age = Integer.parseInt(ageStr);
                        } catch (NumberFormatException e) {
                            age = 0;
                        }
                    }
                    
                    String gender = "Male";
                    int selectedId = genderGroup.getCheckedRadioButtonId();
                    if (selectedId == R.id.gender_female) {
                        gender = "Female";
                    } else if (selectedId == R.id.gender_other) {
                        gender = "Other";
                    }
                    
                    String notes = notesInput.getText().toString().trim();
                    
                    Patient patient = new Patient();
                    patient.setName(name);
                    patient.setAge(age);
                    patient.setGender(gender);
                    patient.setNotes(notes);
                    
                    dbHelper.addPatient(patient);
                    loadPatients();
                    Toast.makeText(getActivity(), "Patient added", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    @Override
    public void onDestroy() {
        if (dbHelper != null) {
            dbHelper.close();
        }
        super.onDestroy();
    }
}
