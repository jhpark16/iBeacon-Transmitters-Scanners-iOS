//
// Bluetooth low energy scanner and transmitter
// This is a simple mobile App performing bluetooth low energy transmission and scanning.
// Two modes of BLE transmissions are supported: Android bluetooth advertising and iBeacon.
// iBeacon is not natively supported by Android. Thus, Android Beacon library is used to
// transmit iBeacon packets. Using the Android Beacon library, it is possible to transmit
// any other packets (e.g. AltBeacon packets).
//
// Author: Jungho Park
// June 1, 2018
//
package com.junghopark.ble_tx_rx;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.BeaconTransmitter;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

/*
* Add Android Beacon library for iBeacon and implements BeaconConsumer for the MainActivity
*    add implementation  'org.altbeacon:android-beacon-library:2+' to build.gradle (Module: app)
*/
@TargetApi(23)
public class BLE_Tx_Rx_Activity extends AppCompatActivity implements BeaconConsumer {

    public static final String TAG = "iBeacons";
    private BeaconManager beaconManager;
    private BeaconTransmitter beaconTransmitter;
    private BluetoothAdapter mBtAdapter;
    private BluetoothLeScanner mLEScanner;
    private static final int REQUEST_ENABLE_BT = 1;
    private Handler mHandler;
    private static final long SCAN_PERIOD = 10000; // Only for 10 seconds
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private Button scan_BLE_btn;
    private Button scan_iBeacon_btn;
    private Button transmit_BLE_btn;
    private Button transmit_iBeacon_btn;
    private TextView bleDeviceTextView;
    private int iScanning = 0, iTransmitting = 0;
    private int lineCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble__tx__rx_);

        // beacon format for iBeacon
        BeaconParser beaconParser = new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24");
        //        .setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));
        beaconManager = BeaconManager.getInstanceForApplication(this);
        // Register this beacon format to receive the packet using beacon library.
        beaconManager.getBeaconParsers().add(beaconParser);
        // Bind the library to this App.
        beaconManager.bind(this);
        // Construct a new beacon transmitter object
        beaconTransmitter = new BeaconTransmitter(getApplicationContext(), beaconParser);

        // Set up a TextView for the scanned BLE device information output
        bleDeviceTextView = (TextView) findViewById(R.id.ibeacon_info);
        // Use the ScrollMovementMethod to show a long page
        bleDeviceTextView.setMovementMethod(new ScrollingMovementMethod());

        // Set up a button for scanning BLE
        scan_BLE_btn = (Button) findViewById(R.id.scan_ble_btn);
        // Set a listener for the button clicking
        scan_BLE_btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                click_BLE_scanning();
            }
        });

        // Set up a button for scanning iBeacon
        scan_iBeacon_btn = (Button) findViewById(R.id.scan_ibeacon_btn);
        // Set a listener for the button clicking
        scan_iBeacon_btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                click_iBeacon_scanning();
            }
        });

        // Set up a button for transmitting BLE signal
        transmit_BLE_btn = (Button) findViewById(R.id.transmit_ble_btn);
        transmit_BLE_btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                click_BLE_transmitting();
            }
        });

        // Set up a button for transmitting iBeacon signal
        transmit_iBeacon_btn = (Button) findViewById(R.id.transmit_ibeacon_btn);
        transmit_iBeacon_btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                click_iBeacon_transmitting();
            }
        });

        mHandler = new Handler();
        // Check whether this device has the bluetooth low energy capability
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE Not Supported",
                    Toast.LENGTH_SHORT).show();
            finish();
        }

        // Setup the bluetooth adapter
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        try{
            mBtAdapter = bluetoothManager.getAdapter();
        }
        catch(NullPointerException ex){
            System.err.println("NullpointException" + ex.getMessage());
        }

        // If the bluetooth is not enabled, request enabling bluetooth
        if (mBtAdapter != null && !mBtAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

        // Make sure we have access coarse location enabled, if not, prompt the user to enable it
        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("This app needs location access");
            builder.setMessage("Please grant location access so this app can detect peripherals.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                }
            });
            builder.show();
        }
    }

    /**
     * Update the scan button text
     *
     * @return void
     */
    private void updateScanButton(int iScan) {
        iScanning = iScan;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (iScanning==1) {
                    scan_BLE_btn.setText("Start Scanning iBeacon");
                    scan_iBeacon_btn.setText("Stop Scanning iBeacon");
                }
                else if (iScanning==2){
                    scan_BLE_btn.setText("Start Scanning iBeacon");
                    scan_iBeacon_btn.setText("Stop Scanning iBeacon");
                }
                else {
                    scan_BLE_btn.setText("Stop Scanning iBeacon");
                    scan_iBeacon_btn.setText("Stop Scanning iBeacon");
                }
            }
        });
    }

    /**
     * implements BeaconConsumer from Android Beacon library
     *
     * @return void
     */
    @Override
    public void onBeaconServiceConnect() {
        final Region region = new Region("myBeacons", Identifier.parse(getString(R.string.ibeacon_uuid)),
                null, null);

        beaconManager.addMonitorNotifier(new MonitorNotifier() {
            @Override
            public void didEnterRegion(Region region) {
                try {
                    Log.d(TAG, "didEnterRegion");
                    beaconManager.startRangingBeaconsInRegion(region);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void didExitRegion(Region region) {
                try {
                    Log.d(TAG, "didExitRegion");
                    beaconManager.stopRangingBeaconsInRegion(region);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void didDetermineStateForRegion(int i, Region region) {
                Log.d(TAG, "didStateRegion");
            }
        });

        beaconManager.addRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                for (Beacon oneBeacon : beacons) {
                    Log.d(TAG, "distance: " + oneBeacon.getDistance() + " id:" + oneBeacon.getId1() + "/" + oneBeacon.getId2() + "/" + oneBeacon.getId3());
                }
            }
        });

        try {
            //beaconManager.startMonitoringBeaconsInRegion(new Region("myMonitoringUniqueId", null, null, null));
            beaconManager.startMonitoringBeaconsInRegion(region);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Process scan button click (toggles the button)
     *
     * @return void
     */
    private void click_BLE_scanning() {
        System.out.println("start BLE scanning");
        //peripheralTextView.setText("");
        if (iScanning == 1) {
            iScanning = 0;
            updateScanButton(iScanning);
            if (mBtAdapter != null && mBtAdapter.isEnabled()) {
                scanLeDevice(iScanning);
            }
        }
        else if (iScanning == 2) {
            iScanning = 1;
            updateScanButton(iScanning);
            if (mBtAdapter != null && mBtAdapter.isEnabled()) {
                scanLeDevice(iScanning);
            }
        } else {
            iScanning = 1;
            updateScanButton(iScanning);
            if (mBtAdapter != null && mBtAdapter.isEnabled()) {
                scanLeDevice(0);
            }
        }
    }

    /**
     * Process scan button click (toggles the button)
     *
     * @return void
     */
    private void click_iBeacon_scanning() {
        System.out.println("start BLE scanning");
        //peripheralTextView.setText("");
        if (iScanning == 1) {
            iScanning = 2;
            updateScanButton(iScanning);
            if (mBtAdapter != null && mBtAdapter.isEnabled()) {
                scanLeDevice(iScanning);
            }
        }
        else if (iScanning == 2) {
            iScanning = 0;
            updateScanButton(iScanning);
            if (mBtAdapter != null && mBtAdapter.isEnabled()) {
                scanLeDevice(iScanning);
            }
        } else {
            iScanning = 2;
            updateScanButton(iScanning);
            if (mBtAdapter != null && mBtAdapter.isEnabled()) {
                scanLeDevice(iScanning);
            }
        }
    }

    /**
     * Update the scan button text
     *
     * @return void
     */
    private void updateTransmitButton(final int iTransmit) {
        iTransmitting = iTransmit;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (iTransmitting==1){
                    transmit_BLE_btn.setText("Stop Transmitting BLE Advertiser packets");
                    transmit_iBeacon_btn.setText("Stop Transmitting BLE Advertiser packets");
                }
                else if (iTransmitting==2){
                    transmit_BLE_btn.setText("Transmit BLE Advertiser packets");
                    transmit_iBeacon_btn.setText("Stop Transmitting BLE Advertiser packets");
                }
                else {
                    transmit_BLE_btn.setText("Transmit BLE Advertiser packets");
                    transmit_iBeacon_btn.setText("Stop Transmitting BLE Advertiser packets");
                }
            }
        });
    }

    /**
     * Process transmit BLE button click (toggles the button)
     *
     * @return void
     */
    private void click_BLE_transmitting() {
        System.out.println("start BLE transmitting");
        bleDeviceTextView.setText("start BLE transmitting");
        if(iTransmitting==1){
            updateTransmitButton(iTransmitting);
            transmit_ble(iTransmitting);
        }
        else if (iTransmitting==2){
            updateTransmitButton(iTransmitting);
            transmit_ble(iTransmitting);
        }
        else {
            updateTransmitButton(iTransmitting);
            transmit_ble(iTransmitting);
        }
    }

    /**
     * Process transmit iBeacon button click (toggles the button)
     *
     * @return void
     */
    private void click_iBeacon_transmitting() {
        System.out.println("start iBeacon transmitting");
        bleDeviceTextView.setText("start iBeacon transmitting");
        if(iTransmitting==1){
            updateTransmitButton(iTransmitting);
            transmit_ble(iTransmitting);
        }
        else if (iTransmitting==2){
            updateTransmitButton(iTransmitting);
            transmit_ble(iTransmitting);
        }
        else {
            updateTransmitButton(iTransmitting);
            transmit_ble(iTransmitting);
        }
    }

    /**
     * Turn on or off Bluetooth low energy device for scanning
     *
     * @return void
     */
    private void transmit_ble(int iTransmit) {
        boolean biBeacon = true; // if not, Android BLE Advertising
        if(biBeacon){
            if(iTransmit == 1){
                Beacon beacon = new Beacon.Builder()
                        .setId1(getString(R.string.ibeacon_uuid)) // UUID for beacon
                        .setId2("1") // Major for beacon
                        .setId3("2") // Minor for beacon
                        .setManufacturer(0x004C) // Radius Networks.0x0118  Change this for other beacon layouts//0x004C for iPhone
                        .setTxPower(-60) // Power in dB
                        //.setDataFields(Arrays.asList(new Long[] {0l})) // Remove this for beacon layouts without d: fields
                        .build();

                beaconTransmitter.startAdvertising(beacon, new AdvertiseCallback() {

                    @Override
                    public void onStartFailure(int errorCode) {
                        Log.e(TAG, "Advertisement start failed with code: "+errorCode);
                    }

                    @Override
                    public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                        Log.i(TAG, "Advertisement start succeeded.");
                    }
                });
            }
            else {
                if(beaconTransmitter != null)
                    beaconTransmitter.stopAdvertising();
            }
        }
        else{
            BluetoothLeAdvertiser bleDevice = mBtAdapter.getBluetoothLeAdvertiser();

            AdvertiseCallback advertCallback = new AdvertiseCallback() {
                @Override
                public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                    super.onStartSuccess(settingsInEffect);
                }

                @Override
                public void onStartFailure(int errorCode) {
                    Log.e("BLE", "Advertising onStartFailure: " + errorCode);
                    updateTransmitButton(0);
                    super.onStartFailure(errorCode);
                }
            };

            if(iTransmit == 2) {
                ParcelUuid uuid = new ParcelUuid(UUID.fromString(getString(R.string.ibeacon_uuid)));
                AdvertiseSettings settings = new AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                        .setConnectable(false)
                        .build();
                AdvertiseData data = new AdvertiseData.Builder()
                        .setIncludeDeviceName(true)
                        .build();
            /*
            AdvertiseData data = new AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addServiceUuid(uuid)
                    .addServiceData(uuid, "Data".getBytes(Charset.forName("UTF-8")))
                    .build();
            */
                bleDevice.startAdvertising(settings, data, advertCallback);
            }
            else
                bleDevice.stopAdvertising(advertCallback);
        }
    }

    /**
     * Turn on or off Bluetooth low energy device for scanning
     *
     * @return void
     */
    private void scanLeDevice(final int iEnable) {
        updateScanButton(iEnable);
        if (iEnable == 1) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT < 21) {
                        mBtAdapter.stopLeScan(mLeScanCallback);
                    } else {
                        mLEScanner.stopScan(mScanCallback);
                        updateScanButton(0);
                    }
                }
            }, SCAN_PERIOD);
            if (Build.VERSION.SDK_INT < 21) {
                mBtAdapter.startLeScan(mLeScanCallback);
            } else {
                mLEScanner.startScan(filters, settings, mScanCallback);
            }
        } else {
            if (Build.VERSION.SDK_INT < 21) {
                mBtAdapter.stopLeScan(mLeScanCallback);
            } else {
                mLEScanner.stopScan(mScanCallback);
            }
        }
    }

    /**
     * When the location permission is requested, the result is handled here.
     *
     * @return
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    System.out.println("coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
            }
        }
    }

    /**
     * Bluetooth low energy callback for SDK <23
     * onScanResult is implemented.
     *
     * @return
     */
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            bleDeviceTextView.append("Device Name: " + result.getDevice().getName() + " rssi: " + result.getRssi() + "\n");

            // auto scroll for text view
            final int scrollAmount = bleDeviceTextView.getLayout().getLineTop(bleDeviceTextView.getLineCount()) - bleDeviceTextView.getHeight();
            // if there is no need to scroll, scrollAmount will be <=0
            if (scrollAmount > 0)
                bleDeviceTextView.scrollTo(0, scrollAmount);
        }
    };

    /**
     * When the App is resumed, check the bluetooth adapter. If it is available and enabled,
     * start scanning. Otherwise, ask to turn on bluetooth.
     *
     * @return
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (mBtAdapter == null || !mBtAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            if (Build.VERSION.SDK_INT >= 21) {
                mLEScanner = mBtAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                filters = new ArrayList<>();
            }
            //scanLeDevice(true);
        }
    }

    /**
     * When the App is paused, stop the scanning
     *
     * @return
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (mBtAdapter != null && mBtAdapter.isEnabled()) {
            scanLeDevice(0);
        }
    }

    /**
     * When the App is stopped, release the beaconManager.
     *
     * @return
     */
    @Override
    protected void onStop() {
        beaconManager.unbind(this);
        super.onStop();
    }

    /**
     * When the App is closed, close Gatt service.
     *
     * @return vpod
     */
    @Override
    protected void onDestroy() {
        if (mGatt != null) {
            mGatt.close();
            mGatt = null;
        }
        super.onDestroy();
    }

    /**
     * When the App is closed, close Gatt service.
     *
     * @return void
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            //If it is not enabled, finish the application.
            if (resultCode == Activity.RESULT_CANCELED) {
                //Bluetooth not enabled.
                finish();
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Bluetooth low energy scan callback function for SDK>=23
     * onScanResult, onBatchScanResults, and onScanFailed are implemented.
     *
     */
    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            bleDeviceTextView.append(String.valueOf(lineCount) + ":"+
                            String.valueOf(callbackType)+
                    ", Device:" + result.getDevice().getAddress() +
                    ", Name:" + result.getDevice().getName() +
                    ", rssi:" + result.getRssi() + "\n");
            lineCount++;
            // auto scroll for text view
            int lheight1 = bleDeviceTextView.getLineHeight();
            int lcount1 = bleDeviceTextView.getLineCount();
            int height1 = bleDeviceTextView.getHeight();
            int scrollAmount = lheight1 * lcount1 - height1;
            //final int scrollAmount =
            //       peripheralTextView.getLayout().getLineTop(peripheralTextView.getLineCount())
            //               - peripheralTextView.getHeight();
            // if there is no need to scroll, scrollAmount will be <=0
            if (scrollAmount > 0)
                bleDeviceTextView.scrollTo(0, scrollAmount);
            Log.i("callbackType", String.valueOf(callbackType));
            Log.i("result", result.toString());
            BluetoothDevice btDevice = result.getDevice();
            connectToDevice(btDevice);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };

    /**
     * Bluetooth low energy scan callback function for SDK<23
     * onLeScan is implemented.
     *
     */
    private final BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.i("onLeScan", device.toString());
                            connectToDevice(device);
                        }
                    });
                }
            };

    /**
     * Connect to the scanned device for Gatt service and setup gattCallback for
     * further information.
     *
     * @return
     */
    private void connectToDevice(BluetoothDevice device) {
        if (mGatt == null) {
            mGatt = device.connectGatt(this, false, gattCallback);
            scanLeDevice(0);// will stop after first device detection
        }
    }

    /**
     * Callback for BLE Gatt services.
     *
     * @return
     */
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i("onConnectionStateChange", "Status: " + status);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i("gattCallback", "STATE_CONNECTED");
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e("gattCallback", "STATE_DISCONNECTED");
                    break;
                default:
                    Log.e("gattCallback", "STATE_OTHER");
            }
            updateScanButton(0);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic
                                                 characteristic, int status) {
            Log.i("onCharacteristicRead", characteristic.toString());
            gatt.disconnect();
            updateScanButton(0);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            List<BluetoothGattService> services = gatt.getServices();
            Log.i("onServicesDiscovered", services.toString());
            gatt.readCharacteristic(services.get(1).getCharacteristics().get(0));
        }

    };
}
