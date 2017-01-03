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
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
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
    TextView logText;


    private int REQUEST_ENABLE_BT = 1;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt = null;
    private String mDeviceAddress = null;
    private boolean mScanning = false;
    private boolean mBonding = false;
    private boolean mConnecting = false;
    private boolean mDiscovering = false;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics;

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

    public final static String ENABLE_DEVICE_UUID = "ffa1";
    public final static String TIME_UUID = "ffa3";
    public final static String DATA_UUID = "ffa4";
    public final static String MAC_UUID = "ffa6";



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
                DisplayLog("Error: " + e.toString());
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
        logText = (TextView) findViewById(R.id.logText);

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
            if (!connectDeviceButton.getText().equals("CONNECT")) {
                connectDeviceButton.setText("Connecting");
                Connect(connectDeviceButton.getTag().toString());
            }


        }
    };

    private void updateStatus(final boolean connected) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (connected) {
                    connectDeviceButton.setText("Connected");
                } else {
                    connectDeviceButton.setText("Connect");
                }

            }
        });
    }

    private void DisplayLog(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logText.setText(logText.getText() + "\n" + text);
            }
        });
    }

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

        if (enable){
            mBluetoothAdapter.startLeScan(mLeScanResult);
            DisplayLog("Start scan");
        } else {
            mBluetoothAdapter.stopLeScan(mLeScanResult);
            DisplayLog("Stop scan");
        }

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

        BluetoothDevice dev = mBluetoothAdapter.getRemoteDevice(mDeviceAddress);
        DisplayLog("Connected Device. \nAddress: " + dev.getAddress() + "  Name: " + dev.getName());
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

    public boolean Connect(String address) {
        mDeviceAddress = address;
        Log("Connecting to " + mDeviceAddress);
        DisplayLog("Connecting to " + mDeviceAddress);
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
                    DisplayLog("Disconnect from address: " + gatt.getDevice().getAddress());
                    updateStatus(false);
                    mDiscovering = false;
                    break;
                case BluetoothProfile.STATE_CONNECTED:
                    Log("STATE_CONNECTED Address : " + gatt.getDevice().getAddress());
                    DisplayLog("Connected from address: " + gatt.getDevice().getAddress());
                    mDiscovering = true;
                    updateStatus(true);
                    mBluetoothGatt.readRemoteRssi();

                    mBluetoothGatt.discoverServices();
                    break;
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            mDiscovering = false;
            displayGattServices(gatt.getServices());

            Log("Discovered");

            enableDevice();
        }

        private void displayGattServices(List<BluetoothGattService> gattServices) {
            if (gattServices == null) return;

            ArrayList<HashMap<String, String>> gattServiceData =
                    new ArrayList<HashMap<String, String>>();
            ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                    = new ArrayList<ArrayList<HashMap<String, String>>>();
            mGattCharacteristics =
                    new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

            String uuid = null;
            for (BluetoothGattService gattService : gattServices) {
                HashMap<String, String> currentServiceData =
                        new HashMap<String, String>();
                uuid = gattService.getUuid().toString();
                gattServiceData.add(currentServiceData);

                ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                        new ArrayList<HashMap<String, String>>();
                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();
                ArrayList<BluetoothGattCharacteristic> charas =
                        new ArrayList<BluetoothGattCharacteristic>();


//                if (uuid.contains("ffa0")) {
                    Log("Service UUID: " + uuid);
                    // Loops through available Characteristics.
                    for (BluetoothGattCharacteristic gattCharacteristic :
                            gattCharacteristics) {
                        charas.add(gattCharacteristic);
                        HashMap<String, String> currentCharaData =
                                new HashMap<String, String>();
                        uuid = gattCharacteristic.getUuid().toString();

                        gattCharacteristicGroupData.add(currentCharaData);

                        Log("Character UUID: " + uuid);
                    }
//                }

                mGattCharacteristics.add(charas);
                gattCharacteristicData.add(gattCharacteristicGroupData);
            }
        }

        private void enableDevice() {
            for (ArrayList<BluetoothGattCharacteristic> gattCharacteristics : mGattCharacteristics) {

                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    String uuid = gattCharacteristic.getUuid().toString();
                    if (uuid.contains(ENABLE_DEVICE_UUID)) {
                        DisplayLog("Enable Device: Writing 1 to: " + uuid);
                        try {
                            byte[] data = new byte[1];
                            data[0] = 0x01;

                            gattCharacteristic.setValue(data);
                            mBluetoothGatt.writeCharacteristic(gattCharacteristic);

                        } catch (Exception e) {
                            DisplayLog("Error: " + e.toString());
                            e.printStackTrace();
                        }

                    }
                }

            }
        }

        private void getMacID() {
            for (ArrayList<BluetoothGattCharacteristic> gattCharacteristics : mGattCharacteristics) {

                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    String uuid = gattCharacteristic.getUuid().toString();
                    if (uuid.contains(MAC_UUID)) {
                        Log("Read value to: " + uuid);
                        DisplayLog("Getting MAC ID from: " + uuid);
                        mBluetoothGatt.readCharacteristic(gattCharacteristic);

                    }
                }

            }
        }

        private void sendTimestamp(){
            for (ArrayList<BluetoothGattCharacteristic> gattCharacteristics : mGattCharacteristics) {

                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    String uuid = gattCharacteristic.getUuid().toString();
                    if (uuid.contains(TIME_UUID)) {
                        int unixTime = (int)(System.currentTimeMillis() / 1000);
//                        String hex = Integer.toHexString(unixTime);
//                        byte[] byteTime = hexStringToByteArray(hex);
                        Log("Send timestamp to: " + uuid + "  Timestamp: " + unixTime);
                        DisplayLog("Send timestamp to: " + uuid + "  Timestamp: " + unixTime);
                        byte[] timestampBytes = longToBytes(unixTime);
                        Log("Bytes: " + timestampBytes.length);
                        gattCharacteristic.setValue(timestampBytes);
                        mBluetoothGatt.writeCharacteristic(gattCharacteristic);

                    }
                }

            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            UUID uuidServ = characteristic.getService().getUuid();
            UUID uuidChar = characteristic.getUuid();
            byte[] value = characteristic.getValue();
            Log("On Characters Read. Service: " + uuidServ.toString() + "   Character: " + uuidChar.toString() + " Status: " + status);
            try {
                if(uuidChar.toString().contains(MAC_UUID)) {
                    String macId = bytesToHex(value);
                    DisplayLog("Mac ID: " + macId);

                }

            } catch(Exception e ) {
                DisplayLog("Error: " + e.toString());
                e.printStackTrace();
            }

        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            UUID uuidServ = characteristic.getService().getUuid();
            UUID uuidChar = characteristic.getUuid();
            Log("On Characters Changed. Service: " + uuidServ.toString() + "   Character: " + uuidChar.toString());
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            UUID uuidServ = characteristic.getService().getUuid();
            UUID uuidChar = characteristic.getUuid();
            Log("On Characters Write. Service: " + uuidServ.toString() + "   Character: " + uuidChar.toString() + " Status: " + status);

            if(uuidChar.toString().contains(ENABLE_DEVICE_UUID)) {
                sendTimestamp();
            } else if(uuidChar.toString().contains(TIME_UUID)) {
                getMacID();
            }

        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {

        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {

        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log("On read remote RSSI:" + rssi);
            DisplayLog("Read RSSI: " + rssi);
            final int finalRssi = rssi;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    deviceRssiText.setText(String.valueOf(finalRssi));
                }
            });
        }

    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver);
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }


    public byte[] longToBytes(int unixTime) {
        return new byte[]{
                (byte) (unixTime >> 24),
                (byte) (unixTime >> 16),
                (byte) (unixTime >> 8),
                (byte) unixTime

        };
//        return ByteBuffer.allocate(4).putInt(unixTime).array();
    }
/*
    public long bytesToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(bytes);
        buffer.flip();//need flip
        return buffer.getLong();
    }*/
}
