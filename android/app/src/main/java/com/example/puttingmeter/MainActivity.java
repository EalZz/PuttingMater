package com.example.puttingmeter;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = ">>>>";

    private BLEManager bleManager;
    private TextView connectionText, speedValue, speedUnitText, distanceValue, correctionText;
    private TextView avgSpeedValue, avgSpeedUnitText; // 평균 속도 UI

    private RecyclerView recordsRecyclerView;
    private RecordAdapter recordAdapter;
    private final List<PuttingRecord> recordList = new ArrayList<>();

    private Button btnReset, btnSettings, btnReconnect;
    private SeekBar correctionSeekBar;
    private Spinner unitSpinner;

    private int correctionStep = 5; // 기본값 5 (1-10 범위)
    private String speedUnit = "cm/s";

    private float lastPeakSpeed = 0f; // 기록용
    private float lastAvgSpeed = 0f;  // 기록용

    // 2초 타이머 관련 변수들
    private Handler resetHandler = new Handler(Looper.getMainLooper());
    private Runnable resetRunnable;
    private static final long RESET_DELAY_MS = 2000; // 2초

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_main);

        connectionText = findViewById(R.id.connectionText);
        speedValue = findViewById(R.id.speedValue);
        speedUnitText = findViewById(R.id.speedUnitText);
        distanceValue = findViewById(R.id.distanceValue);
        correctionText = findViewById(R.id.greenSpeedText); // 기존 greenSpeedText를 재사용

        avgSpeedValue = findViewById(R.id.avgSpeedValue);
        avgSpeedUnitText = findViewById(R.id.avgSpeedUnitText);
        avgSpeedUnitText.setText(speedUnit);

        btnReset = findViewById(R.id.btnChart);
        btnSettings = findViewById(R.id.btnSettings);
        btnReconnect = findViewById(R.id.btnReconnect);

        recordsRecyclerView = findViewById(R.id.recordsRecyclerView);
        recordAdapter = new RecordAdapter(recordList);
        recordsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        recordsRecyclerView.setAdapter(recordAdapter);

        // 2초 후 UI 리셋을 위한 Runnable 초기화
        resetRunnable = new Runnable() {
            @Override
            public void run() {
                resetUIValues();
            }
        };

        bleManager = new BLEManager(this);
        bleManager.setListener(new BLEManager.BluetoothConnectionListener() {
            @Override
            public void onConnectionStateChanged(boolean connected) {
                runOnUiThread(() -> {
                    connectionText.setText(connected ? "Arduino 연결됨" : "연결 끊김");
                    View statusDot = findViewById(R.id.statusDot);
                    if (statusDot != null) {
                        statusDot.setBackgroundResource(connected ? R.drawable.status_dot_connected : R.drawable.circle_red);
                    }
                    btnReconnect.setVisibility(connected ? View.GONE : View.VISIBLE);

                    // 연결이 끊어지면 UI 값들 리셋
                    if (!connected) {
                        resetUIValues();
                    }
                });
            }

            @Override
            public void onPeakSpeedReceived(String data) {
                runOnUiThread(() -> handleSpeedData(data, false));
            }

            @Override
            public void onAvgSpeedReceived(String data) {
                runOnUiThread(() -> handleSpeedData(data, true));
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "BLE 오류: " + error);
            }
        });

        btnReset.setOnClickListener(v -> {
            if (bleManager.isConnected()) {
                bleManager.sendResetCommand();
                recordList.clear();
                recordAdapter.notifyDataSetChanged();
                resetUIValues();
            }
            else startBLEScan();
        });

        btnSettings.setOnClickListener(v -> showSettingsDialog());
        btnReconnect.setOnClickListener(v -> startBLEScan());
        btnReconnect.setVisibility(View.GONE);

        checkAndRequestPermissions();

        // 초기 단위 표시
        speedUnitText.setText(speedUnit);
        avgSpeedUnitText.setText(speedUnit);
        recordAdapter.setSpeedUnit(speedUnit);

        // 초기 보정 단계 표시
        updateCorrectionText(correctionStep);
    }

    /**
     * UI 값들을 0으로 리셋
     */
    private void resetUIValues() {
        speedValue.setText("0.0");
        avgSpeedValue.setText("0.0");
        distanceValue.setText("0"); // 정수 형태로 표시
        Log.d(TAG, "2초 동안 입력값이 없어 UI 값들을 0으로 리셋");
    }

    /**
     * 2초 타이머 시작
     */
    private void startResetTimer() {
        // 기존 타이머 제거
        resetHandler.removeCallbacks(resetRunnable);
        // 새로운 타이머 시작
        resetHandler.postDelayed(resetRunnable, RESET_DELAY_MS);
    }

    private void handleSpeedData(String data, boolean isAverage) {
        try {
            float rawSpeed = Float.parseFloat(data); // mm/s
            float displaySpeed = rawSpeed;
            switch (speedUnit) {
                case "cm/s": displaySpeed /= 10f; break;
                case "m/s":  displaySpeed /= 1000f; break;
            }

            if (isAverage) {
                avgSpeedValue.setText(String.format("%.1f", displaySpeed));
                lastAvgSpeed = rawSpeed;

                // 새로운 물리 기반 비거리 계산
                double distance = PuttingDistanceCalculator.calculateDistance(rawSpeed, correctionStep);
                int distanceValueInt = (int)Math.round(distance); // 정수로 반올림 출력
                distanceValue.setText(String.valueOf(distanceValueInt));
                Log.d("비거리", "거리(m): " + distance + ", 표기(정수): " + distanceValueInt);

                // 디버그 로그
                PuttingDistanceCalculator.debugCalculation(rawSpeed, correctionStep);

                // 기록 추가 (평균 기준)
                String now = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                recordList.add(0, new PuttingRecord(now, lastPeakSpeed, lastAvgSpeed, (float) distance));
                if (recordList.size() > 5) recordList.remove(recordList.size() - 1);
                recordAdapter.notifyDataSetChanged();

            }
            else {
                speedValue.setText(String.format("%.1f", displaySpeed));
                lastPeakSpeed = rawSpeed;
            }

            // 데이터 수신 시마다 2초 타이머 재시작
            startResetTimer();

            Log.d(TAG, (isAverage ? "Average" : "Peak") + " Speed: " + rawSpeed);
        }
        catch (NumberFormatException e) {
            Log.e(TAG, "속도 변환 오류", e);
        }
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(R.layout.dialog_settings);
        AlertDialog dialog = builder.create();
        dialog.show();

        correctionSeekBar = dialog.findViewById(R.id.greenSpeedSeekBar); // 기존 seekbar 재사용
        TextView tvCorrection = dialog.findViewById(R.id.currentGreenSpeedText);
        TextView tvGreenSpeed = dialog.findViewById(R.id.currentGreenSpeedValue);
        TextView currentUnitText = dialog.findViewById(R.id.currentUnitText);
        unitSpinner = dialog.findViewById(R.id.unitSpinner);
        Button btnSave = dialog.findViewById(R.id.btnSave);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);
        Button btnDecreaseStep = dialog.findViewById(R.id.btnDecreaseStep);
        Button btnIncreaseStep = dialog.findViewById(R.id.btnIncreaseStep);

        // 보정 단계 설정 (1-10)
        correctionSeekBar.setMin(1);
        correctionSeekBar.setMax(10);
        correctionSeekBar.setProgress(correctionStep);
        tvCorrection.setText(String.valueOf(correctionStep));
        if (tvGreenSpeed != null) {
            tvGreenSpeed.setText(PuttingDistanceCalculator.getGreenSpeedText(correctionStep));
            tvGreenSpeed.setTextColor(0xFF2E7D32); // 녹색
        }
        
        // 현재 단위 표시
        currentUnitText.setText(speedUnit);

        correctionSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    correctionStep = progress;
                    tvCorrection.setText(String.valueOf(correctionStep));
                    updateCorrectionText(correctionStep);
                    if (tvGreenSpeed != null) {
                        tvGreenSpeed.setText(PuttingDistanceCalculator.getGreenSpeedText(correctionStep));
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        View.OnClickListener stepClick = v -> {
            int delta = (v.getId() == R.id.btnIncreaseStep) ? 1 : -1;
            int next = Math.max(1, Math.min(10, correctionSeekBar.getProgress() + delta));
            if (next != correctionSeekBar.getProgress()) {
                correctionSeekBar.setProgress(next);
                correctionStep = next;
                tvCorrection.setText(String.valueOf(correctionStep));
                updateCorrectionText(correctionStep);
                if (tvGreenSpeed != null) {
                    tvGreenSpeed.setText(PuttingDistanceCalculator.getGreenSpeedText(correctionStep));
                }
            }
        };
        if (btnDecreaseStep != null) btnDecreaseStep.setOnClickListener(stepClick);
        if (btnIncreaseStep != null) btnIncreaseStep.setOnClickListener(stepClick);

        String[] units = {"mm/s", "cm/s", "m/s"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, units);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unitSpinner.setAdapter(adapter);

        int currentIndex = Arrays.asList(units).indexOf(speedUnit);
        if (currentIndex >= 0) unitSpinner.setSelection(currentIndex);

        btnSave.setOnClickListener(v -> {
            speedUnit = unitSpinner.getSelectedItem().toString();
            speedUnitText.setText(speedUnit);
            avgSpeedUnitText.setText(speedUnit);
            recordAdapter.setSpeedUnit(speedUnit);

            // 비거리 재계산 (정수 표기)
            if (lastAvgSpeed > 0) {
                double distance = PuttingDistanceCalculator.calculateDistance(lastAvgSpeed, correctionStep);
                distanceValue.setText(String.valueOf((int)Math.round(distance)));
            }

            dialog.dismiss();
        });
        btnCancel.setOnClickListener(v -> dialog.dismiss());
    }

    private void checkAndRequestPermissions() {
        if (!bleManager.hasPermissions()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        }, 1);
            }
            else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
        else startBLEScan();
    }

    private void startBLEScan() {
        Log.d(TAG, "BLE 스캔 시작");
        bleManager.startScan();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean granted = true;
        for (int result : grantResults) if (result != PackageManager.PERMISSION_GRANTED) granted = false;
        if (granted) startBLEScan();
        else Log.e(TAG, "권한이 허용되지 않음");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 타이머 정리
        if (resetHandler != null) {
            resetHandler.removeCallbacks(resetRunnable);
        }
        bleManager.disconnect();
    }

    /**
     * 보정 단계에 따른 텍스트 및 색상 업데이트
     */
    private void updateCorrectionText(int step) {
        String description = PuttingDistanceCalculator.getCorrectionDescription(step);
        int colorRes = PuttingDistanceCalculator.getCorrectionColor(step);

        correctionText.setText(description + " (" + step + ")");
        correctionText.setCompoundDrawablesWithIntrinsicBounds(colorRes, 0, 0, 0);
        correctionText.setCompoundDrawablePadding(8);
        correctionText.setTextColor(0xFF666666);
    }

    // -------------------- PuttingRecord --------------------
    public static class PuttingRecord {
        private final String time;
        private final float peakSpeed;
        private final float avgSpeed;
        private final float distance;

        public PuttingRecord(String time, float peakSpeed, float avgSpeed, float distance) {
            this.time = time;
            this.peakSpeed = peakSpeed;
            this.avgSpeed = avgSpeed;
            this.distance = distance;
        }

        public String getTime() { return time; }
        public float getPeakSpeed() { return peakSpeed; }
        public float getAvgSpeed() { return avgSpeed; }
        public float getDistance() { return distance; }
    }
}