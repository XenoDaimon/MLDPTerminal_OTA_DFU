/*
 * Copyright (C) 2015 Microchip Technology Inc. and its subsidiaries.  You may use this software and any derivatives
 * exclusively with Microchip products.
 *
 * THIS SOFTWARE IS SUPPLIED BY MICROCHIP "AS IS".  NO WARRANTIES, WHETHER EXPRESS, IMPLIED OR STATUTORY, APPLY TO THIS
 * SOFTWARE, INCLUDING ANY IMPLIED WARRANTIES OF NON-INFRINGEMENT, MERCHANTABILITY, AND FITNESS FOR A PARTICULAR
 * PURPOSE, OR ITS INTERACTION WITH MICROCHIP PRODUCTS, COMBINATION WITH ANY OTHER PRODUCTS, OR USE IN ANY APPLICATION.
 *
 * IN NO EVENT WILL MICROCHIP BE LIABLE FOR ANY INDIRECT, SPECIAL, PUNITIVE, INCIDENTAL OR CONSEQUENTIAL LOSS, DAMAGE,
 * COST OR EXPENSE OF ANY KIND WHATSOEVER RELATED TO THE SOFTWARE, HOWEVER CAUSED, EVEN IF MICROCHIP HAS BEEN ADVISED OF
 * THE POSSIBILITY OR THE DAMAGES ARE FORESEEABLE.  TO THE FULLEST EXTENT ALLOWED BY LAW, MICROCHIP'S TOTAL LIABILITY ON
 * ALL CLAIMS IN ANY WAY RELATED TO THIS SOFTWARE WILL NOT EXCEED THE AMOUNT OF FEES, IF ANY, THAT YOU HAVE PAID
 * DIRECTLY TO MICROCHIP FOR THIS SOFTWARE.
 *
 * MICROCHIP PROVIDES THIS SOFTWARE CONDITIONALLY UPON YOUR ACCEPTANCE OF THESE TERMS.
 */

package com.microchip.mldpterminal3;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Activity for scanning and displaying available Bluetooth LE devices
 */
public class MldpBluetoothScanActivity extends ListActivity {
    // ----------------------------------------------------------------------------------------------------------------
    // Callback for MldpBluetoothService service connection and disconnection
    private final ServiceConnection bleServiceConnection = new ServiceConnection() {		        //Create new ServiceConnection interface to handle service connection and disconnection
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {		        //Service MldpBluetoothService has connected
            MldpBluetoothService.LocalBinder binder = (MldpBluetoothService.LocalBinder) service;
            bleService = binder.getService();                                                       //Get a reference to the service
            scanStart();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) { 			                //Service disconnects - should never happen while activity is running
            bleService = null;								                                        //Service has no connection
        }
    };

