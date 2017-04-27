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
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import java.util.List;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

/**
 * Service for handling Bluetooth communication with the RN4020 using the Microchip Low-energy Data Profile, MLDP.
 *
 * This service uses the BluetoothAdapter.startLeScan() and stopLeScan() which have been deprecated in API level 21 (Android 5).
 * Rather use BluetoothLeScanner.startScan() and stopScan() if Android 4.x does not need to be supported.
 */
public class MldpBluetoothService extends Service {

    private final static String TAG = MldpBluetoothService.class.getSimpleName();                   //Service name for logging messages on the ADB

    public static final String INTENT_EXTRA_SERVICE_ADDRESS = "BLE_SERVICE_DEVICE_ADDRESS";
    public static final String INTENT_EXTRA_SERVICE_NAME = "BLE_SERVICE_DEVICE_NAME";
    public static final String INTENT_EXTRA_SERVICE_DATA = "BLE_SERVICE_DATA";

    public final static String ACTION_BLE_REQ_ENABLE_BT = "com.microchip.mldpterminal3.ACTION_BLE_REQ_ENABLE_BT";
    public final static String ACTION_BLE_SCAN_RESULT = "com.microchip.mldpterminal3.ACTION_BLE_SCAN_RESULT";
    public final static String ACTION_BLE_CONNECTED = "com.microchip.mldpterminal3.ACTION_BLE_CONNECTED";
    public final static String ACTION_BLE_DISCONNECTED = "com.microchip.mldpterminal3.ACTION_BLE_DISCONNECTED";
    public final static String ACTION_BLE_DATA_RECEIVED = "com.microchip.mldpterminal3.ACTION_BLE_DATA_RECEIVED";

    //The MLDP UUID will be included in the RN4020 Advertising packet unless a private service and characteristic exists. In that case use the private service UUID here instead.
    private final static byte[] SCAN_RECORD_MLDP_PRIVATE_SERVICE = {0x00, 0x03, 0x00, 0x3a, 0x12, 0x08, 0x1a, 0x02, (byte) 0xdd, 0x07, (byte) 0xe6, 0x58, 0x03, 0x5b, 0x03, 0x00};

    private final static UUID UUID_MLDP_PRIVATE_SERVICE = UUID.fromString("00035b03-58e6-07dd-021a-08123a000300"); //Private service for Microchip MLDP
    private final static UUID UUID_MLDP_DATA_PRIVATE_CHAR = UUID.fromString("00035b03-58e6-07dd-021a-08123a000301"); //Characteristic for MLDP Data, properties - notify, write
    private final static UUID UUID_MLDP_CONTROL_PRIVATE_CHAR = UUID.fromString("00035b03-58e6-07dd-021a-08123a0003ff"); //Characteristic for MLDP Control, properties - read, write


    private final static UUID UUID_DEVICE_NAME_GENERIC_ACCESS = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb"); // Characteristic for Device Name property - read, write


    private final static UUID UUID_TANSPARENT_PRIVATE_SERVICE = UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455"); //Private service for Microchip Transparent
    private final static UUID UUID_TRANSPARENT_TX_PRIVATE_CHAR = UUID.fromString("49535343-1e4d-4bd9-ba61-23c647249616"); //Characteristic for Transparent Data from BM module, properties - notify, write, write no response
    private final static UUID UUID_TRANSPARENT_RX_PRIVATE_CHAR = UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3"); //Characteristic for Transparent Data to BM module, properties - write, write no response

    private final static UUID UUID_CHAR_NOTIFICATION_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"); //Special descriptor needed to enable notifications
    private UUID[] uuidScanList = {UUID_MLDP_PRIVATE_SERVICE, UUID_TANSPARENT_PRIVATE_SERVICE};
    private final Queue<BluetoothGattDescriptor> descriptorWriteQueue = new LinkedList<BluetoothGattDescriptor>();
    private final Queue<BluetoothGattCharacteristic> characteristicWriteQueue = new LinkedList<BluetoothGattCharacteristic>();

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic mldpDataCharacteristic, transparentTxDataCharacteristic, transparentRxDataCharacteristic;

    private BluetoothGattCharacteristic mldpControlCharacteristic;
    private BluetoothGattCharacteristic genericDeviceNameCharacteristic;

    private int connectionAttemptCountdown = 0;

