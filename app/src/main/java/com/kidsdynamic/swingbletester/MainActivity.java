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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.ArrayMap;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    Button findDeviceButton;
    TextView deviceNameText;
    TextView deviceAddressText;
    TextView deviceRssiText;
    TextView logText;
    ScrollView logScroll;
    TextView deviceStatus;
    TextView errorMessage;
    Spinner companySelector;


    private int REQUEST_ENABLE_BT = 1;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt = null;
    private String mDeviceAddress = null;
    private boolean mScanning = false;
    private boolean mBonding = false;
    private boolean mConnecting = false;
    private boolean mDiscovering = false;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics;
    private Handler handler;
    private boolean mConnected;
    private List<String> foundDevice;
    private String deviceAddress;

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
    public final static String TEST_START_UUID = "ffae";
    public final static String TEST_DATA_UUID = "ffaf";


    public ArrayList<DeviceDetail> deviceDetail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log("SDK INT: " + Build.VERSION.SDK_INT);

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
        deviceStatus = (TextView) findViewById(R.id.deviceStatus);
        errorMessage = (TextView) findViewById(R.id.errorMessage);

        handler = new Handler();

        deviceNameText = (TextView) findViewById(R.id.deviceNameText);
        deviceAddressText = (TextView) findViewById(R.id.deviceAddressText);
        deviceRssiText = (TextView) findViewById(R.id.deviceRssiText);
        logText = (TextView) findViewById(R.id.logText);
        logScroll = (ScrollView) findViewById(R.id.logScroll);
        companySelector = (Spinner) findViewById(R.id.companySelector);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.company_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        companySelector.setAdapter(adapter);

        foundDevice = new ArrayList<>();

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


/*        try{
            checkMacID("60:64:05:86:2D:E7");
        }catch(Exception e) {
            e.printStackTrace();
            DisplayLog("Error on request checkMacID");
        }*/

    }



    private Button.OnClickListener OnDeviceButtonClicked = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (!findDeviceButton.getText().toString().equals("Stop testing")) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        findDeviceButton.setText("Stop testing");
                    }
                });

                Scan(true);
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        findDeviceButton.setText("Start testing");
                    }
                });
                cleanData();

                if (mBluetoothGatt != null) {
                    mBluetoothGatt.disconnect();
                }

                Scan(false);
            }


        }
    };

    private void updateStatus(final boolean connected) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (connected) {
                    mConnected = true;
                    deviceStatus.setText("Connected");
                } else {
                    mConnected = false;
                    deviceStatus.setText("Connect");
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


        logScroll.postDelayed(new Runnable() {
            @Override
            public void run() {
                logScroll.fullScroll(ScrollView.FOCUS_DOWN);

            }
        }, 1000);
    }

    private void updateErrorMessage(final String text){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                errorMessage.setText(text);
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

            final String name = device.getName();
            final String address = device.getAddress();


            boolean alreadyExist = false;
            for (String d : foundDevice) {
                if (d.equals(address)) {
                    alreadyExist = true;
                    break;
                }
            }

            if (!alreadyExist) {
                foundDevice.add(address);

                DisplayLog("Found Device Name: " + name + "  Address: " + address);
                if (name == null || address == null)
                    return;

                final int rssii = rssi;

                if (name.contains("SWING")) {
//                    Scan(false);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            deviceRssiText.setText(String.valueOf(rssii));
                            deviceNameText.setText(name);
                            deviceAddressText.setText(address);
                        }
                    });

                    DisplayLog("Found - Device Name: " + name + " Device Address: " + address);


                    deviceAddress = device.getAddress();
                    try{
//                        checkMacID(device.getAddress());
                        Connect(deviceAddress);
                        Scan(false);
                    } catch(Exception e){
                        updateErrorMessage(e.getMessage());
                    }

                }

            }


        }
    };

    public void checkMacID(String macId) throws Exception {
        RequestQueue queue = Volley.newRequestQueue(this);
        String url ="https://childrenlab.com:8110/api/checkMacId?mac_id=" + macId;

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        //Success response
                        Connect(deviceAddress);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                DisplayLog("That didn't work");
                updateErrorMessage("The Mac ID doesn't exist on childrenlab database");
            }
        }){
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String>  params = new HashMap<>();
                params.put("X-AUTH-TOKEN", "50ddcb9f1da9b08c2892ba58b694859e");
                return params;
            }
        };
// Add the request to the RequestQueue.
        queue.add(stringRequest);

    }

    public void uploadResult(String macId, byte[] resultData, boolean success) throws Exception {
        RequestQueue queue = Volley.newRequestQueue(this);
        String url ="https://childrenlab.com:8110/api/final";

        final JSONObject jsonBody = new JSONObject("{\"mac_id\":\"" + macId + "\", \"x_max\":\"" + resultData[0] +
                "\", \"x_min\":\"" + resultData[1] + "\", \"y_max\":\"" + resultData[2] +
                "\", \"y_min\":\"" + resultData[3] + "\" , \"uv_max\":\"" + (resultData[4] + "" + resultData[5]) +
                "\", \"uv_min\":\"" + (resultData[5] + " " + resultData[6]) +
                "\", \"company\":\"" + companySelector.getSelectedItem().toString() +
                "\", \"mac_id\":\"" + macId + "\", \"result\":" + success + "}");
        DisplayLog(jsonBody.toString());
        Log.d("Uploading", jsonBody.toString());
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, jsonBody, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {

                //Success response
                DisplayLog("Upload to backend successfully");
                findDeviceButton.callOnClick();
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
                DisplayLog("upload result error");
                updateErrorMessage("Error on uploading test result");
            }
        }){
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String>  params = new HashMap<>();
                params.put("X-AUTH-TOKEN", "50ddcb9f1da9b08c2892ba58b694859e");
                return params;
            }
        };
