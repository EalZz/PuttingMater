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

import com.example.puttingmeter.model.PuttingRecord;
import com.example.puttingmeter.format.SpeedUnit;
import com.example.puttingmeter.format.SpeedFormatter;
import com.example.puttingmeter.session.PuttingSession;
import com.example.puttingmeter.settings.PuttingSettings;

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
    private TextView avgSpeedValue, avgSpeedUnitText;
    private View layoutHome, layoutHistory, layoutSettings;

    private final PuttingSession puttingSession = new PuttingSession();

    private RecyclerView recordsRecyclerView;
    private RecordAdapter recordAdapter;

    private Button btnReset, btnSettings, btnReconnect;
    private SeekBar correctionSeekBar;
    private Spinner unitSpinner;

    private final PuttingSettings puttingSettings = new PuttingSettings();
    private float lastPeakSpeed = 0f;
    private float lastAvgSpeed = 0f;

    // 2초 타이머 관련 변수들
    private Handler resetHandler = new Handler(Looper.getMainLooper());
    private Runnable resetRunnable;
    private static final long RESET_DELAY_MS = 2000;
    private static final long RECONNECT_SCAN_DELAY_MS = 1800;
    private static final String STATE_DISCONNECTED = "Disconnected";
    private static final String STATE_CONNECTING = "Connecting...";
    private static final String STATE_CONNECTED = "Connected to Device";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_main);

        // UI 요소 연결
        connectionText = findViewById(R.id.connectionText);
        speedValue = findViewById(R.id.speedValue);
        speedUnitText = findViewById(R.id.speedUnitText);
        distanceValue = findViewById(R.id.distanceValue);
        correctionText = findViewById(R.id.greenSpeedText);

        avgSpeedValue = findViewById(R.id.avgSpeedValue);
        avgSpeedUnitText = findViewById(R.id.avgSpeedUnitText);
        
        layoutHome = findViewById(R.id.layout_home);
        layoutHistory = findViewById(R.id.layout_history);
        layoutSettings = findViewById(R.id.layout_settings);

        btnReset = findViewById(R.id.btnChart); // 초기화 버튼 (XML ID match)
        btnSettings = findViewById(R.id.btnSettings);
        btnReconnect = findViewById(R.id.btnReconnect);

        // Bottom Navigation 설정
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (layoutHome != null) layoutHome.setVisibility(id == R.id.nav_home ? View.VISIBLE : View.GONE);
                if (layoutHistory != null) layoutHistory.setVisibility(id == R.id.nav_history ? View.VISIBLE : View.GONE);
                if (layoutSettings != null) layoutSettings.setVisibility(id == R.id.nav_settings ? View.VISIBLE : View.GONE);
                return true;
            });
        }

        recordsRecyclerView = findViewById(R.id.recordsRecyclerView);
        recordAdapter = new RecordAdapter(puttingSession.getRecordList());
        if (recordsRecyclerView != null) {
            recordsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            recordsRecyclerView.setAdapter(recordAdapter);
        }

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
                    setConnectionStatus(connected ? STATE_CONNECTED : STATE_DISCONNECTED);
                    View statusDot = findViewById(R.id.statusDot);
                    if (statusDot != null) {
                        statusDot.setBackgroundResource(connected ? R.drawable.status_dot_connected : R.drawable.circle_red);
                    }
                    if (!connected) resetUIValues();
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

        if (btnReset != null) {
            btnReset.setOnClickListener(v -> {
                if (bleManager.isConnected()) {
                    bleManager.sendResetCommand();
                    puttingSession.clear();
                    recordAdapter.notifyDataSetChanged();
                    resetUIValues();
                } else startBLEScanAction();
            });
        }

        if (btnSettings != null) btnSettings.setOnClickListener(v -> showSettingsDialog());
        if (btnReconnect != null) btnReconnect.setOnClickListener(v -> requestBoardRebootAndReconnect());

        checkAndRequestPermissions();

        // 초기 설정
        puttingSettings.setSpeedUnit(SpeedUnit.CM_PER_SEC);
        if (speedUnitText != null) speedUnitText.setText(puttingSettings.getSpeedUnit().getDisplayName());
        if (avgSpeedUnitText != null) avgSpeedUnitText.setText(puttingSettings.getSpeedUnit().getDisplayName());
        if (recordAdapter != null) recordAdapter.setSpeedUnit(puttingSettings.getSpeedUnit().getDisplayName());
        updateCorrectionText(puttingSettings.getCorrectionStep());
    }

    private void startBLEScanAction() {
        setConnectionStatus(STATE_CONNECTING);
        if (bleManager.hasPermissions()) {
            bleManager.startScan();
        } else {
            checkAndRequestPermissions();
        }
    }

    private void requestBoardRebootAndReconnect() {
        if (bleManager == null) return;

        if (bleManager.isConnected()) {
            bleManager.sendRebootCommand();
            bleManager.disconnect();
            resetHandler.postDelayed(this::startBLEScanAction, RECONNECT_SCAN_DELAY_MS);
        } else {
            startBLEScanAction();
        }
    }

    private void setConnectionStatus(String status) {
        if (connectionText != null) {
            connectionText.setText(status);
        }
    }

    private void startResetTimer() {
        resetHandler.removeCallbacks(resetRunnable);
        resetHandler.postDelayed(resetRunnable, RESET_DELAY_MS);
    }

    private void resetUIValues() {
        speedValue.setText("0.0");
        avgSpeedValue.setText("0.0");
        distanceValue.setText("0.0");
        distanceValue.setTextColor(getResources().getColor(R.color.neon_orange)); // 색상 복구
        Log.d(TAG, "데이터 리셋 완료");
    }

    private void handleSpeedData(String data, boolean isAverage) {
        try {
            float rawSpeed = Float.parseFloat(data); // mm/s
            String displaySpeedStr = SpeedFormatter.formatValue(rawSpeed, puttingSettings.getSpeedUnit());

            if (isAverage) {
                avgSpeedValue.setText(displaySpeedStr);
                lastAvgSpeed = rawSpeed;

                double distance = PuttingDistanceCalculator.calculateDistance(
                        lastAvgSpeed, puttingSettings.getCorrectionStep());
                float fDistance = (float) distance;
                distanceValue.setText(String.format("%.1f", fDistance));

                // 최고 기록(Jackpot) 연출
                if (puttingSession.isNewBest(fDistance)) {
                    distanceValue.setTextColor(getResources().getColor(R.color.jackpot_gold));
                    // 햅틱 피드백 추가 가능
                } else {
                    distanceValue.setTextColor(getResources().getColor(R.color.neon_orange));
                }

                // 기록 추가
                String now = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                puttingSession.addRecord(new PuttingRecord(now, lastPeakSpeed, lastAvgSpeed, fDistance));
                recordAdapter.notifyDataSetChanged();
            } else {
                speedValue.setText(displaySpeedStr);
                lastPeakSpeed = rawSpeed;
            }
            startResetTimer();
        } catch (NumberFormatException e) {
            Log.e(TAG, "속도 변환 오류", e);
        }
    }
    // ... 나머지 기존 메서드들 유지 (showSettingsDialog 등) ...


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
        correctionSeekBar.setProgress(puttingSettings.getCorrectionStep());
        tvCorrection.setText(String.valueOf(puttingSettings.getCorrectionStep()));
        if (tvGreenSpeed != null) {
            tvGreenSpeed.setText(PuttingDistanceCalculator.getGreenSpeedText(puttingSettings.getCorrectionStep()));
            tvGreenSpeed.setTextColor(0xFF2E7D32); // 녹색
        }
        
        // 현재 단위 표시
        currentUnitText.setText(puttingSettings.getSpeedUnit().getDisplayName());

        correctionSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    puttingSettings.setCorrectionStep(progress);
                    tvCorrection.setText(String.valueOf(puttingSettings.getCorrectionStep()));
                    updateCorrectionText(puttingSettings.getCorrectionStep());
                    if (tvGreenSpeed != null) {
                        tvGreenSpeed.setText(PuttingDistanceCalculator.getGreenSpeedText(
                                puttingSettings.getCorrectionStep()));
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
                puttingSettings.setCorrectionStep(next);
                tvCorrection.setText(String.valueOf(puttingSettings.getCorrectionStep()));
                updateCorrectionText(puttingSettings.getCorrectionStep());
                if (tvGreenSpeed != null) {
                    tvGreenSpeed.setText(PuttingDistanceCalculator.getGreenSpeedText(
                            puttingSettings.getCorrectionStep()));
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

        int currentIndex = Arrays.asList(units).indexOf(puttingSettings.getSpeedUnit().getDisplayName());
        if (currentIndex >= 0) unitSpinner.setSelection(currentIndex);

        btnSave.setOnClickListener(v -> {
            String selectedUnitStr = unitSpinner.getSelectedItem().toString();
            puttingSettings.setSpeedUnit(SpeedUnit.fromString(selectedUnitStr));
            speedUnitText.setText(puttingSettings.getSpeedUnit().getDisplayName());
            avgSpeedUnitText.setText(puttingSettings.getSpeedUnit().getDisplayName());
            recordAdapter.setSpeedUnit(puttingSettings.getSpeedUnit().getDisplayName());

            // 비거리 재계산 (정수 표기)
            if (lastAvgSpeed > 0) {
                double distance = PuttingDistanceCalculator.calculateDistance(
                        lastAvgSpeed, puttingSettings.getCorrectionStep());
                distanceValue.setText(String.format(Locale.getDefault(), "%.1f", distance));
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Log.d(TAG, "모든 권한 허용됨 - 스캔 시작");
                startBLEScan();
            } else {
                Log.e(TAG, "권한이 거부되어 스캔할 수 없음");
            }
        }
    }

    private void startBLEScan() {
        if (bleManager != null && bleManager.hasPermissions()) {
            Log.d(TAG, "BLE 스캔 시작");
            bleManager.startScan();
        } else {
            Log.e(TAG, "스캔 권한 부족 또는 매니저 미초기화");
        }
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

}
