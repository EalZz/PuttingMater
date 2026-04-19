package com.example.puttingmeter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

import com.example.puttingmeter.model.PuttingRecord;
import com.example.puttingmeter.format.SpeedUnit;
import com.example.puttingmeter.format.SpeedFormatter;

public class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.RecordViewHolder> {

    private final List<PuttingRecord> recordList;
    private SpeedUnit speedUnit = SpeedUnit.MM_PER_SEC;

    public RecordAdapter(List<PuttingRecord> recordList) {
        this.recordList = recordList;
    }

    public void setSpeedUnit(String unitStr) {
        this.speedUnit = SpeedUnit.fromString(unitStr);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_record, parent, false);
        return new RecordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordViewHolder holder, int position) {
        PuttingRecord record = recordList.get(position);

        String peakStr = SpeedFormatter.formatValue(record.getPeakSpeed(), speedUnit);
        String avgStr = SpeedFormatter.formatValue(record.getAvgSpeed(), speedUnit);
        String unitName = speedUnit.getDisplayName();

        holder.speedText.setText(String.format("피크 %s %s", peakStr, unitName));
        holder.avgSpeedText.setText(String.format("평균 %s %s", avgStr, unitName));
        
        holder.distanceText.setText(String.format(Locale.getDefault(), "→ %.1f m", record.getDistance()));
        
        holder.timeText.setText(record.getTime());
    }

    @Override
    public int getItemCount() {
        return recordList.size();
    }

    public static class RecordViewHolder extends RecyclerView.ViewHolder {
        TextView speedText, avgSpeedText, distanceText, timeText;

        public RecordViewHolder(@NonNull View itemView) {
            super(itemView);
            speedText = itemView.findViewById(R.id.speedText);
            avgSpeedText = itemView.findViewById(R.id.avgSpeedText);
            distanceText = itemView.findViewById(R.id.distanceText);
            timeText = itemView.findViewById(R.id.timeText);
        }
    }
}