// Add the request to the RequestQueue.
        queue.add(request);

    }

    public boolean Scan(boolean enable) {

        if (enable) {
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
                    cleanData();
                    break;
                case BluetoothProfile.STATE_CONNECTED:
                    Log("STATE_CONNECTED Address : " + gatt.getDevice().getAddress());
                    DisplayLog("Connected from address: " + gatt.getDevice().getAddress());
                    mDiscovering = true;
                    updateStatus(true);
                    mBluetoothGatt.discoverServices();
                    break;
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            mDiscovering = false;

            displayGattServices(gatt.getServices());

            Log("Discovered");

            enableTest();
        }

        private void displayGattServices(List<BluetoothGattService> gattServices) {
            if (gattServices == null) return;

            mGattCharacteristics = new ArrayList<>();

            String uuid = null;
            for (BluetoothGattService gattService : gattServices) {
                uuid = gattService.getUuid().toString();

                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();
                ArrayList<BluetoothGattCharacteristic> charas = new ArrayList<>();


                if (uuid.contains("ffa0")) {
                    // Loops through available Characteristics.
                    for (BluetoothGattCharacteristic gattCharacteristic :
                            gattCharacteristics) {

                        charas.add(gattCharacteristic);
                        uuid = gattCharacteristic.getUuid().toString();


                        Log("Character UUID: " + uuid);
                    }
                }

                mGattCharacteristics.add(charas);
            }
        }

        private void enableTest() {
            for (ArrayList<BluetoothGattCharacteristic> gattCharacteristics : mGattCharacteristics) {
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    String uuid = gattCharacteristic.getUuid().toString();
                    if (uuid.contains(TEST_START_UUID)) {
                        DisplayLog("Enable TEST Device: Writing 0x54, 0x33, 0x45 to: " + uuid);
                        try {
                            byte[] data = new byte[3];
                            data[0] = 0x54;
                            data[1] = 0x33;
                            data[2] = 0x45;

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

        private void readTestData() {
            for (ArrayList<BluetoothGattCharacteristic> gattCharacteristics : mGattCharacteristics) {

                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    String uuid = gattCharacteristic.getUuid().toString();
                    if (uuid.contains(TEST_DATA_UUID)) {
                        Log("Read value from: " + uuid);
                        DisplayLog("Getting Test data from: " + uuid);

                        mBluetoothGatt.readCharacteristic(gattCharacteristic);

                    }
                }

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

        private void sendTimestamp() {
            for (ArrayList<BluetoothGattCharacteristic> gattCharacteristics : mGattCharacteristics) {

                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    String uuid = gattCharacteristic.getUuid().toString();
                    if (uuid.contains(TIME_UUID)) {
                        int unixTime = (int) (System.currentTimeMillis() / 1000);
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
                if (uuidChar.toString().contains(MAC_UUID)) {
                    String macId = bytesToHex(value);
                    DisplayLog("Mac ID: " + macId);

                } else if (uuidChar.toString().contains(TEST_DATA_UUID)) {
                    StringBuilder s = new StringBuilder();
                    if (value == null) {
                        DisplayLog("Read Null Value From UUID: " + uuidChar);
                        return;
                    }
                    s.append(" - Bytes Size - : " + value.length + "\n");
                    for (int i = 0; i < value.length; i++) {
//                        DisplayLog(" -- Byte[" + i + "]: " + value[i]);
                        s.append(" -- Byte[" + i + "]: " + value[i] + "\n");
                    }

//                    s.append(" -- Hex: " + bytesToHex(value) + "\n");
                    DisplayLog(s.toString());
                    DisplayLog("--------Success Testing -------");
                    mBluetoothGatt.disconnect();
                    uploadResult(deviceAddress, value, true);

                }

            } catch (Exception e) {
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

            if (uuidChar.toString().contains(ENABLE_DEVICE_UUID)) {
                sendTimestamp();
            } else if (uuidChar.toString().contains(TIME_UUID)) {
                getMacID();
            } else if (uuidChar.toString().contains(TEST_START_UUID)) {
                final Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        readTestData();
                    }
                };
                handler.postDelayed(r, 2000);
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

    private void cleanData() {

        foundDevice.clear();
        deviceAddress = null;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                deviceNameText.setText("");
                deviceAddressText.setText("");
                deviceRssiText.setText("");
                deviceStatus.setText("");
            }
        });

//            Scan(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(mBroadcastReceiver);
        } catch (Exception e) {
            Log("Error on destroy");
        }

    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
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
