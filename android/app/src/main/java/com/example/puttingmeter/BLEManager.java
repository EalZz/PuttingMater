package com.example.puttingmeter;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.util.UUID;

public class BLEManager {

    private static final String TAG = ">>>>";
    private final Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic speedCharacteristic;
    private BluetoothGattCharacteristic resetCharacteristic;

    private static final String SERVICE_UUID = "12345678-1234-1234-1234-123456789abc";
    private static final String SPEED_UUID   = "87654321-4321-4321-4321-cba987654321";
    private static final String RESET_UUID   = "22222222-3333-4444-5555-666666666666";

    private BluetoothConnectionListener listener;

    public BLEManager(Context context) {
        this.context = context;
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager != null) bluetoothAdapter = manager.getAdapter();
    }

    public void setListener(BluetoothConnectionListener listener) {
        this.listener = listener;
    }

    public boolean hasPermissions() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public void startScan() {
        bluetoothAdapter.startLeScan(scanCallback);
        Log.d(TAG, "BLE 스캔 시작");
    }

    public void stopScan() {
        bluetoothAdapter.stopLeScan(scanCallback);
        Log.d(TAG, "BLE 스캔 중지");
    }

    public void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }

    public boolean isConnected() {
        return bluetoothGatt != null && speedCharacteristic != null;
    }

    public void sendResetCommand() {
        if (isConnected() && speedCharacteristic != null) {
            speedCharacteristic.setValue("RESET");
            bluetoothGatt.writeCharacteristic(speedCharacteristic);
            Log.d(TAG, "화면 초기화 명령 전송");
        }
        if (isConnected() && resetCharacteristic != null) {
            resetCharacteristic.setValue("RESET");
            bluetoothGatt.writeCharacteristic(resetCharacteristic);
            Log.d(TAG, "보드 초기화 명령 전송");
        }
    }

    private final BluetoothAdapter.LeScanCallback scanCallback = (device, rssi, scanRecord) -> {
        if (device != null && "Putting Speed Meter".equals(device.getName())) {
            Log.d(TAG, "발견된 디바이스: " + device.getName());
            stopScan();
            connectToDevice(device);
        }
    };

    private void connectToDevice(BluetoothDevice device) {
        bluetoothGatt = device.connectGatt(context, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server.");
                gatt.discoverServices();
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server.");
                if (listener != null) listener.onConnectionStateChanged(false);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(UUID.fromString(SERVICE_UUID));
                if (service != null) {
                    speedCharacteristic = service.getCharacteristic(UUID.fromString(SPEED_UUID));
                    resetCharacteristic = service.getCharacteristic(UUID.fromString(RESET_UUID));
                    enableNotification(gatt, speedCharacteristic);
                }
                if (listener != null) listener.onConnectionStateChanged(true);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            String data = characteristic.getStringValue(0);
            Log.d(TAG, "Characteristic changed: " + characteristic.getUuid() + " | 값: " + data);

            if (SPEED_UUID.equals(characteristic.getUuid().toString())) {
                try {
                    // 데이터가 "피크|평균" 형태인지 확인
                    if (data.contains("|")) {
                        String[] parts = data.split("\\|");
                        if (parts.length == 2) {
                            float peak = Float.parseFloat(parts[0]);
                            float avg  = Float.parseFloat(parts[1]);

                            if (listener != null) {
                                listener.onPeakSpeedReceived(String.valueOf(peak));
                                listener.onAvgSpeedReceived(String.valueOf(avg));
                            }
                        }
                        else {
                            Log.e(TAG, "데이터 형식 오류: " + data);
                            if (listener != null) listener.onError("데이터 형식 오류");
                        }
                    }
                    else {
                        // 단일 값인 경우 (예: "316")
                        float speed = Float.parseFloat(data);
                        if (listener != null) {
                            // 단일 값은 피크 속도와 평균 속도 모두에 동일하게 적용
                            listener.onPeakSpeedReceived(String.valueOf(speed));
                            listener.onAvgSpeedReceived(String.valueOf(speed));
                        }
                        Log.d(TAG, "단일 속도 값 수신: " + speed);
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "속도 변환 오류: " + data, e);
                    if (listener != null) listener.onError("속도 변환 오류: " + data);
                }
            }
        }
    };

    private void enableNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (characteristic != null) {
            gatt.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
                Log.d(TAG, "Notification enabled for " + characteristic.getUuid());
            }
        }
    }

    public interface BluetoothConnectionListener {
        void onConnectionStateChanged(boolean connected);
        void onPeakSpeedReceived(String data);
        void onAvgSpeedReceived(String data);
        void onError(String error);
    }
}
