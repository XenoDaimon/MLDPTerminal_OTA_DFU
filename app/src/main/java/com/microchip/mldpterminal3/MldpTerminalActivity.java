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

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Activity provides a terminal interface to send and receive bytes from an MLDP enabled
 * Bluetooth LE module such as an RN4020.
 */
public class MldpTerminalActivity extends Activity {

    private final static String TAG = MldpTerminalActivity.class.getSimpleName();                   //Activity name for logging messages on the ADB

    private static final String PREFS = "PREFS";                                                    //Strings to identify fields stored in shared preferences
    private static final String PREFS_NAME = "NAME";                                                //used to save name and MAC address of Bluetooth device and
    private static final String PREFS_ADDRESS = "ADDR";                                             //whether to connect automatically on startup.
    private static final String PREFS_AUTO_CONNECT = "AUTO";
    private static final int REQ_CODE_SCAN_ACTIVITY = 1;                                            //Codes to identify activities that return results such as enabling Bluetooth
    private static final int REQ_CODE_ENABLE_BT = 2;                                                //or scanning for bluetooth devices.

    private static final long CONNECT_TIME = 5000;						                            //Length of time in milliseconds to try to connect to a device
    private Handler connectTimeoutHandler;                                                          //Handler to provide a time out if connection attempt takes too long
    private MldpBluetoothService bleService;                                                        //Service that handles all interaction with the Bluetooth radio and remote device

    private String bleDeviceName, bleDeviceAddress;                                                 //Name and address of remote Bluetooth device
    private boolean bleAutoConnect;                                                                 //Indication whether we should try to automatically connect to a device on startup
    private boolean attemptingAutoConnect = false;                                                  //Indication that we are trying to connect automatically

    private ShowAlertDialogs showAlert;                                                             //Object that creates and shows all the alert pop ups used in the app
    private SharedPreferences prefs;									                            //SharedPreferences storage area to save the name and address of the Bluetooth device

    private TextView textDeviceNameAndAddress, textConnectionState;                                    //To show device and status information on the screen
    private TextView textIncoming;                                                                  //To show the text received from the remote Bluetooth device
    private EditText textOutgoing;                                                                  //To type text to send to the remote Bluetooth device
    private Button buttonClearIncoming, buttonClearOutgoing;                                        //To clear the text on the display

    private EditText textDeviceName;                                                                  //To type text to change device name
    private Button buttonChangeName;                                                                 //Change device name

    private Button buttonSendDFU;                                                                   //To send the DFU image
    private Button buttonSwitchOTA;                                                                 //Prepare OTA mode
    private static int otaCheck = 0;

    private Switch switchOTA;

    private ProgressBar progressBarDFU;
    private TextView textProgressDFU;
    private static boolean isDisconnected;
    private static boolean hasFailed = false;

    private enum State {STARTING, ENABLING, SCANNING, CONNECTING, CONNECTED, DISCONNECTED, DISCONNECTING}; //States of the app.
    State state = State.STARTING;                                                                   //Initial state when app starts

