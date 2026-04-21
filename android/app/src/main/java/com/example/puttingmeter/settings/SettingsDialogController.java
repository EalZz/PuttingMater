package com.example.puttingmeter.settings;

import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.puttingmeter.PuttingDistanceCalculator;
import com.example.puttingmeter.R;
import com.example.puttingmeter.format.SpeedUnit;

import java.util.Arrays;

public class SettingsDialogController {

    public interface OnSettingsChangeListener {
        void onUnitChanged();
        void onCorrectionStepChanged();
    }

    public void show(Context context, PuttingSettings settings, OnSettingsChangeListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(R.layout.dialog_settings);
        AlertDialog dialog = builder.create();
        dialog.show();

        setupViews(context, dialog, settings, listener);
    }

    private void setupViews(Context context, AlertDialog dialog, PuttingSettings settings, OnSettingsChangeListener listener) {
        SeekBar correctionSeekBar = dialog.findViewById(R.id.greenSpeedSeekBar);
        TextView tvCorrection = dialog.findViewById(R.id.currentGreenSpeedText);
        TextView tvGreenSpeed = dialog.findViewById(R.id.currentGreenSpeedValue);
        TextView currentUnitText = dialog.findViewById(R.id.currentUnitText);
        Spinner unitSpinner = dialog.findViewById(R.id.unitSpinner);
        Button btnSave = dialog.findViewById(R.id.btnSave);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);
        Button btnDecreaseStep = dialog.findViewById(R.id.btnDecreaseStep);
        Button btnIncreaseStep = dialog.findViewById(R.id.btnIncreaseStep);

        if (correctionSeekBar == null || tvCorrection == null || unitSpinner == null) return;

        // 다이얼로그 전용 임시 보정치 (저장 전까지는 실제 설정을 바꾸지 않음)
        final int[] draftCorrectionStep = {settings.getCorrectionStep()};

        // 보정 단계 설정 (1-10)
        correctionSeekBar.setMin(1);
        correctionSeekBar.setMax(10);
        correctionSeekBar.setProgress(draftCorrectionStep[0]);
        tvCorrection.setText(String.valueOf(draftCorrectionStep[0]));
        
        if (tvGreenSpeed != null) {
            tvGreenSpeed.setText(PuttingDistanceCalculator.getGreenSpeedText(draftCorrectionStep[0]));
            tvGreenSpeed.setTextColor(0xFF2E7D32); // 녹색
        }
        
        // 현재 단위 표시
        if (currentUnitText != null) {
            currentUnitText.setText(settings.getSpeedUnit().getDisplayName());
        }

        // SeekBar 리스너
        correctionSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateCorrectionPreview(draftCorrectionStep, progress, tvCorrection, tvGreenSpeed);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 단계 조절 버튼 리스너
        View.OnClickListener stepClick = v -> {
            int delta = (v.getId() == R.id.btnIncreaseStep) ? 1 : -1;
            int next = Math.max(1, Math.min(10, correctionSeekBar.getProgress() + delta));
            if (next != correctionSeekBar.getProgress()) {
                correctionSeekBar.setProgress(next);
                updateCorrectionPreview(draftCorrectionStep, next, tvCorrection, tvGreenSpeed);
            }
        };
        if (btnDecreaseStep != null) btnDecreaseStep.setOnClickListener(stepClick);
        if (btnIncreaseStep != null) btnIncreaseStep.setOnClickListener(stepClick);

        // 단위 스피너 설정
        String[] units = {"mm/s", "cm/s", "m/s"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, units);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unitSpinner.setAdapter(adapter);

        int currentIndex = Arrays.asList(units).indexOf(settings.getSpeedUnit().getDisplayName());
        if (currentIndex >= 0) unitSpinner.setSelection(currentIndex);

        // 저장/취소 버튼
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                // 실제 설정 반영
                settings.setCorrectionStep(draftCorrectionStep[0]);
                String selectedUnitStr = unitSpinner.getSelectedItem().toString();
                settings.setSpeedUnit(SpeedUnit.fromString(selectedUnitStr));
                
                // 리스너 호출을 통해 메인 UI 갱신
                if (listener != null) {
                    listener.onCorrectionStepChanged();
                    listener.onUnitChanged();
                }
                dialog.dismiss();
            });
        }
        if (btnCancel != null) btnCancel.setOnClickListener(v -> dialog.dismiss());
    }

    /**
     * 다이얼로그 내부의 보정 단계 표시만 업데이트 (실제 설정 변경은 하지 않음)
     */
    private void updateCorrectionPreview(int[] draftStep, int step, TextView tvCorrection, TextView tvGreenSpeed) {
        draftStep[0] = step;
        tvCorrection.setText(String.valueOf(step));
        if (tvGreenSpeed != null) {
            tvGreenSpeed.setText(PuttingDistanceCalculator.getGreenSpeedText(step));
        }
    }
}
