package com.mat.mat_ble_led;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.UUID;

public class BleService extends Service {
    private final static String TAG = BleService.class.getSimpleName();

    private BluetoothManager bleManager;
    private BluetoothAdapter bleAdapter;
    private BluetoothDevice device;
    private BluetoothGatt bleGatt;
    private String bleDeviceAdd;

    private final IBinder mBinder = new LocalBinder();
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private int mConnectionState = STATE_DISCONNECTED;
    private boolean passChk = false;
    private boolean passFail = false;

    public final static String ACTION_GATT_CONNECTED =
            "ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "ACTION_DATA_AVAILABLE";
    public final static String ACTION_DATA_WRITE =
            "ACTION_DATA_WRITE";
    public final static String EXTRA_DATA = "EXTRA_DATA";
    public final static int BONDED = 0;

    //본딩 연결 확인
    IntentFilter filter = new IntentFilter();

    private final BroadcastReceiver requestReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            int mType = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR);

            if(device.getBondState() == BluetoothDevice.BOND_BONDING){
                System.out.println("본딩중");
            }else if(device.getBondState() == BluetoothDevice.BOND_BONDED){
                passChk = true;
                System.out.println("본딩완료");
            }else {
                passFail = true;
                System.out.println("본딩안됨");
            }

        }
    };

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            System.out.println("!!ConnectionStatus status : " + status);
            System.out.println("!!ConnectionState newState : " + newState);

            registerReceiver(requestReceiver, filter);

            if (newState == BluetoothProfile.STATE_CONNECTED) {

                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Attempting to start service discovery:" +
                        bleGatt.discoverServices());

            }else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            System.out.println("!!Discovered status : " + status);


            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
//                while(true){
//                    if(passFail){
//                        disconnect();
//                        break;
//                    }
//
//                    if(passChk){
//
//                        break;
//                    }
//                    try {
//                        Thread.sleep(500);
//
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }

            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            /*
            GATT_SUCCESS : 0
            GATT_INSUFFICIENT_AUTHENTICATION : 5
            GATT_INSUFFICIENT_ENCRYPTION : 15
            GATT_CONNECTION_CONGESTED : 143
            */

            System.out.println("!!Read status : " + status);

            if(characteristic.getValue() != null && characteristic.getValue().length != 0){
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS){
                broadcastUpdate(ACTION_DATA_WRITE);
            }
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {

        final Intent intent = new Intent(action);

        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {
            int builder[] = new int[data.length];
            for (int i = 0; i < data.length; i++){
                builder[i] = data[i] & 0xff;
            }
            intent.putExtra(EXTRA_DATA, builder);
        }

        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BleService getService() {
            return BleService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return mBinder; }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (bleManager == null) {
            bleManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (bleManager == null) {
                return false;
            }
        }

        bleAdapter = bleManager.getAdapter();
        if (bleAdapter == null) {
            return false;
        }
        return true;
    }

    public boolean connect(final String address) {
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);

        if (bleAdapter == null || address == null) {
            return false;
        }

        // Previously connected device.  Try to reconnect.
//        if (bleDeviceAdd != null && address.equals(bleDeviceAdd)
//                && bleGatt != null) {
//            if (bleGatt.connect()) {
//                mConnectionState = STATE_CONNECTING;
//                return true;
//            } else {
//                return false;
//            }
//        }

        device = bleAdapter.getRemoteDevice(address);
        if (device == null) {
            return false;
        }

        System.out.println("getBondState : " + device.getBondState());

        bleGatt = device.connectGatt(this, false, mGattCallback);

        bleDeviceAdd = address;
        mConnectionState = STATE_CONNECTING;

        return true;
    }

    public void disconnect() {
        if (bleAdapter == null || bleGatt == null) {
            return;
        }
//        passChk = false;
//        passFail = false;
        bleGatt.disconnect();
    }

    public void close() {
        if (bleGatt == null) {
            return;
        }
        bleGatt.close();
        bleGatt = null;
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic){
        bleGatt.writeCharacteristic(characteristic);
    }

    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (bleAdapter == null || bleGatt == null) {
            return;
        }
        bleGatt.readCharacteristic(characteristic);
    }

    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (bleAdapter == null || bleGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        bleGatt.setCharacteristicNotification(characteristic, enabled);

//        // This is specific to Heart Rate Measurement.
//        if (UUID_MAT_CL420.equals(characteristic.getUuid())) {
//            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
//                    UUID.fromString(CL420UUID.CLIENT_CHARACTERISTIC_CONFIG));
//            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//            bleGatt.writeDescriptor(descriptor);
//        }
    }

    public BluetoothGattService getSupportedGattServices() {
        if (bleGatt == null) return null;
        return bleGatt.getService(UUID.fromString("63f596e4-b583-c624-bfc3-b04225378713"));
    }

}
