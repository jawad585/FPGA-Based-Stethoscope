package de.kai_morich.simple_bluetooth_le_terminal;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

public class PatientOptionsFragment extends Fragment {

    private int patientId;
    private String patientName;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            patientId = getArguments().getInt("patient_id");
            patientName = getArguments().getString("patient_name");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_patient_options, container, false);

        TextView titleText = view.findViewById(R.id.patient_name_title);
        titleText.setText(patientName);

        CardView diagnoseCard = view.findViewById(R.id.diagnose_card);
        CardView recordsCard = view.findViewById(R.id.records_card);

        diagnoseCard.setOnClickListener(v -> openDiagnose());
        recordsCard.setOnClickListener(v -> openRecords());

        return view;
    }

    private void openDiagnose() {
        // Navigate to DevicesFragment to select Bluetooth device
        Bundle args = new Bundle();
        args.putInt("patient_id", patientId);
        args.putString("patient_name", patientName);
        args.putBoolean("from_diagnose", true);

        DevicesFragment fragment = new DevicesFragment();
        fragment.setArguments(args);

        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment, fragment, "devices")
                .addToBackStack(null)
                .commit();
    }

    private void openRecords() {
        Bundle args = new Bundle();
        args.putInt("patient_id", patientId);
        args.putString("patient_name", patientName);

        RecordsFragment fragment = new RecordsFragment();
        fragment.setArguments(args);

        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment, fragment, "records")
                .addToBackStack(null)
                .commit();
    }
}
