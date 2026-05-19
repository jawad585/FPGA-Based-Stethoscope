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

public class RecordsAdapter extends RecyclerView.Adapter<RecordsAdapter.RecordViewHolder> {

    private List<DiagnosisRecord> records;
    private OnRecordClickListener clickListener;
    private OnRecordDeleteListener deleteListener;

    public interface OnRecordClickListener {
        void onRecordClick(DiagnosisRecord record);
    }

    public interface OnRecordDeleteListener {
        void onRecordDelete(DiagnosisRecord record);
    }

    public RecordsAdapter(List<DiagnosisRecord> records, OnRecordClickListener clickListener,
                         OnRecordDeleteListener deleteListener) {
        this.records = records;
        this.clickListener = clickListener;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public RecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.record_list_item, parent, false);
        return new RecordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordViewHolder holder, int position) {
        DiagnosisRecord record = records.get(position);
        holder.bind(record);
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    class RecordViewHolder extends RecyclerView.ViewHolder {
        TextView dateText;
        TextView heartRateText;
        TextView durationText;
        ImageButton deleteBtn;

        public RecordViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.record_date);
            heartRateText = itemView.findViewById(R.id.record_heart_rate);
            durationText = itemView.findViewById(R.id.record_duration);
            deleteBtn = itemView.findViewById(R.id.delete_record_btn);
        }

        public void bind(DiagnosisRecord record) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault());
            String date = sdf.format(new Date(record.getRecordedAt()));
            dateText.setText(date);
            
            heartRateText.setText("Heart Rate: " + record.getHeartRate() + " BPM");
            durationText.setText("Duration: " + record.getDurationSeconds() + " seconds");
            
            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onRecordClick(record);
                }
            });
            
            deleteBtn.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onRecordDelete(record);
                }
            });
        }
    }
}
