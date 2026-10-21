package expo.modules.blemesh;

import android.util.Base64;
import java.util.UUID;

import android.content.Context;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Base64;
import android.util.Log;

import java.util.UUID;

public class PeripheralManager {
    private static final String TAG = "PeripheralManager";
    // The exact same "secret password" used in CentralManager
    private static final UUID SERVICE_UUID = UUID.fromString("7B3C1A80-9F42-4E17-9A6D-2C5E8B1F0D31");
    // The exact same mailbox UUID used in CentralManager
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("8C4D2B91-0E53-5F28-AB7E-3D6F9C2E1E42");

    private OnDataReceivedListener dataListener;

    public void setOnDataReceivedListener(OnDataReceivedListener listener) {
        this.dataListener = listener;
    }

    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser advertiser;
    private boolean isAdvertising = false;
    private BluetoothGattServer gattServer;

    public interface MeshEventListener {
        void onMessageReceived(String base64Bytes);
        void onDeviceDisconnected(String peerId);
    }

    private MeshEventListener listener;

    public void setListener(MeshEventListener listener) {
        this.listener = listener;
    }

    public PeripheralManager(Context context) {
        this.context = context;
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            this.bluetoothAdapter = bluetoothManager.getAdapter();
        }
    }

    @SuppressLint("MissingPermission")
    public void startServer() {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) return;

        gattServer = bluetoothManager.openGattServer(context, gattServerCallback);

        BluetoothGattCharacteristic dataPipe = new BluetoothGattCharacteristic(
                CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
        );

        BluetoothGattService service = new BluetoothGattService(
                SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
        );

        service.addCharacteristic(dataPipe);
        if (gattServer != null) {
            gattServer.addService(service);
            Log.d(TAG, "GATT Server started. Mailbox is ready to receive bytes.");
        }
    }

    @SuppressLint("MissingPermission")
    public void startAdvertising() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is disabled or not supported");
            return;
        }

        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (advertiser == null) return;

        startServer();

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();

        Log.d(TAG, "Starting BLE Advertising...");

        advertiser.startAdvertising(settings, data, advertiseCallback);
        isAdvertising = true;
    }

    @SuppressLint("MissingPermission")
    public void stopAdvertising() {
        if (isAdvertising && advertiser != null) {
            advertiser.stopAdvertising(advertiseCallback);
            isAdvertising = false;
        }
        if (gattServer != null) {
            gattServer.close();
            gattServer = null;
        }
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.d(TAG, "Advertising started successfully! Other phones can now see us.");
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Log.e(TAG, "Advertising failed with error code: " + errorCode);
        }
    };

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "A Central device connected to our server: " + device.getAddress());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                String address = device.getAddress();
                Log.d(TAG, "Device disconnected: " + address);

                if (listener != null) {
                    listener.onDeviceDisconnected(address);
                }
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);

            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {

                String base64Bytes = Base64.encodeToString(value, Base64.DEFAULT);
                Log.d(TAG, "Received encrypted message! Base64 Payload: " + base64Bytes);

                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                }

                if (listener != null) {
                    listener.onMessageReceived(base64Bytes);
                }
            }
        }
    };
}