    // ----------------------------------------------------------------------------------------------------------------
    // Activity launched
    // Invoked by Intent in onListItemClick method in MldpBluetoothScanActivity
    @Override
    public void onCreate(Bundle savedInstanceState) {
        //requestWindowFeature(Window.FEATURE_ACTION_BAR);                                            //Request the ActionBar feature - automatic with the theme selected in AndroidManifest.xml
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);                                //Request the circular progress feature
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_terminal_screen);                                              //Show the main terminal screen - may be shown briefly if we immediately start the scan activity

        setProgressBarIndeterminate(true);                                                          //Make the progress bar indeterminate
        setProgressBarIndeterminateVisibility(false);                                               //Hide the progress bar

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);								            //Get a reference to the SharedPreferences storage area
        if(prefs != null) {																	        //Check that a SharedPreferences exists
            bleAutoConnect = prefs.getBoolean(PREFS_AUTO_CONNECT, false);                           //Get the instruction to automatically connect or manually connect
            if (bleAutoConnect == true) {                                                           //Only need name and address if going to connect automatically
                bleDeviceName = prefs.getString(PREFS_NAME, null);                                  //Get the name of the last BLE device the app was connected to
                bleDeviceAddress = prefs.getString(PREFS_ADDRESS, null);                            //Get the address of the last BLE device the app was connected to
            }
        }
        state = State.STARTING;
        Intent bleServiceIntent = new Intent(this, MldpBluetoothService.class);	                    //Create Intent to start the MldpBluetoothService
        this.bindService(bleServiceIntent, bleServiceConnection, BIND_AUTO_CREATE);	                //Create and bind the new service to bleServiceConnection object that handles service connect and disconnect

        showAlert = new ShowAlertDialogs(this);                                                     //Create the object that will show alert dialogs
        textDeviceNameAndAddress = (TextView) findViewById(R.id.deviceNameAndAddress);		        //Get a reference to the TextView that will display the device
        textConnectionState = (TextView) findViewById(R.id.connectionState);		                //Get a reference to the TextView that will display the connection state
        textIncoming = (TextView) findViewById(R.id.incomingText);				                    //Get a reference to the TextView that will display data received
        textIncoming.setMovementMethod(new ScrollingMovementMethod());                              //Allow text to scroll within the TextView
        textOutgoing = (EditText) findViewById(R.id.outgoingText);                                  //Get a reference to the EditText used for entering data
        textOutgoing.setMovementMethod(new ScrollingMovementMethod());                              //Allow text to scroll within the TextView
        textOutgoing.addTextChangedListener(mOutgoingTextWatcher);                                  //Listen for changes so we can send byte by byte
        buttonClearOutgoing = (Button) findViewById(R.id.clearOutgoingButton);                      //Get a reference to the Button used for send data
        buttonClearOutgoing.setOnClickListener(mClearOutgoingButtonListener);                       //Listener for click on Send button
        buttonClearIncoming = (Button) findViewById(R.id.clearIncomingButton);                      //Get a reference to the Button used for send data
        buttonClearIncoming.setOnClickListener(mClearIncomingButtonListener);                       //Listener for click on Send button

        buttonSendDFU = (Button) findViewById(R.id.buttonDFU);
        buttonSendDFU.setOnClickListener(mSendDFUButtonListener);

        buttonSwitchOTA = (Button) findViewById(R.id.buttonOTA);
        buttonSwitchOTA.setOnClickListener(mSwitchOTAButtonListener);
