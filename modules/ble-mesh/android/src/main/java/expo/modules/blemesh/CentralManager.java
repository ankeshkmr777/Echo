package expo.modules.blemesh;

import java.util.ArrayList;
import android.util.Base64;
import java.util.List;
import java.util.UUID;

import android.content.Context;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;
import android.util.Log;

public class CentralManager {
    private static final String TAG = "CentralManager";
    // The specific UUID for ChitChatChit from your architecture doc
    private static final UUID SERVICE_UUID = UUID.fromString("7B3C1A80-9F42-4E17-9A6D-2C5E8B1F0D31");
    // The specific "mailbox" inside ChitChatChit for sending messages
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("8C4D2B91-0E53-5F28-AB7E-3D6F9C2E1E42");
    
    private BluetoothGattCharacteristic dataPipe;
    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean isScanning = false;
    private BluetoothGatt bluetoothGatt;

    // This callback fires every time a phone running the app is found
    private final ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            Log.d(TAG, "Found device: " + device.getAddress() + ". Attempting to connect...");
            
            // 1. Stop scanning to save battery and avoid connecting twice
            stopScanning();
            
            // 2. Initiate the handshake! This connects to the Peripheral.
            bluetoothGatt = device.connectGatt(context, false, gattCallback);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Scan failed with error code: " + errorCode);
        }
    };

    // The "Handshake Receipt"
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Successfully connected to " + gatt.getDevice().getAddress());
                
                // 3. The phones are now holding hands. 
                // Next step: Search the Peripheral for the actual data pipes.
                gatt.discoverServices();
                
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from " + gatt.getDevice().getAddress());
                if (bluetoothGatt != null) {
                    bluetoothGatt.close();
                    bluetoothGatt = null;
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // 1. Find the ChitChatChit building
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                
                if (service != null) {
                    // 2. Find the specific mailbox inside
                    dataPipe = service.getCharacteristic(CHARACTERISTIC_UUID);
                    
                    if (dataPipe != null) {
                        Log.d(TAG, "Data pipe found! We are ready to send bytes.");
                        // The connection is fully established!
                    } else {
                        Log.e(TAG, "Service found, but data pipe is missing.");
                    }
                } else {
                    Log.e(TAG, "ChitChatChit service not found on this device.");
                }
            } else {
                Log.w(TAG, "Service discovery failed with status: " + status);
            }
        }
    };

    public CentralManager(Context context) {
        this.context = context;
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            this.bluetoothAdapter = bluetoothManager.getAdapter();
        }
    }

    // @SuppressLint is used here because permission checking is handled in the UI layer (Dev 4)
    @SuppressLint("MissingPermission")
    public void startScanning() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is disabled or not supported");
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) return;

        // Filter so we ONLY see other ChitChatChit phones, not random smartwatches
        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(filter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        Log.d(TAG, "Starting BLE Scan...");
        bluetoothLeScanner.startScan(filters, settings, scanCallback);
        isScanning = true;
    }

    @SuppressLint("MissingPermission")
    public void stopScanning() {
        if (isScanning && bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(scanCallback);
            isScanning = false;
        }
    }
    @SuppressLint("MissingPermission")
    public void writeRawData(String base64Bytes) {
        // 1. Safety Check: Are we holding hands and do we have the mailbox?
        if (bluetoothGatt == null || dataPipe == null) {
            Log.e(TAG, "Cannot send data. Not connected or data pipe missing.");
            return;
        }

        try {
            // 2. Translate the JS text back into raw electrical bytes
            byte[] rawBytes = Base64.decode(base64Bytes, Base64.DEFAULT);
            
            // 3. Load the bytes into the mailbox
            dataPipe.setValue(rawBytes);
            
            // 4. Command the hardware to push the mailbox contents over the air
            boolean success = bluetoothGatt.writeCharacteristic(dataPipe);
            
            if (success) {
                Log.d(TAG, "Successfully pushed bytes to the hardware buffer!");
            } else {
                Log.e(TAG, "Hardware rejected the write command.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to decode and send data: " + e.getMessage());
        }
    }
}