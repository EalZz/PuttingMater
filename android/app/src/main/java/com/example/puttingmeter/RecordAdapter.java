package com.example.puttingmeter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.RecordViewHolder> {

    private final List<MainActivity.PuttingRecord> recordList;
    private String speedUnit = "mm/s";

    public RecordAdapter(List<MainActivity.PuttingRecord> recordList) {
        this.recordList = recordList;
    }

    public void setSpeedUnit(String unit) {
        this.speedUnit = unit;
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
        MainActivity.PuttingRecord record = recordList.get(position);

        // 피크 속도 변환
        float displayPeak = record.getPeakSpeed();
        float displayAvg  = record.getAvgSpeed();
        switch (speedUnit) {
            case "cm/s": displayPeak /= 10f; displayAvg /= 10f; break;
            case "m/s":  displayPeak /= 1000f; displayAvg /= 1000f; break;
        }

        holder.speedText.setText(String.format("피크 %.1f %s", displayPeak, speedUnit));
        holder.avgSpeedText.setText(String.format("평균 %.1f %s", displayAvg, speedUnit));
        
        // 비거리를 10배하여 반올림한 정수로 표시
        int distanceValueInt = (int)Math.round(record.getDistance() * 10);
        holder.distanceText.setText(String.format("→ %d m", distanceValueInt));
        
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