//        buttonSwitchOTA.setEnabled(false);

        switchOTA = (Switch) findViewById(R.id.switchOTA);
        switchOTA.setOnCheckedChangeListener(mSwitchOTAListener);

        progressBarDFU = (ProgressBar) findViewById(R.id.progressBar);
        progressBarDFU.setMax(100);
        progressBarDFU.setProgress(0);

        textProgressDFU = (TextView) findViewById(R.id.progressionPercentageText);

        connectTimeoutHandler = new Handler();                                                      //Create a handler for a delayed runnable that will stop the connection attempt
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity resumed
    // Register the receiver for intents from the MldpBluetoothService
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(bleServiceReceiver, bleServiceIntentFilter()); 	                        //Register receiver to handles events fired by the service: connected, disconnected, discovered services, received data from read or notification operation
//        if (bleService != null && bleService.isBluetoothRadioEnabled() == false) {                  //See if the Bluetooth radio is on
//            state = State.ENABLING;
//            updateConnectionState();                                                                //Update the screen and menus
//            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);             //Create an intent asking the user to grant permission to enable Bluetooth
//            startActivityForResult(enableBtIntent, REQ_CODE_ENABLE_BT);                             //Fire the intent to start the activity that will return a result based on user input
//            Log.d(TAG, "Requesting user to enable Bluetooth radio");					            //Send debug message
//        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity paused
    // Unregister the receiver for intents from the MldpBluetoothService
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(bleServiceReceiver);                                                     //Unregister receiver that was registered in onResume()
        //showAlert.dismiss();                                                                        //Dismiss any dialogs
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity stopped
    // Save the details of the BLE device for next time
    @Override
    public void onStop() {
        super.onStop();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);                                          //Get a reference to the SharedPreferences storage area
        SharedPreferences.Editor editor = prefs.edit();                                             //Create a SharedPreferences editor
        editor.clear();                                                                             //Clear all saved preferences
        editor.putBoolean(PREFS_AUTO_CONNECT, bleAutoConnect);                                      //Use the editor to put the instruction to automatically connect in the SharedPreferences
        if (bleAutoConnect == true) {                                                               //Only need name and address if going to connect automatically
            editor.putString(PREFS_NAME, bleDeviceName);                                            //Use the editor to put the current device name in the SharedPreferences
            editor.putString(PREFS_ADDRESS, bleDeviceAddress);                                      //Use the editor to put the current MAC address in the SharedPreferences
        }
        editor.commit();                                                                            //Write the changes into the SharedPreferences storage
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Activity is ending
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(bleServiceConnection);                                                        //Unbind from the service handling Bluetooth
        bleService = null;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Options menu is different depending on whether connected or not
    // Show Disconnect option if we are connected or show Connect option if not connected and have a device address
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_terminal_menu, menu);
        if (state == State.CONNECTED) {                                                             //See if we are connected
            menu.findItem(R.id.menu_disconnect).setVisible(true);                                   //Are connected so show Disconnect menu
            menu.findItem(R.id.menu_connect).setVisible(false);                                     //and hide Connect menu
        } else {
            menu.findItem(R.id.menu_disconnect).setVisible(false);                                  //Are not connected so hide the disconnect menu
            if (bleDeviceAddress != null) {                                                         //See if we have a device address
            menu.findItem(R.id.menu_connect).setVisible(true);                                      //Have a device address so show the connect menu
            }
            else {
                menu.findItem(R.id.menu_connect).setVisible(true);                                  //No address so hide the connect menu
            }
        }
        return true;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Menu item selected
    // Connect or disconnect to BLE device
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:                                                                    //Menu option Scan chosen
                startScan();                                                                        //Launch the MldpBluetoothScanActivity to scan for BLE devices supporting MLDP service
                return true;

            case R.id.menu_connect:                                                                 //Menu option Connect chosen
                if(bleDeviceAddress != null) {                                                      //Check that there is a valid Bluetooth LE address
                    connectWithAddress(bleDeviceAddress);                                           //Call method to ask the MldpBluetoothService to connect
                }
                return true;

            case R.id.menu_disconnect:                                                              //Menu option Disconnect chosen
                state = State.DISCONNECTING;                                                        //Used to determine whether disconnect event should trigger a popup to reconnect
                updateConnectionState();                                                            //Update the screen and menus
                bleService.disconnect();                                                            //Ask the MldpBluetoothService to disconnect
                return true;

            case R.id.menu_help:                                                                    //Menu option Help chosen
                showAlert.showHelpMenuDialog(this.getApplicationContext());                          //Show the AlertDialog that has the Help text
                return true;

            case R.id.menu_about:                                                                   //Menu option About chosen
                showAlert.showAboutMenuDialog();                                                    //Show the AlertDialog that has the About text
                return true;

            case R.id.menu_exit:                                                                    //Menu option Exit chosen
                showAlert.showExitMenuDialog(new Runnable() {                                       //Show the AlertDialog that has the Exit warning text
                    @Override
                    public void run()                                                               //Runnable to execute if OK button pressed
                    {
                        bleService.disconnect();                                                    //Disconnect from any devices
                        onBackPressed();                            //Check this                                //Exit by going back
                    }
                });
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Intent filter to add Intent values that will be broadcast by the MldpBluetoothService to the bleServiceReceiver BroadcastReceiver
    private static IntentFilter bleServiceIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MldpBluetoothService.ACTION_BLE_REQ_ENABLE_BT);
        intentFilter.addAction(MldpBluetoothService.ACTION_BLE_CONNECTED);
        intentFilter.addAction(MldpBluetoothService.ACTION_BLE_DISCONNECTED);
        intentFilter.addAction(MldpBluetoothService.ACTION_BLE_DATA_RECEIVED);
        return intentFilter;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // BroadcastReceiver handles various events fired by the MldpBluetoothService service.
    private final BroadcastReceiver bleServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (MldpBluetoothService.ACTION_BLE_CONNECTED.equals(action)) {			                //Service has connected to BLE device
                connectTimeoutHandler.removeCallbacks(abortConnection);                             //Stop the connection timeout handler from calling the runnable to stop the connection attempt
                Log.d(TAG, "Received intent  ACTION_BLE_CONNECTED");
                state = State.CONNECTED;
                updateConnectionState();                                                            //Update the screen and menus
                if (attemptingAutoConnect == true) {
                    showAlert.dismiss();
                }
            }
            else if (MldpBluetoothService.ACTION_BLE_DISCONNECTED.equals(action)) {		            //Service has disconnected from BLE device
                Log.d(TAG, "Received intent ACTION_BLE_DISCONNECTED");
                if (state == State.CONNECTED) {
                    showLostConnectionDialog();                                                     //Show dialog to ask to scan for another device
                }
                else {
                    if (attemptingAutoConnect == true) {
                        showAlert.dismiss();
                    }
                    clearUI();
                    if (state != State.DISCONNECTING) {                                             //See if we are not deliberately disconnecting
                        showNoConnectDialog();                                                      //Show dialog to ask to scan for another device
                    }
                }
                state = State.DISCONNECTED;
                updateConnectionState();                                                            //Update the screen and menus
            }
            else if (MldpBluetoothService.ACTION_BLE_DATA_RECEIVED.equals(action)) {		        //Service has found new data available on BLE device
                Log.d(TAG, "Received intent ACTION_BLE_DATA_RECEIVED");
                String data = intent.getStringExtra(MldpBluetoothService.INTENT_EXTRA_SERVICE_DATA); //Get data as a string to display
//                String data = null;
//                try {
//                    data = new String(intent.getByteArrayExtra(MldpBluetoothService.INTENT_EXTRA_SERVICE_DATA), "UTF-8"); // Example for bytes instead of string
//                } catch (UnsupportedEncodingException e) {
//                    e.printStackTrace();
//                }
                if (data != null) {
                    textIncoming.append(data);
                    Log.w(TAG, data);

                    // Disable button switch OTA and accept move switch OTA to true
                    if (data.contains("OTA\r\n")) {
                        buttonSwitchOTA.setEnabled(false);
                        switchOTA.setChecked(true);
                    }

                    if (data.contains("OTA\r\n") && otaCheck == 1)                                  // If OTA button has been pressed and OTA has been received, send the DFU file
                        new sendDFUFile(false).execute();
                    else if (data.contains("CMD\r\n"))                                              // If we received CMD, enable the button switch to OTA and send DFU
                        buttonSwitchOTA.setEnabled(true);

                    if (data.contains("Upgrade Err")) {                                             // Stop the data transfer if DFU failed
                        hasFailed = true;
                    }
                }
            }
        }
    };


    // ----------------------------------------------------------------------------------------------------------------
    // Attempt to connect to a Bluetooth device given its address and time out after CONNECT_TIME milliseconds
    private boolean connectWithAddress(String address) {
        state = State.CONNECTING;
        updateConnectionState();                                                                    //Update the screen and menus
        connectTimeoutHandler.postDelayed(abortConnection, CONNECT_TIME);
        return bleService.connect(address);                                                         //Ask the MldpBluetoothService to connect
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Runnable used by the connectTimeoutHandler to stop the connection attempt
    private Runnable abortConnection = new Runnable() {
        @Override
        public void run() {
            if (state == State.CONNECTING) {                                                        //See if still trying to connect
                bleService.disconnect();                      							            //Stop the connection in progress
                showNoConnectDialog();
            }
        }
    };

    // ----------------------------------------------------------------------------------------------------------------
    //
    private void showAutoConnectDialog() {
        state = State.CONNECTING;
        updateConnectionState();                                                                    //Update the screen and menus
        showAlert.showAutoConnectDialog(new Runnable() {                                            //Show the AlertDialog that a connection to the stored device is being attempted
            @Override
            public void run() {                                                                     //Runnable to execute if Cancel button pressed
                startScan();                                                                        //Launch the MldpBluetoothScanActivity to scan for BLE devices supporting MLDP service
            }
        });
    }

    // ----------------------------------------------------------------------------------------------------------------
    //
    private void showNoConnectDialog() {
        state = State.DISCONNECTED;
        updateConnectionState();                                                                    //Update the screen and menus
        showAlert.showFailedToConnectDialog(new Runnable() {                                        //Show the AlertDialog for a connection attempt that failed
            @Override
            public void run() {                                                                     //Runnable to execute if OK button pressed
                startScan();                                                                        //Launch the MldpBluetoothScanActivity to scan for BLE devices supporting MLDP service
            }
        });
    }

    // ----------------------------------------------------------------------------------------------------------------
    //
    private void showLostConnectionDialog() {
        state = State.DISCONNECTED;
        updateConnectionState();                                                                    //Update the screen and menus
        showAlert.showLostConnectionDialog(new Runnable() {                                         //Show the AlertDialog for a lost connection
            @Override
            public void run() {                                                                     //Runnable to execute if OK button pressed
                startScan();                                                                        //Launch the MldpBluetoothScanActivity to scan for BLE devices supporting MLDP service
            }
        });
    }

    // ----------------------------------------------------------------------------------------------------------------
    //
    private void startScan() {
        bleService.disconnect();                                                                    //Disconnect an existing connection or cancel a connection attempt
        state = State.DISCONNECTING;
        //updateConnectionState();                                                                    //Update the screen and menus
        final Intent bleScanActivityIntent = new Intent(MldpTerminalActivity.this, MldpBluetoothScanActivity.class); //Create Intent to start the MldpBluetoothScanActivity
        startActivityForResult(bleScanActivityIntent, REQ_CODE_SCAN_ACTIVITY);                      //Start the MldpBluetoothScanActivity
    }

    // ----------------------------------------------------------------------------------------------------------------
    // 
//    private void updateConnectionState(final int resourceId) {
    private void updateConnectionState() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (state) {
                    case STARTING:
                    case ENABLING:
                    case SCANNING:
                    case DISCONNECTED:
                        textConnectionState.setText(R.string.not_connected);
                        setProgressBarIndeterminateVisibility(false);                               //Hide circular progress bar
                        isDisconnected = true;
                        break;
                    case CONNECTING:
                        textConnectionState.setText(R.string.connecting);
                        setProgressBarIndeterminateVisibility(true);                                //Show circular progress bar
                        break;
                    case CONNECTED:
                        textConnectionState.setText(R.string.connected);
                        setProgressBarIndeterminateVisibility(false);                               //Hide circular progress bar
                        buttonSwitchOTA.setEnabled(true);
                        buttonSendDFU.setEnabled(true);
                        switchOTA.setEnabled(true);
                        switchOTA.setChecked(false);
                        isDisconnected = false;
                        hasFailed = false;
                        break;
                    case DISCONNECTING:
                        textConnectionState.setText(R.string.disconnecting);
                        setProgressBarIndeterminateVisibility(false);                               //Hide circular progress bar
                        buttonSwitchOTA.setEnabled(false);
                        buttonSendDFU.setEnabled(false);
                        switchOTA.setEnabled(false);
                        switchOTA.setChecked(false);
                        isDisconnected = true;
                        break;
                    default:
                        state = State.STARTING;
                        setProgressBarIndeterminateVisibility(false);                               //Hide circular progress bar
                        break;
                }

                invalidateOptionsMenu();                                                            //Update the menu

                if (bleDeviceName != null) {                                                        //See if there is a device name
                    textDeviceNameAndAddress.setText(bleDeviceName);                                //Display the name
                }
                else {
                    textDeviceNameAndAddress.setText(R.string.unknown);                             //or display Unknown
                }
                if (bleDeviceAddress != null) {                                                     //See if there is an address
                    textDeviceNameAndAddress.append(" - " + bleDeviceAddress);                      //Display the address
                }
            }
        });
    }

    // ----------------------------------------------------------------------------------------------------------------
    // 
    private void clearUI() {
        textIncoming.setText(null);
        textOutgoing.setText(null);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Listener for a KeyEvent
    private final TextWatcher mOutgoingTextWatcher = new TextWatcher() {

        public void beforeTextChanged(CharSequence cs, int i, int i1, int i2) {
        }

        public void onTextChanged(CharSequence cs, int start, int before, int count) {              //Note that keyboard returns LF, not CR when enter key is pressed
            if(count > before) {
                bleService.writeMLDP(cs.subSequence(start + before, start + count).toString());     //Write the text string to the MLDP characteristic (user cannot type fast enough to overflow)
                //bleService.writeMLDP(cs.subSequence(start + before, start + count).toString().getBytes()); //Same example but using bytes instead of string
            }
        }

        public void afterTextChanged(Editable edtbl) {
        }
    };
    
    // ----------------------------------------------------------------------------------------------------------------
    // Listener for the Clear Incoming button
    private final Button.OnClickListener mClearIncomingButtonListener = new Button.OnClickListener() {
        public void onClick(View view) {
            textIncoming.setText(null);
            textIncoming.scrollTo(0, 0);
        }
    };

    // ----------------------------------------------------------------------------------------------------------------
    // Listener for the Clear Outgoing button
    private final Button.OnClickListener mClearOutgoingButtonListener = new Button.OnClickListener() {
        public void onClick(View view) {
            textOutgoing.setText(null);
            textOutgoing.scrollTo(0, 0);
        }
    };

    /* Listener for the Send OTA DFU button (will send a DFU file using MLDP then switch OTA control and send the DFU again) */
    private final Button.OnClickListener mSwitchOTAButtonListener = new Button.OnClickListener() {
        public void onClick(View view) {
            new sendDFUFile(true).execute();
            buttonSwitchOTA.setEnabled(false);
        }
    };

    /* Listener for the OTA switch (sends 2 or 0 to the MLDP control characteristic) */
    private final CompoundButton.OnCheckedChangeListener mSwitchOTAListener = (new CompoundButton.OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                byte[] OTAMode = new byte[1];
                OTAMode[0] = 2;
                bleService.writeControlMLDP(OTAMode);
            }
            else {
                byte[] OTAMode = new byte[1];
                OTAMode[0] = 0;
                bleService.writeControlMLDP(OTAMode);
            }

        }
    });

    // Listener for the DFU send button
    private final Button.OnClickListener mSendDFUButtonListener = new Button.OnClickListener() {
        public void onClick(View view) {
            Log.d(TAG, "Sending file using background worker.");
            new sendDFUFile(false).execute();
        }

    };

    /* Class used to send the DFU file and update a progress bar in the UIThread */
    private class sendDFUFile extends AsyncTask<String, Integer, Boolean> {
        protected boolean isOTA = false;

        /* Check if this is an OTA (need to send 2 files) */
        public sendDFUFile(boolean isOTA) {
            super();
            this.isOTA = isOTA;
        }

        /* Update the progress bar based on the DFU file size */
        @Override
        protected void onProgressUpdate(Integer... values) {
            int progress = ((Integer[])values)[0];
            int maximum = ((Integer[])values)[1];
            progressBarDFU.setMax(100);
            progressBarDFU.setProgress(progress * 100 / maximum);
            textProgressDFU.setText(Math.round(progress * 100 / maximum) + "% - " + String.format("%.2f", (progress / 1000f)) + "KB / " + String.format("%.2f", (maximum / 1000f)) + "KB");
            super.onProgressUpdate(values);
        }

        /* Disable buttons, texts and switch to avoid misuse */
        @Override
        protected void onPreExecute() {
            switchOTA.setEnabled(false);
            textOutgoing.setEnabled(false);
            if (isOTA)
                buttonSwitchOTA.setEnabled(false);
            buttonSendDFU.setEnabled(false);
        }

        /* Enable back the buttons, texts and switch */
        @Override
        protected void onPostExecute(Boolean result) {
            textOutgoing.setEnabled(true);
            buttonSendDFU.setEnabled(true);
            if (!result && isOTA)
                buttonSwitchOTA.setEnabled(true);
            if (result)
                textProgressDFU.setText("Firmware file sent");
            else
                textProgressDFU.setText("Failed to send firmware file");
            if (!isOTA)
                switchOTA.setEnabled(true);
        }

        /* Prepare and send the DFU bin file */
        @Override
        protected Boolean doInBackground(String... strings) {
                try {
                    List<Byte> DFUList = new ArrayList<Byte>();
                    DataInputStream dis = new DataInputStream(new BufferedInputStream(getAssets().open("RN4020BEC_133_112415_DFU.bin")));   // Get the data stream of the bin file from assets
                    while (true) {
                        try {
                            Byte endian = dis.readByte();
                            DFUList.add(endian);
                            DFUList.add(dis.readByte());
                        } catch (EOFException e) {
                            break;
                        }
                    }
                    dis.close();
                    Byte[] DFUarr = DFUList.toArray(new Byte[DFUList.size()]);                      // Create Byte array from the file data
                    printHexValues(DFUarr);                                                         // Used for debug (print hex values of DFUarr
                    //Log.d(TAG, DFUList.toString());
                    Log.d(TAG, "Byte list size: " + DFUList.size() + " |  Byte array length: " + DFUarr.length);

                    byte[] byteValues = toPrimitives(DFUarr);                                       // Switch type from Byte to byte
                    boolean isComplete = false;
                    if (!createMLDPByteArray(byteValues, isComplete)) {                             // Send the DFU byte arrays to the RN4020
                        return false;
                    }
                    if (isOTA) {
                        sendOTASignal();                                                            // If the class has been call for OTA, send 2 to the MLDP Control characteristic
                        Log.d(TAG, "OTA signal sent.");
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to open DFU file.");
                    Log.e(TAG, e.getMessage());
                    return false;
                }
                Log.d(TAG, "DFU transfer done.");
            return true;
        }

        /* Send the OTA signal to the RN4020 (MLDP Control 2) */
        protected void sendOTASignal() {
            byte[] OTAMode = new byte[1];
            OTAMode[0] = 2;
            bleService.writeControlMLDP(OTAMode);
        }

        /* Cut the byte array of the DFU file in byte[16] in order to send it to the RN4020 (max data size in MLDP_data is 20) */
        protected Boolean createMLDPByteArray(byte[] byteValues, boolean isComplete) {
            try {
                double mem = 0;
                double per = 0;
                for (int i = 0; i * 16 < byteValues.length; ++i) {                                  // Run this until we run out of bytes
                    byte[] msg = new byte[16];
                    for (int j = 0; j < 16; ++j) {                                                  // Create the byte[16] and fill it with
                        if (j + i * 16 >= byteValues.length) {
                            isComplete = true;                                                      // Switch is complete if we have less than 16 bytes to send before the end of the array
                            break;
                        }
                        msg[j] = byteValues[j + i * 16];
                    }
                    if (isComplete) {                                                               // Less than 16 bytes until the end of the array, create an array of length < 16 to avoid garbage
                        msg = new byte[byteValues.length - (i * 16)];
                        for (int j = 0; j < msg.length; j++)
                            msg[j] = byteValues[j + i * 16];
                    }
                    if (hasFailed) {                                                                // hasFailed is switch to true if we receive "Upgrade Err" in order to stop sending data
                        hasFailed = false;
                        return false;
                    }
                    if (isDisconnected)                                                             // isDisconnected is switch to true if we lost connection or disconnect from the device. Stop sending data
                        return false;

                    Thread.sleep(18);                                                               // During tests we used 18ms of sleep between each packet. Less results in packet loss. 18 seems stable.
                                                                                                    // More is fine too but can take a long time to finish: (48kB / 16) * nbr of ms time .. (ex: 18ms -> ~54s)
                    bleService.writeMLDP(msg);                                                               //Write the DFU bin to the ble device

                    per = ((i / (byteValues.length / 16.0)) * 100.0);                               // Percentage calculation for update
                    if (per - mem > 0.05) {                                                         // Update every 0.05% or more from last update
                        mem = per;
                        publishProgress(i * 16, byteValues.length);
                        Log.d(TAG, "Upload in progress: " + String.format("%.2f", per) + "%");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in " + e.getStackTrace() + ": " + e.getMessage());
                return false;
            }
            return true;
        }
   }

   /* Convert Byte array to byte array */
    byte[] toPrimitives(Byte[] oBytes)
    {
        byte[] bytes = new byte[oBytes.length];

        for(int i = 0; i < oBytes.length; i++) {
            bytes[i] = oBytes[i];
        }
        return bytes;
    }

    /* Print hex values from a Byte array */
    private final void printHexValues(Byte[] bytes) {
        char [] hexArray = "0123456789abcdef".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        Log.d(TAG, new String(hexChars));
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Callback for Activities that return a result
    // We call BluetoothAdapter to turn on the Bluetooth radio and MldpBluetoothScanActivity to scan
    // and return the name and address of a Bluetooth device that the user chooses
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQ_CODE_ENABLE_BT) {                                                    //User was requested to enable Bluetooth
            if (resultCode == Activity.RESULT_OK) {                                                 //User chose to enable Bluetooth
                if(bleAutoConnect == false  || bleDeviceAddress == null) {                          //Not automatically connecting or do not have an address so must do a scan to select a BLE device
                    startScan();
                }
                else {                                                                              //Automatically connect to the last Bluetooth device used
                    attemptingAutoConnect = true;
                    showAutoConnectDialog();
                    if (!connectWithAddress(bleDeviceAddress)) {                                    //Ask the MldpBluetoothService to connect and see if it failed
                        showNoConnectDialog();                                                      //Show dialog to ask to scan for another device
                    }
                }
            }
            return;
        }
        else if(requestCode == REQ_CODE_SCAN_ACTIVITY) {                                            //Result from BluetoothScanActivity
            showAlert.dismiss();
            if (resultCode == Activity.RESULT_OK) {                                                 //User chose a Bluetooth device to connect
                bleDeviceAddress = intent.getStringExtra(MldpBluetoothScanActivity.INTENT_EXTRA_SCAN_ADDRESS); //Get the address of the BLE device selected in the MldpBluetoothScanActivity
                bleDeviceName = intent.getStringExtra(MldpBluetoothScanActivity.INTENT_EXTRA_SCAN_NAME);   //Get the name of the BLE device selected in the MldpBluetoothScanActivity
                bleAutoConnect = intent.getBooleanExtra(MldpBluetoothScanActivity.INTENT_EXTRA_SCAN_AUTO_CONNECT, false); //Get the instruction to automatically connect or manually connect
                if(bleDeviceAddress == null) {
                    state = State.DISCONNECTED;
                    updateConnectionState();                                                        //Update the screen and menus
                }
                else {
                    state = State.CONNECTING;
                    updateConnectionState();                                                        //Update the screen and menus
                    connectWithAddress(bleDeviceAddress);
                }
            }
            else {
                state = State.DISCONNECTED;
                updateConnectionState();                                                            //Update the screen and menus
            }
        }
        super.onActivityResult(requestCode, resultCode, intent);		//Pass the activity result up to the parent method
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Callbacks for MldpBluetoothService service connection and disconnection
    private final ServiceConnection bleServiceConnection = new ServiceConnection() {		        //Create new ServiceConnection interface to handle connection and disconnection

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {		        //Service connects
            MldpBluetoothService.LocalBinder binder = (MldpBluetoothService.LocalBinder) service;   //Get the Binder for the Service
            bleService = binder.getService();                                                       //Get a link to the Service from the Binder
            if (bleService.isBluetoothRadioEnabled()) {                                             //See if the Bluetooth radio is on
                if(bleAutoConnect == false  || bleDeviceAddress == null) {                          //Not automatically connecting or do not have an address so must do a scan to select a BLE device
                    startScan();
                }
                else {
                    attemptingAutoConnect = true;
                    showAutoConnectDialog();
                    if (!connectWithAddress(bleDeviceAddress)) {                                    //Ask the MldpBluetoothService to connect and see if it failed
                        showNoConnectDialog();                                                      //Show dialog to ask to scan for another device
                    }
                }
            }
            else {                                                                                  //Radio needs to be enabled
                state = State.ENABLING;
                updateConnectionState();                                                            //Update the screen and menus
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);         //Create an intent asking the user to grant permission to enable Bluetooth
                startActivityForResult(enableBtIntent, REQ_CODE_ENABLE_BT);                         //Fire the intent to start the activity that will return a result based on user input
                Log.d(TAG, "Requesting user to enable Bluetooth radio");					                //Send debug message
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {			                //Service disconnects - should never happen
            bleService = null;								                                        //Service has no connection
        }
    };

}
