package com.kidsdynamic.swingbletester;

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
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MainActivity extends AppCompatActivity {

    Button findDeviceButton;
    Button connectDeviceButton;
    TextView deviceNameText;
    TextView deviceAddressText;
    TextView deviceRssiText;


    private int REQUEST_ENABLE_BT = 1;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt = null;
    private String mDeviceAddress = null;
    private boolean mScanning = false;
    private boolean mBonding = false;
    private boolean mConnecting = false;
    private boolean mDiscovering = false;
    private TaskQueue mTaskQueue = new TaskQueue();

    public final static int BLUETOOTH_PERMISSION = 0x1000;
    public final static int BLUETOOTH_ADMIN_PERMISSION = 0x1001;
    public final static int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    public final static int BLUETOOTH_PRIVILEGED_PERMISSION = 1;

    private void Log(String msg) {
        Log.i("LeControl", msg);
    }

    class DeviceDetail {
        String name;
        String address;
        String macID;
        int rssi;
    }

    public ArrayList<DeviceDetail> deviceDetail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBluetoothAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        while (!mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.enable();
            try {
                Thread.sleep(10);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        getApplicationContext().registerReceiver(mBroadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));

        deviceDetail = new ArrayList<>();

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        findDeviceButton = (Button) findViewById(R.id.findDeviceButton);
        findDeviceButton.setOnClickListener(OnDeviceButtonClicked);

        deviceNameText = (TextView) findViewById(R.id.deviceNameText);
        deviceAddressText = (TextView) findViewById(R.id.deviceAddressText);
        connectDeviceButton = (Button) findViewById(R.id.connectButton);
        connectDeviceButton.setOnClickListener(OnDeviceConnectButtonClicked);
        deviceRssiText = (TextView) findViewById(R.id.deviceRssiText);

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.BLUETOOTH}, BLUETOOTH_PERMISSION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.BLUETOOTH_ADMIN}, BLUETOOTH_ADMIN_PERMISSION);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_PRIVILEGED) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.BLUETOOTH_PRIVILEGED}, BLUETOOTH_PRIVILEGED_PERMISSION);
        }
    }

    private Button.OnClickListener OnDeviceButtonClicked = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Scan(true);
        }
    };

    private Button.OnClickListener OnDeviceConnectButtonClicked = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Connect(connectDeviceButton.getTag().toString());
        }
    };

    @Override
    protected void onResume() {
        super.onResume();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.BLUETOOTH}, BLUETOOTH_PERMISSION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.BLUETOOTH_ADMIN}, BLUETOOTH_ADMIN_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (requestCode) {
            case BLUETOOTH_PERMISSION:
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show();
                }
                break;
            case BLUETOOTH_ADMIN_PERMISSION:
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, "Bluetooth admin permission denied", Toast.LENGTH_SHORT).show();
                }
                break;
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
            final int previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);

            Log("Bond state changed for: " + device.getAddress() + " new state: " + bondState + " previous: " + previousBondState);
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {


                if (mDeviceAddress == null || !mDeviceAddress.equals(device.getAddress())) return;

                switch (device.getBondState()) {
                    case BluetoothDevice.BOND_BONDED:
                        Log("BluetoothDevice.BOND_BONDED");
                        break;
                    case BluetoothDevice.BOND_NONE:
                        Log("BluetoothDevice.BOND_NONE");
                        break;
                    case BluetoothDevice.BOND_BONDING:
                        Log("BluetoothDevice.BOND_BONDING");
                        break;
                }
            }
        }

    };

    private BluetoothAdapter.LeScanCallback mLeScanResult = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {

            String name = device.getName();
            String address = device.getAddress();

            if (name == null || address == null)
                return;

            Scan(false);

            DeviceDetail newDevice = new DeviceDetail();
            newDevice.name = device.getName();
            newDevice.address = device.getAddress();

            deviceDetail.add(newDevice);

            deviceNameText.setText(newDevice.name);
            deviceAddressText.setText(newDevice.address);
            connectDeviceButton.setTag(newDevice.address);

            Log("Device Name: " + name + " Device Address: " + address);

        }
    };

    public boolean Scan(boolean enable) {

        if (enable == mScanning)
            return false;

        if (enable)
            mBluetoothAdapter.startLeScan(mLeScanResult);
        else
            mBluetoothAdapter.stopLeScan(mLeScanResult);

        mScanning = enable;

        return true;
    }

    public synchronized boolean Connect() {
        if (mBluetoothAdapter == null || mDeviceAddress == null) {
            Log("mBluetoothAdapter == null or address == null");
            return false;
        }
        Log("Connect()");

        mBonding = true;
        mTaskQueue.reset();

        BluetoothDevice dev = mBluetoothAdapter.getRemoteDevice(mDeviceAddress);

        if (mBluetoothGatt != null && (mConnecting || mDiscovering)) {
            mBluetoothGatt.disconnect();
            mDiscovering = false;
        }

        mConnecting = true;
        mBonding = false;
        Close();
        mBluetoothGatt = dev.connectGatt(this, false, mGattCallback);

        return true;
    }

    private final BroadcastReceiver mPairingRequestReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log("Pairing request: " + action);
            if (action.equals(BluetoothDevice.ACTION_PAIRING_REQUEST)) {

                final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                int type = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR);

                if (type == BluetoothDevice.PAIRING_VARIANT_PIN) {

                    try{
                        byte[] pin = (byte[]) BluetoothDevice.class.getMethod("convertPinToBytes", String.class).invoke(BluetoothDevice.class, "0000");
                        device.setPin(pin);
//                        abortBroadcast();
                    }catch(Exception e){
                        e.printStackTrace();
                    }

                }
                else{
                    Log("Unexpected pairing type: " + type);
                }
            }
        }
    };

    public boolean Connect(String address) {
        mDeviceAddress = address;
        Log("Connecting to " + mDeviceAddress);
        return Connect();
    }

    private boolean Close() {
        if (mBluetoothGatt == null)
            return false;

        mBluetoothGatt.close();
        mBluetoothGatt = null;

        return true;
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            mConnecting = false;
            switch (newState) {
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log("STATE_DISCONNECTED Address : " + gatt.getDevice().getAddress());
                    mDiscovering = false;
                    break;
                case BluetoothProfile.STATE_CONNECTED:
                    Log("STATE_CONNECTED Address : " + gatt.getDevice().getAddress());
                    mDiscovering = true;
                    boolean rssiStatus = mBluetoothGatt.readRemoteRssi();

                    mBluetoothGatt.discoverServices();
                    break;
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            mDiscovering = false;
            gatt.getD

            Log("Discovered");
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
/*            if (mEventListener != null) {
                UUID uuidServ = characteristic.getService().getUuid();
                UUID uuidChar = characteristic.getUuid();
                byte[] value = characteristic.getValue();
                mEventListener.onCharacteristicRead(uuidServ, uuidChar, value);
            }*/
            mTaskQueue.pop(true);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
/*            if (mEventListener != null) {
                UUID uuidServ = characteristic.getService().getUuid();
                UUID uuidChar = characteristic.getUuid();
                byte[] value = characteristic.getValue();
                mEventListener.onCharacteristicChange(uuidServ, uuidChar, value);
            }*/
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
/*            if (mEventListener != null) {
                UUID uuidServ = characteristic.getService().getUuid();
                UUID uuidChar = characteristic.getUuid();
                mEventListener.onCharacteristicWrite(uuidServ, uuidChar);
            }*/

            mTaskQueue.pop(true);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            mTaskQueue.pop(true);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            mTaskQueue.push(new DeviceAccessQueueItem(descriptor, false));
            mTaskQueue.pop(true);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log("On read remote RSSI:" + rssi);
            deviceRssiText.setText(String.valueOf(rssi));

/*            if (mEventListener != null) {
                mEventListener.onRssiUpdate(rssi);
            }*/
        }

    };

    private class DeviceAccessQueueItem {
        Object mObject;
        boolean mWrite;

        DeviceAccessQueueItem(Object o, boolean write) {
            mObject = o;
            mWrite = write;
        }
    }

    private class TaskQueue {
        boolean mDeviceAccess = false;
        Queue<DeviceAccessQueueItem> mDeviceAccessQueue = new ConcurrentLinkedQueue<>();
        long mDeviceAccessTick = 0;

        void reset() {
            mDeviceAccess = false;
            mDeviceAccessQueue.clear();
            mDeviceAccessTick = 0;
        }

        synchronized void push(DeviceAccessQueueItem item) {
            mDeviceAccessQueue.add(item);
            pop(false);
        }

        synchronized void pop(boolean accessDone) {
            if (accessDone)
                mDeviceAccess = false;

            if (!mDeviceAccessQueue.isEmpty() && !mDeviceAccess) {
                mDeviceAccess = access(mDeviceAccessQueue.poll());
                mDeviceAccessTick = System.currentTimeMillis();
            } else if (!mDeviceAccessQueue.isEmpty()) {
                if (System.currentTimeMillis() - mDeviceAccessTick > 2000) {
                    Log("Blocked! Purge queue!");
                    mDeviceAccess = false;
                    mDeviceAccessQueue.clear();
                }
            }
        }

        synchronized boolean access(DeviceAccessQueueItem item) {
            if (item.mObject instanceof BluetoothGattCharacteristic) {

                if (item.mWrite) {
                    //Log("WRITE : " + ((BluetoothGattCharacteristic)item.mObject).getUuid().toString());
                    mBluetoothGatt.writeCharacteristic((BluetoothGattCharacteristic) item.mObject);
                } else {
                    //Log("READ : " + ((BluetoothGattCharacteristic)item.mObject).getUuid().toString());
                    mBluetoothGatt.readCharacteristic((BluetoothGattCharacteristic) item.mObject);
                }

                return true;
            } else if (item.mObject instanceof BluetoothGattDescriptor) {

                if (item.mWrite) {
                    mBluetoothGatt.writeDescriptor((BluetoothGattDescriptor) item.mObject);
                } else {
                    mBluetoothGatt.readDescriptor((BluetoothGattDescriptor) item.mObject);
                }

                return true;
            } else {
                Log("Invalid item!");
            }

            return false;
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mPairingRequestReceiver);
        unregisterReceiver(mBroadcastReceiver);
    }
}