    // ----------------------------------------------------------------------------------------------------------------
    // BroadcastReceiver handles the scan result event fired by the MldpBluetoothService service
    private final BroadcastReceiver bleServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (MldpBluetoothService.ACTION_BLE_SCAN_RESULT.equals(action)) {			            //Service has sent a scan result
                Log.d(TAG, "Scan scan result received");
                final BleDevice device = new BleDevice(intent.getStringExtra(MldpBluetoothService.INTENT_EXTRA_SERVICE_ADDRESS), intent.getStringExtra(MldpBluetoothService.INTENT_EXTRA_SERVICE_NAME)); //Create new item to hold name and address
//                if (device.getName().contains("Bob")) {
//                }
                bleDeviceListAdapter.addDevice(device);                                             //Add the device to our list adapter that displays a list on the screen
                bleDeviceListAdapter.notifyDataSetChanged();                                        //Refresh the list on the screen
            }
        }
    };

    // ----------------------------------------------------------------------------------------------------------------
    // Device has been selected in the list adapter
    // Return name and address of BLE device to the MldpTerminalActivity that started this activity
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BleDevice device = bleDeviceListAdapter.getDevice(position);		                    //Get the device from the list adapter
        scanStopHandler.removeCallbacks(stopScan);                                                  //Stop the scan timeout handler from calling the runnable to stop the scan
        scanStop();
        final Intent intent = new Intent();			                                                //Create Intent to return information to the MldpTerminalActivity that started this activity
        if (device == null) {                                                                       //Check that valid device was received
            setResult(Activity.RESULT_CANCELED, intent);                                            //Something went wrong so indicate cancelled
        }
        else {
            intent.putExtra(INTENT_EXTRA_SCAN_AUTO_CONNECT, alwaysConnectCheckBox.isChecked());          //Add to the Intent whether to automatically connect next time
            intent.putExtra(INTENT_EXTRA_SCAN_NAME, device.getName());	                                //Add BLE device name to the intent
            intent.putExtra(INTENT_EXTRA_SCAN_ADDRESS, device.getAddress());                             //Add BLE device address to the intent
            setResult(Activity.RESULT_OK, intent);                                                  //Return an intent to the calling activity with the selected BLE name and address
        }
        finish();                                                                                   //Done with this activity
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Adapter for holding devices found through scanning
    private class DeviceListAdapter extends ArrayAdapter<BleDevice> {

        private ArrayList<BleDevice> bleDevices;                                                    //An ArrayList to hold the devices in the list

        private int layoutResourceId;
        private Context context;
        //Constructor for the DeviceListAdapter
        public DeviceListAdapter(Context context, int layoutResourceId) {
            super(context, layoutResourceId);
            this.layoutResourceId = layoutResourceId;
            this.context = context;
            bleDevices = new ArrayList<BleDevice>();                                                //Create the list to hold devices
        }

        //Add a new device to the list
        public void addDevice(BleDevice device) {
            if(!bleDevices.contains(device)) {                                                      //See if device is already in the list
                bleDevices.add(device);                                                             //Add the device to the list
            }
        }

        //Get a device from the list based on its position
        public BleDevice getDevice(int position) {
            return bleDevices.get(position);
        }

        //Clear the list of devices
        public void clear() {
            bleDevices.clear();
        }

        @Override
        public int getCount() {
            return bleDevices.size();
        }

        @Override
        public BleDevice getItem(int i) {
            return bleDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        //Called by the Android OS to show each item in the view. View items that scroll off the screen are reused.
        @Override
        public View getView(int position, View convertView, ViewGroup parentView) {
            if (convertView == null) {                                                              //Only inflate a new layout if not recycling a view
                LayoutInflater inflater = ((Activity) context).getLayoutInflater();                 //Get the layout inflater for this activity
                convertView = inflater.inflate(layoutResourceId, parentView, false);                //Inflate a new view containing the device information
            }
            BleDevice device = bleDevices.get(position);                                            //Get device item based on the position
            TextView textViewAddress = (TextView) convertView.findViewById(R.id.device_address);    //Get the TextView for the address
            textViewAddress.setText(device.address);                                                //Set the text to the name of the device
            TextView textViewName = (TextView) convertView.findViewById(R.id.device_name);          //Get the TextView for the name
            textViewName.setText(device.name);                                                      //Set the text to the address of the device
            return convertView;
        }

    }
    // ----------------------------------------------------------------------------------------------------------------
    // Class to hold device name and address
    private class BleDevice {

        private String address;                                                                     //Instance variables for address and name of a BLE device
        private String name;
        //Constructor for a new BleDevice object
        public BleDevice(String a, String n) {
            address = a;
            name = n;
        }

        public String getAddress() {
            return address;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object object) {                                                      //equals function required to execute if(!bleDevices.contains(device)) above
            if (object != null && object instanceof BleDevice) {                                    //Check that the object is valis
                if (this.address.equals(((BleDevice) object).address)) {                            //Check that address strings are the same
                    return true;                                                                    //Then the BleDevice objects are the same
                }
            }
            return false;                                                                           //Not teh same so return false
        }

        @Override
        public int hashCode() {                                                                     //hashCode required for cleanup if equals is implemented
            return this.address.hashCode();
        }

    }

    // ----------------------------------------------------------------------------------------------------------------
    // Starts a scan
    private void scanStart() {
        if (areScanning == false) {                                                                 //See if already scanning - possible if resuming after turning on Bluetooth
            if (bleService.isBluetoothRadioEnabled()) {                                             //See if the Bluetooth radio is on - may have been turned off
                bleDeviceListAdapter.clear();                                                       //Clear list of BLE devices found
                areScanning = true;                                                                 //Indicate that we are scanning - used for menu context and to avoid starting scan twice
                setProgressBarIndeterminateVisibility(true);                                        //Show circular progress bar
                invalidateOptionsMenu();                                                            //The options menu needs to be refreshed
                bleService.scanStart();                                                             //Start scanning
                scanStopHandler.postDelayed(stopScan, SCAN_TIME);                                   //Create delayed runnable that will stop the scan when it runs after SCAN_TIME milliseconds
            } else {                                                                                //Radio needs to be enabled
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);         //Create an intent asking the user to grant permission to enable Bluetooth
                startActivityForResult(enableBtIntent, REQ_CODE_ENABLE_BT);                         //Fire the intent to start the activity that will return a result based on user input
                Log.d(TAG, "Requesting user to enable Bluetooth radio");                            //Send debug message
            }
        }
    }


    // ----------------------------------------------------------------------------------------------------------------
    // Runnable used by the scanStopHandler to stop the scan
    private Runnable stopScan = new Runnable() {
        @Override
        public void run() {
            scanStop();
        }
    };

    // ----------------------------------------------------------------------------------------------------------------
    // Stops a scan
    private void scanStop() {
        if (areScanning) {															                //See if still scanning
            bleService.scanStop();                                         							//Stop the scan in progress
            areScanning = false;						                							//Indicate that we are not scanning
            setProgressBarIndeterminateVisibility(false);                                           //Show circular progress bar
            invalidateOptionsMenu();                                                                //The options menu needs to be refreshed
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Callback for Activity that returns a result
    // We call BluetoothAdapter to turn on the Bluetooth radio
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQ_CODE_ENABLE_BT) {                                                    //User was requested to enable Bluetooth
            if (resultCode == Activity.RESULT_OK) {                                                 //User chose to enable Bluetooth
                scanStart();
            }
            else {
                onBackPressed();                                                                    //User chose not to enable Bluetooth so do back to calling activity
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, intent);		                            //Pass the activity result up to the parent method
    }

    // Start scanning for BLE devices
    private final static String TAG = MldpBluetoothScanActivity.class.getSimpleName();              //Activity name for logging messages on the ADB

    public static final String INTENT_EXTRA_SCAN_ADDRESS = "BLE_SCAN_DEVICE_ADDRESS";
    public static final String INTENT_EXTRA_SCAN_NAME = "BLE_SCAN_DEVICE_NAME";
    public static final String INTENT_EXTRA_SCAN_AUTO_CONNECT = "BLE_SCAN_AUTO_CONNECT";
    private static final int REQ_CODE_ENABLE_BT = 2;                                                //Code to identify activity that enables Bluetooth

    private static final long SCAN_TIME = 10000;						                            //Length of time in milliseconds to scan for BLE devices
    private Handler scanStopHandler;                                                                //Handler to stop the scan after a time delay

    private MldpBluetoothService bleService;
    private DeviceListAdapter bleDeviceListAdapter;
    private boolean areScanning;
    private CheckBox alwaysConnectCheckBox;

    // ----------------------------------------------------------------------------------------------------------------
    // Activity launched
    // Start and bind to the MldpBluetoothService
    @Override
    public void onCreate(Bundle savedInstanceState) {
        //requestWindowFeature(Window.FEATURE_ACTION_BAR);                                            //Request the ActionBar feature - automatic with the theme selected in AndroidManifest.xml
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);                                //Request the circular progress feature
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scan_list_screen);                                                  //Show the screen
        ActionBar actionBar = getActionBar();                                                       //Get the ActionBar
        actionBar.setTitle(R.string.scan_for_devices);                                              //Set the title on the ActionBar
        actionBar.setDisplayHomeAsUpEnabled(true);					                                //Make home icon clickable with < symbol on the left to go back
        setProgressBarIndeterminate(true);                                                          //Make the progress bar indeterminate
        setProgressBarIndeterminateVisibility(true);                                                //Make the progress bar visible
        alwaysConnectCheckBox = (CheckBox) findViewById(R.id.alwaysConnectCheckBox);                //Get a reference to the checkbox on the screen

        Intent bleServiceIntent = new Intent(this, MldpBluetoothService.class);	                    //Create Intent to bind to the MldpBluetoothService
        this.bindService(bleServiceIntent, bleServiceConnection, BIND_AUTO_CREATE);	                //Bind to the  service and use bleServiceConnection callbacks for service connect and disconnect
        scanStopHandler = new Handler();                                                            //Create a handler for a delayed runnable that will stop the scan after a time
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity resumed
    // Initializes list view adapter
    @Override
    protected void onResume() {
        super.onResume();
        bleDeviceListAdapter = new DeviceListAdapter(this, R.layout.scan_list_item);                //Create new list adapter to hold list of BLE devices found during scan
        setListAdapter(bleDeviceListAdapter);						                                //Bind to our new list adapter
        if(bleService != null) {                                                                    //Service will not have started when activity first starts but this ensures a scan if resuming from pause
            scanStart();
        }

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MldpBluetoothService.ACTION_BLE_SCAN_RESULT);
        registerReceiver (bleServiceReceiver, intentFilter);                                        //Register the receiver to receive the scan results broadcast by the service
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity paused
    // Stop scan and clear device list
    @Override
    protected void onPause() {
        super.onPause();
        if(bleService != null) {
            scanStopHandler.removeCallbacks(stopScan);                                              //Stop the scan timeout handler from calling the runnable to stop the scan
            scanStop();
        }
        unregisterReceiver(bleServiceReceiver);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity stopped
    // Unregister the BroadcastReceiver
    @Override
    public void onStop() {
        super.onStop();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity is ending
    // Unbind from the MldpBluetoothService service
    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(bleServiceConnection);                                                    //Unbind from the service
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Options menu is different depending on whether scanning or not
    // Show Scan option if not scanning
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.scan_activity_menu, menu);
        if (areScanning) {											                                //Are scanning
            menu.findItem(R.id.menu_scan).setVisible(false);                                        //so do not show Scan menu option
        } else {													                                //Are not scanning
            menu.findItem(R.id.menu_scan).setVisible(true);			                                //so show Scan menu option
        }
        return true;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Menu item selected
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:						                                            //Option to Scan chosen
                scanStart();
                break;
            case android.R.id.home:                                                                 //User pressed the back arrow next to the icon on the ActionBar
                onBackPressed();                                                                    //Treat it as if the back button was pressed
                return true;
        }
        return true;
    }
}