    // ----------------------------------------------------------------------------------------------------------------
    // Client Activity has bound to our Service
    @Override
    public IBinder onBind(Intent intent) {
        final IBinder binder = new LocalBinder();                                                   //Create an instance of our Binder that allows clients to use this Service
        return binder;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // All activities have stopped using the service so close the Bluetooth GATT connection
    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Service is created when first Activity binds to it
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);          //Get a reference to BluetoothManager from the operating system
            if (bluetoothManager == null) {                                                             //Check that we did get a BluetoothManager
                Log.e(TAG, "Unable to initialize the BluetoothManager");
            }
            else {
                bluetoothAdapter = bluetoothManager.getAdapter();                                       //Get a reference to BluetoothAdapter from the BluetoothManager
                if (bluetoothAdapter == null) {                                                         //Check that we did get a BluetoothAdapter
                    Log.e(TAG, "Unable to obtain a BluetoothAdapter");
                }
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Service ends when all Activities have unbound
    // Close any existing connection
    @Override
    public void onDestroy() {
        try {
            if (bluetoothGatt != null) {                                                                //See if there is an existing Bluetooth connection
                bluetoothGatt.close();                                                                  //Close the connection as the service is ending
                bluetoothGatt = null;                                                                   //Remove the reference to the connection we had
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        }
        super.onDestroy();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Returns instance of this MldpBluetoothService so clients of the service can access it's methods
    public class LocalBinder extends Binder {
        MldpBluetoothService getService() {
            return MldpBluetoothService.this;
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Implements callback methods for GATT events such as connecting, discovering services, write completion, etc.
    private final BluetoothGattCallback bleGattCallback = new BluetoothGattCallback() {
        //Connected or disconnected
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            try {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    connectionAttemptCountdown = 0;                                                     //Stop counting connection attempts
                    if (newState == BluetoothProfile.STATE_CONNECTED) {                                 //Connected
                        final Intent intent = new Intent(ACTION_BLE_CONNECTED);
                        sendBroadcast(intent);
                        Log.i(TAG, "Connected to BLE device");
                        descriptorWriteQueue.clear();                                                   //Clear write queues in case there was something left in the queue from the previous connection
                        characteristicWriteQueue.clear();
                        bluetoothGatt.discoverServices();                                               //Discover services after successful connection
                    }
                    else if (newState == BluetoothProfile.STATE_DISCONNECTED) {                         //Disconnected
                        final Intent intent = new Intent(ACTION_BLE_DISCONNECTED);
                        sendBroadcast(intent);
                        Log.i(TAG, "Disconnected from BLE device");
                    }
                }
                else {                                                                                  //Something went wrong with the connection or disconnection request
                    if (connectionAttemptCountdown-- > 0) {                                             //See is we should try another attempt at connecting
                        gatt.connect();                                                                 //Use the existing BluetoothGatt to try connect
                        Log.d(TAG, "Connection attempt failed, trying again");
                    }
                    else if (newState == BluetoothProfile.STATE_DISCONNECTED) {                         //Not trying another connection attempt and are not connected
                        final Intent intent = new Intent(ACTION_BLE_DISCONNECTED);
                        sendBroadcast(intent);
                        Log.i(TAG, "Unexpectedly disconnected from BLE device");
                    }
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
            }
        }

        //Service discovery completed
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            try {
                mldpDataCharacteristic = transparentTxDataCharacteristic = transparentRxDataCharacteristic = null;
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    List<BluetoothGattService> gattServices = gatt.getServices();                       //Get the list of services discovered
                    if (gattServices == null) {
                        Log.d(TAG, "No BLE services found");
                        return;
                    }
                    UUID uuid;
                    for (BluetoothGattService gattService : gattServices) {                             //Loops through available GATT services
                        uuid = gattService.getUuid();                                                   //Get the UUID of the service
                        if (uuid.equals(UUID_MLDP_PRIVATE_SERVICE) || uuid.equals(UUID_TANSPARENT_PRIVATE_SERVICE)) { //See if it is the MLDP or Transparent private service UUID
                            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
                            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) { //Loops through available characteristics
                                uuid = gattCharacteristic.getUuid();                                    //Get the UUID of the characteristic
                                Log.d(TAG, "UUID FOUND: " + uuid.toString());
                                if (uuid.equals(UUID_TRANSPARENT_TX_PRIVATE_CHAR)) {                    //See if it is the Transparent Tx data private characteristic UUID
                                    transparentTxDataCharacteristic = gattCharacteristic;
                                    final int characteristicProperties = gattCharacteristic.getProperties(); //Get the properties of the characteristic
                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_NOTIFY)) > 0) { //See if the characteristic has the Notify property
                                        bluetoothGatt.setCharacteristicNotification(gattCharacteristic, true); //If so then enable notification in the BluetoothGatt
                                        BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor(UUID_CHAR_NOTIFICATION_DESCRIPTOR); //Get the descriptor that enables notification on the server
                                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); //Set the value of the descriptor to enable notification
                                        descriptorWriteQueue.add(descriptor);                           //put the descriptor into the write queue
                                        if(descriptorWriteQueue.size() == 1) {                          //If there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
                                            bluetoothGatt.writeDescriptor(descriptor);                 //Write the descriptor
                                        }
                                    }
                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) { //See if the characteristic has the Write (unacknowledged) property
                                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE); //If so then set the write type (write with no acknowledge) in the BluetoothGatt
                                    }
                                    Log.d(TAG, "Found Transparent service Tx characteristics");
                                }
                                if (uuid.equals(UUID_TRANSPARENT_RX_PRIVATE_CHAR)) {                    //See if it is the Transparent Rx data private characteristic UUID
                                    transparentRxDataCharacteristic = gattCharacteristic;
                                    final int characteristicProperties = gattCharacteristic.getProperties(); //Get the properties of the characteristic
                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) { //See if the characteristic has the Write (unacknowledged) property
                                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE); //If so then set the write type (write with no acknowledge) in the BluetoothGatt
                                    }
                                    Log.d(TAG, "Found Transparent service Rx characteristics");
                                }

