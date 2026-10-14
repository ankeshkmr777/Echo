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
import android.os.ParcelUuid;
import android.util.Log;

public interface OnDataReceivedListener {
    void onDataReceived(String peerAddress, String base64Bytes);
}

private OnDataReceivedListener datalistener;

public void setOnDataReceivedListener(OnDataReceivedListener listener) {
    this.datalistener = listener;
}

public class PeripheralManager {
    private static final String TAG = "PeripheralManager";
    // The exact same "secret password" used in CentralManager
    private static final UUID SERVICE_UUID = UUID.fromString("7B3C1A80-9F42-4E17-9A6D-2C5E8B1F0D31");
    // The exact same mailbox UUID used in CentralManager
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("8C4D2B91-0E53-5F28-AB7E-3D6F9C2E1E42");

    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser advertiser;
    private boolean isAdvertising = false;
    private BluetoothGattServer gattServer;

    // 4. The Receipt (Did the hardware actually turn on?)
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

    // The "Receiving Doorbell"
    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "A Central device connected to our server: " + device.getAddress());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Device disconnected: " + device.getAddress());
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);

            // 1. Verify the incoming mail was dropped in the correct mailbox
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                
                // 2. Translate the raw electrical bytes back into JS-friendly text
                String base64Bytes = Base64.encodeToString(value, Base64.DEFAULT);
                Log.d(TAG, "Received encrypted message! Base64 Payload: " + base64Bytes);
                
                // 3. Send a "Delivered" receipt back to the sender so their hardware knows it succeeded
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                }

                if (datalistener != null) {
                    dataListener.onDataReceived(device.getAddress(), base64Bytes);
                }
                // (Next phase: Bridge this base64Bytes string up to the Kotlin 'onRawData' event for Dev 4's UI)
            }
        }
    };

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

        // 1. Start the server and hand it our receiving doorbell
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback);

        // 2. Construct the Mailbox (Characteristic) to allow write access
        BluetoothGattCharacteristic dataPipe = new BluetoothGattCharacteristic(
                CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
        );

        // 3. Construct the Building (Service)
        BluetoothGattService service = new BluetoothGattService(
                SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
        );

        // 4. Put the mailbox in the building, and open the building to the network
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

        // Ensure the server is running so we have a mailbox ready when someone connects
        startServer();

        // 1. The Megaphone (How we shout)
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .build();

        // 2. The Nametag (What we shout)
        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();

        Log.d(TAG, "Starting BLE Advertising...");
        
        // 3. Turn on the hardware
        advertiser.startAdvertising(settings, data, advertiseCallback);
        isAdvertising = true;
    }

    @SuppressLint("MissingPermission")
    public void stopAdvertising() {
        if (isAdvertising && advertiser != null) {
            advertiser.stopAdvertising(advertiseCallback);
            isAdvertising = false;
        }
        // Properly shut down the server to free up memory
        if (gattServer != null) {
            gattServer.close();
            gattServer = null;
        }
    }
}