package com.example.puttingmeter;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.UUID;

public class BLEManager {

    private static final String TAG = ">>>>";
    private static final String DEVICE_NAME = "Putting Speed Meter";
    private static final String SERVICE_UUID = "12345678-1234-1234-1234-123456789abc";
    private static final String SPEED_UUID = "87654321-4321-4321-4321-cba987654321";
    private static final String RESET_UUID = "22222222-3333-4444-5555-666666666666";
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic speedCharacteristic;
    private BluetoothGattCharacteristic resetCharacteristic;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED
                    && hasConnectPermission();
        }

        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public void startScan() {
        if (!hasPermissions()) {
            Log.e(TAG, "BLE scan skipped: missing Bluetooth permission");
            return;
        }
        if (!isBluetoothEnabled()) {
            Log.e(TAG, "BLE scan skipped: Bluetooth adapter is unavailable or disabled");
            return;
        }

        BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            Log.e(TAG, "BLE scanner is not available");
            return;
        }

        scanner.startScan(scanCallback);
        Log.d(TAG, "BLE scan started");
    }

    public void stopScan() {
        if (!hasPermissions() || !isBluetoothEnabled()) {
            return;
        }

        BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner != null) {
            scanner.stopScan(scanCallback);
            Log.d(TAG, "BLE scan stopped");
        }
    }

    public void disconnect() {
        if (bluetoothGatt != null && hasConnectPermission()) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
        }
        bluetoothGatt = null;
        speedCharacteristic = null;
        resetCharacteristic = null;
    }

    public boolean isConnected() {
        return bluetoothGatt != null && speedCharacteristic != null;
    }

    public void sendResetCommand() {
        if (!hasConnectPermission()) {
            Log.e(TAG, "Reset command skipped: missing BLUETOOTH_CONNECT permission");
            return;
        }

        if (isConnected() && resetCharacteristic != null) {
            resetCharacteristic.setValue("RESET");
            bluetoothGatt.writeCharacteristic(resetCharacteristic);
            Log.d(TAG, "Reset command sent to reset characteristic");
        }
    }

    public void sendRebootCommand() {
        if (!hasConnectPermission()) {
            Log.e(TAG, "Reboot command skipped: missing BLUETOOTH_CONNECT permission");
            return;
        }

        if (isConnected() && resetCharacteristic != null) {
            resetCharacteristic.setValue("REBOOT");
            bluetoothGatt.writeCharacteristic(resetCharacteristic);
            Log.d(TAG, "Reboot command sent to reset characteristic");
        }
    }

    private boolean hasConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device == null || !hasConnectPermission()) {
                return;
            }

            String deviceName = device.getName();
            if (DEVICE_NAME.equals(deviceName)) {
                Log.d(TAG, "Device found: " + deviceName);
                stopScan();
                connectToDevice(device);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "BLE scan failed: " + errorCode);
            if (listener != null) listener.onError("BLE scan failed: " + errorCode);
        }
    };

    private void connectToDevice(BluetoothDevice device) {
        if (!hasConnectPermission()) {
            Log.e(TAG, "Connection skipped: missing BLUETOOTH_CONNECT permission");
            return;
        }
        bluetoothGatt = device.connectGatt(context, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server.");
                if (hasConnectPermission()) {
                    gatt.discoverServices();
                }
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
            Log.d(TAG, "Characteristic changed: " + characteristic.getUuid() + " | value: " + data);

            if (SPEED_UUID.equals(characteristic.getUuid().toString())) {
                try {
                    if (data.contains("|")) {
                        String[] parts = data.split("\\|");
                        if (parts.length == 2) {
                            float peak = Float.parseFloat(parts[0]);
                            float avg = Float.parseFloat(parts[1]);

                            if (listener != null) {
                                listener.onPeakSpeedReceived(String.valueOf(peak));
                                listener.onAvgSpeedReceived(String.valueOf(avg));
                            }
                        }
                        else {
                            Log.e(TAG, "Invalid speed payload: " + data);
                            if (listener != null) listener.onError("Invalid speed payload");
                        }
                    }
                    else {
                        float speed = Float.parseFloat(data);
                        if (listener != null) {
                            listener.onPeakSpeedReceived(String.valueOf(speed));
                            listener.onAvgSpeedReceived(String.valueOf(speed));
                        }
                        Log.d(TAG, "Single speed value received: " + speed);
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid numeric speed payload: " + data, e);
                    if (listener != null) listener.onError("Invalid numeric speed payload: " + data);
                }
            }
        }
    };

    private void enableNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (characteristic == null || !hasConnectPermission()) {
            return;
        }

        gatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
            Log.d(TAG, "Notification enabled for " + characteristic.getUuid());
        }
    }

    public interface BluetoothConnectionListener {
        void onConnectionStateChanged(boolean connected);
        void onPeakSpeedReceived(String data);
        void onAvgSpeedReceived(String data);
        void onError(String error);
    }
}