                                if (uuid.equals(UUID_MLDP_DATA_PRIVATE_CHAR)) {                         //See if it is the MLDP data private characteristic UUID
                                    mldpDataCharacteristic = gattCharacteristic;
                                    final int characteristicProperties = gattCharacteristic.getProperties(); //Get the properties of the characteristic
                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_NOTIFY)) > 0) { //See if the characteristic has the Notify property
                                        bluetoothGatt.setCharacteristicNotification(gattCharacteristic, true); //If so then enable notification in the BluetoothGatt
                                        BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor(UUID_CHAR_NOTIFICATION_DESCRIPTOR); //Get the descriptor that enables notification on the server
                                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); //Set the value of the descriptor to enable notification
                                        descriptorWriteQueue.add(descriptor);                           //put the descriptor into the write queue
                                        if(descriptorWriteQueue.size() == 1) {                          //If there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
                                            bluetoothGatt.writeDescriptor(descriptor);                 //Write the descriptor
                                        }
                                    }
//Use Indicate for RN4020 module firmware prior to 1.20 (not recommended)
//                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_INDICATE)) > 0) { //Only see if the characteristic has the Indicate property if it does not have the Notify property
//                                        bluetoothGatt.setCharacteristicNotification(gattCharacteristic, true); //If so then enable notification (and indication) in the BluetoothGatt
//                                        BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor(UUID_CHAR_NOTIFICATION_DESCRIPTOR); //Get the descriptor that enables indication on the server
//                                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE); //Set the value of the descriptor to enable indication
//                                        descriptorWriteQueue.add(descriptor);                           //put the descriptor into the write queue
//                                        if(descriptorWriteQueue.size() == 1) {                          //If there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
//                                            bluetoothGatt.writeDescriptor(descriptor);                  //Write the descriptor
//                                        }
//                                    }
                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) { //See if the characteristic has the Write (unacknowledged) property
                                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE); //If so then set the write type (write with no acknowledge) in the BluetoothGatt
                                    }
//Use Write With Response for RN4020 module firmware prior to 1.20 (not recommended)
//                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE)) > 0) { //See if the characteristic has the Write (acknowledged) property
//                                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT); //If so then set the write type (write with acknowledge) in the BluetoothGatt
//                                    }
                                    Log.d(TAG, "Found MLDP service and characteristics");
                                }

                                if (uuid.equals(UUID_MLDP_CONTROL_PRIVATE_CHAR)) {                  //See if it is the MLDP control private characteristic UUID
                                    mldpControlCharacteristic = gattCharacteristic;
                                    final int characteristicProperties = gattCharacteristic.getProperties(); //Get the properties of the characteristic
                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) { //See if the characteristic has the Write (unacknowledged) property
                                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE); //If so then set the write type (write with no acknowledge) in the BluetoothGatt
                                    }
                                    Log.d(TAG, "Found MLDP control service");
                                }

                                if (uuid.equals(UUID_DEVICE_NAME_GENERIC_ACCESS)) {
                                    genericDeviceNameCharacteristic = gattCharacteristic;
                                    final int characteristicProperties = gattCharacteristic.getProperties(); //Get the properties of the characteristic
                                    if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE)) > 0) { //See if the characteristic has the Write (unacknowledged) property
                                        gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT); //If so then set the write type (write with no acknowledge) in the BluetoothGatt
                                    }
                                    Log.d(TAG, "Found Device Name characteristic");
                                }
                            }
                            break;
                        }
                    }
                    if(mldpDataCharacteristic == null && (transparentTxDataCharacteristic == null || transparentRxDataCharacteristic == null)) {
                        Log.d(TAG, "Did not find MLDP or Transparent service");
                    }
                }
                else {
                    Log.w(TAG, "Failed service discovery with status: " + status);
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
            }
        }

        //Received notification or indication with new value for a characteristic
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            try {
                if (UUID_MLDP_DATA_PRIVATE_CHAR.equals(characteristic.getUuid()) || UUID_TRANSPARENT_TX_PRIVATE_CHAR.equals(characteristic.getUuid())) {                     //See if it is the MLDP data characteristic
                    String dataValue = characteristic.getStringValue(0);                                //Get the data in string format
                    //byte[] dataValue = characteristic.getValue();                                     //Example of getting data in a byte array
                    Log.d(TAG, "New notification or indication");
                    final Intent intent = new Intent(ACTION_BLE_DATA_RECEIVED);                         //Create the intent to announce the new data
                    intent.putExtra(INTENT_EXTRA_SERVICE_DATA, dataValue);                              //Add the data to the intent
                    sendBroadcast(intent);                                                              //Broadcast the intent
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
            }
        }

        //Write completed
        //Use write queue because BluetoothGatt can only do one write at a time
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            try {
                if (status != BluetoothGatt.GATT_SUCCESS) {                                             //See if the write was successful
                    Log.w(TAG, "Error writing GATT characteristic with status: " + status);
                }
                characteristicWriteQueue.remove();                                                      //Pop the item that we just finishing writing
                if(characteristicWriteQueue.size() > 0) {                                               //See if there is more to write
                    bluetoothGatt.writeCharacteristic(characteristicWriteQueue.element());              //Write characteristic
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
            }
        }

        //Write descriptor completed
        //Use write queue because BluetoothGatt can only do one write at a time
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            try {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "Error writing GATT descriptor with status: " + status);
                }
                descriptorWriteQueue.remove();                                                          //Pop the item that we just finishing writing
                if(descriptorWriteQueue.size() > 0) {                                                   //See if there is more to write
                    bluetoothGatt.writeDescriptor(descriptorWriteQueue.element());                      //Write descriptor
                }
            }
            catch (Exception e) {
                Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
            }
        }

        //Read completed. For information only. This application uses Notification or Indication to receive updated characteristic data, not Read
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        }
    };

    // ----------------------------------------------------------------------------------------------------------------
    // Check whether Bluetooth radio is enabled
    public boolean isBluetoothRadioEnabled() {
        try {
            if (bluetoothAdapter != null) {                                                             //Check that we have a BluetoothAdapter
                if (bluetoothAdapter.isEnabled()) {						                                //See if Bluetooth radio is enabled
                    return true;
                }
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        }
        return false;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Start scan for BLE devices
    // The bleScanCallback method is called each time a device is found during the scan
    public void scanStart() {
        try {
            if (Build.VERSION.SDK_INT >= 21) { //Build.VERSION_CODES.LOLLIPOP) {
                bluetoothAdapter.startLeScan(bleScanCallback);                                          //Start scanning with callback method to execute when a new BLE device is found
//                bluetoothAdapter.startLeScan(uuidScanList, bleScanCallback);                            //Start scanning with callback method to execute when a new BLE device is found
            }
            else {
                bluetoothAdapter.startLeScan(bleScanCallback);                                          //Start scanning with callback method to execute when a new BLE device is found
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Stop scan for BLE devices
    public void scanStop() {
        try {
            bluetoothAdapter.stopLeScan(bleScanCallback); 		                                        //Stop scanning - callback method indicates which scan to stop
        }
        catch (Exception e) {
            Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Connect to a Bluetooth LE device with a specific address
    public boolean connect(final String address) {
        try {
            if (bluetoothAdapter == null || address == null) {
                Log.w(TAG, "BluetoothAdapter not initialized or unspecified address");
                return false;
            }
            bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
            if (bluetoothDevice == null) {
                Log.w(TAG, "Unable to connect because device was not found");
                return false;
            }
            if (bluetoothGatt != null) {                                                                //See if an existing connection needs to be closed
                bluetoothGatt.close();                                                                  //Faster to create new connection than reconnect with existing BluetoothGatt
            }
            connectionAttemptCountdown = 3;                                                             //Try to connect three times for reliability
            bluetoothGatt = bluetoothDevice.connectGatt(this, false, bleGattCallback);                           //Directly connect to the device , so set autoConnect to false
            Log.d(TAG, "Attempting to create a new Bluetooth connection");
            return true;
        }
        catch (Exception e) {
            Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
            return false;
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Disconnect an existing connection or cancel a connection that has been requested
    public void disconnect() {
        try {
            if (bluetoothAdapter == null || bluetoothGatt == null) {
                Log.w(TAG, "BluetoothAdapter not initialized");
                return;
            }
            connectionAttemptCountdown = 0;                                                             //Stop counting connection attempts
            bluetoothGatt.disconnect();
        }
        catch (Exception e) {
            Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Write to the MLDP control characteristic
    public void writeControlMLDP(byte[] byteValues) {
        try {
            BluetoothGattCharacteristic writeControlCharacteristic;
            if (mldpControlCharacteristic != null)
                writeControlCharacteristic = mldpControlCharacteristic;
            else
                return;
            if (bluetoothAdapter == null || bluetoothGatt == null || writeControlCharacteristic == null) {
                Log.w(TAG, "Write attempted with Bluetooth uninitialized or not connected");
                return;
            }
            writeControlCharacteristic.setValue(byteValues);
            characteristicWriteQueue.add(writeControlCharacteristic);                                       //Put the characteristic into the write queue
            if(characteristicWriteQueue.size() == 1){                                                   //If there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
                if (!bluetoothGatt.writeCharacteristic(writeControlCharacteristic)) {                       //Request the BluetoothGatt to do the Write
                    Log.d(TAG, "Failed to write characteristic");                                       //Write request was not accepted by the BluetoothGatt
                }
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // NOT WORKING: Write to the device name characteristic
    public void writeDeviceName(byte[] byteValues) {                                                          //Write string (may need to add code to limit write to 20 bytes)
        try {
            BluetoothGattCharacteristic writeNameCharacteristic;
            Log.d(TAG, "btadpt: " + bluetoothAdapter + " | btgatt: " + bluetoothGatt + " | genericDeviceName: " + genericDeviceNameCharacteristic);
            if (genericDeviceNameCharacteristic != null) {
                writeNameCharacteristic = genericDeviceNameCharacteristic;
            }
            else {
                writeNameCharacteristic = transparentRxDataCharacteristic;
            }
            if (bluetoothAdapter == null || bluetoothGatt == null || writeNameCharacteristic == null) {
                Log.w(TAG, "Write attempted with Bluetooth uninitialized or not connected");
                return;
            }
            writeNameCharacteristic.setValue(byteValues);
            characteristicWriteQueue.add(writeNameCharacteristic);                                       //Put the characteristic into the write queue
            Log.d(TAG, "Write queue size: " + characteristicWriteQueue.size());
            if(characteristicWriteQueue.size() == 1){                                                   //If there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
                Log.d(TAG, "Will write device name characteristic");
                if (!bluetoothGatt.writeCharacteristic(writeNameCharacteristic)) {                       //Request the BluetoothGatt to do the Write
                    Log.d(TAG, "Failed to write characteristic");                                       //Write request was not accepted by the BluetoothGatt
                }
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Write to the MLDP data characteristic
    public void writeMLDP(String string) {                                                          //Write string (may need to add code to limit write to 20 bytes)
        try {
            BluetoothGattCharacteristic writeDataCharacteristic;
            Log.d(TAG, "mldpDataCharacteristic: " + mldpDataCharacteristic);
            if (mldpDataCharacteristic != null) {
                writeDataCharacteristic = mldpDataCharacteristic;
            }
            else {
                writeDataCharacteristic = transparentRxDataCharacteristic;
            }
            if (bluetoothAdapter == null || bluetoothGatt == null || writeDataCharacteristic == null) {
                Log.w(TAG, "Write attempted with Bluetooth uninitialized or not connected");
                return;
            }
            writeDataCharacteristic.setValue(string);
            characteristicWriteQueue.add(writeDataCharacteristic);                                       //Put the characteristic into the write queue
            if(characteristicWriteQueue.size() == 1){                                                   //If there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
                if (!bluetoothGatt.writeCharacteristic(writeDataCharacteristic)) {                       //Request the BluetoothGatt to do the Write
                    Log.d(TAG, "Failed to write characteristic");                                       //Write request was not accepted by the BluetoothGatt
                }
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        }
    }

    public void writeMLDP(byte[] byteValues) {                                                      //Write bytes (may need to add code to limit write to 20 bytes)
        try {
            BluetoothGattCharacteristic writeDataCharacteristic;
            if (mldpDataCharacteristic != null) {
                writeDataCharacteristic = mldpDataCharacteristic;
            }
            else {
                writeDataCharacteristic = transparentRxDataCharacteristic;
            }
            if (bluetoothAdapter == null || bluetoothGatt == null || writeDataCharacteristic == null) {
                Log.w(TAG, "Write attempted with Bluetooth uninitialized or not connected");
                return;
            }
                writeDataCharacteristic.setValue(byteValues);
                characteristicWriteQueue.add(writeDataCharacteristic);                                       //Put the characteristic into the write queue
                if (characteristicWriteQueue.size() == 1) {                                                   //If there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
                    if (!bluetoothGatt.writeCharacteristic(writeDataCharacteristic)) {                       //Request the BluetoothGatt to do the Write
                        Log.d(TAG, "Failed to write characteristic");                                       //Write request was not accepted by the BluetoothGatt
                    }
                }
        } catch (Exception e) {
            Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Device scan callback. Bluetooth adapter calls this method when a new device is discovered during a scan.
    // The callback is only called for devices with advertising packets containing a UUID in the uuidScanList[] (i.e. MLDP service).
    // The code that parses the UUID in the advertising packet is only required because the uuidScanList[] does not work for Android 4.X.
    private final BluetoothAdapter.LeScanCallback bleScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
        try {
            if (Build.VERSION.SDK_INT >= 21) { //Build.VERSION_CODES.LOLLIPOP) {
                final Intent intent = new Intent(ACTION_BLE_SCAN_RESULT);                           //Create intent to report back the scan result
                intent.putExtra(INTENT_EXTRA_SERVICE_ADDRESS, device.getAddress());                 //Get address and add to intent
                intent.putExtra(INTENT_EXTRA_SERVICE_NAME, device.getName());                       //Get name and add to intent
                sendBroadcast(intent);                                                              //Broadcast the intent
            }
            else {
                int i = 0;
                while (i < scanRecord.length - 1) {
                    if (scanRecord[i + 1] != 6 && scanRecord[i + 1] != 7) {                         //Look for complete or incomplete list of 128-bit Service Class UUIDs
                        i += scanRecord[i] + 1;                                                     //Add length of current field to the index to point to the next field
                    } else {
                        if (scanRecord[i] == 17) {                                                  //Look for length code of 17 for 1 byte identifier and 16 byte UUID
                            i += 2;                                                                 //Set index to start of data
                            if (i + 15 < scanRecord.length) {
                                for (byte b : SCAN_RECORD_MLDP_PRIVATE_SERVICE) {                   //Check that the scan record shows the MLDP service UUID
                                    if (b != scanRecord[i++]) {
                                        return;                                                     //Don't report discovered device if it does not have the MLDP service
                                    }
                                }
                                final Intent intent = new Intent(ACTION_BLE_SCAN_RESULT);           //Create intent to report back the scan result
                                intent.putExtra(INTENT_EXTRA_SERVICE_ADDRESS, device.getAddress()); //Get address and add to intent
                                intent.putExtra(INTENT_EXTRA_SERVICE_NAME, device.getName());       //Get name and add to intent
                                sendBroadcast(intent);                                              //Broadcast the intent
                            }
                        }
                        break;
                    }
                }
            }
            return;
        }
        catch (Exception e) {
            Log.e(TAG, "Oops, exception caught in " + e.getStackTrace()[0].getMethodName() + ": " + e.getMessage());
        }
        }

//        @Override
//        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
//            final Intent intent = new Intent(ACTION_BLE_SCAN_RESULT);                               //Create intent to report back the scan result
//            intent.putExtra(INTENT_EXTRA_SERVICE_ADDRESS, device.getAddress());                     //Get address and add to intent
//            intent.putExtra(INTENT_EXTRA_SERVICE_NAME, device.getName());                           //Get name and add to intent
//            sendBroadcast(intent);                                                                  //Broadcast the intent
//            return;
//        }
    };

}
