/*
    Copyright (C) 2014 Parrot SA

    Redistribution and use in source and binary forms, with or without
    modification, are permitted provided that the following conditions
    are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in
      the documentation and/or other materials provided with the 
      distribution.
    * Neither the name of Parrot nor the names
      of its contributors may be used to endorse or promote products
      derived from this software without specific prior written
      permission.

    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
    "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
    LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
    FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
    COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
    INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
    BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
    OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED 
    AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
    OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
    OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
    SUCH DAMAGE.
*/
package com.parrot.arsdk.ardiscovery;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

import com.parrot.arsdk.arsal.ARSALPrint;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.os.Handler;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;

import android.annotation.TargetApi;

public class ARDiscoveryBLEDiscoveryImpl implements ARDiscoveryBLEDiscovery
{
    private static final String TAG = ARDiscoveryBLEDiscoveryImpl.class.getSimpleName();

    private static final int ARDISCOVERY_BT_VENDOR_ID = 0x0043; /* Parrot Company ID registered by Bluetooth SIG (Bluetooth Specification v4.0 Requirement) */
    private static final int ARDISCOVERY_USB_VENDOR_ID = 0x19cf; /* official Parrot USB Vendor ID */

    private boolean bleIsAvailable;
    private BluetoothAdapter bluetoothAdapter;
    private BLEScanner bleScanner;
    private HashMap<String, ARDiscoveryDeviceService> bleDeviceServicesHmap;
    private Object leScanCallback;/*< Device scan callback. (BluetoothAdapter.LeScanCallback) */

    private IntentFilter networkStateChangedFilter;
    private BroadcastReceiver networkStateIntentReceiver;
    
    private Handler mHandler;

    private ARDiscoveryService broadcaster;
    private Context context;
    private boolean opened;
    private Boolean isLeDiscovering = false;
    private Boolean askForLeDiscovering = false;

    public ARDiscoveryBLEDiscoveryImpl()
    {
        ARSALPrint.w(TAG,"ARDiscoveryBLEDiscoveryImpl new !!!!");
        
        opened = false;
        
        bleDeviceServicesHmap = new HashMap<String, ARDiscoveryDeviceService> ();

        networkStateChangedFilter = new IntentFilter();
        networkStateChangedFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        networkStateIntentReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                ARSALPrint.d(TAG,"BroadcastReceiver onReceive");

                if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED))
                {

                    ARSALPrint.d(TAG,"ACTION_STATE_CHANGED");

                    if (bleIsAvailable)
                    {
                        int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                        switch (state)
                        {
                        case BluetoothAdapter.STATE_ON:
                            if (askForLeDiscovering)
                            {
                                bleConnect();
                                askForLeDiscovering = false;
                            }
                            break;
                        case BluetoothAdapter.STATE_TURNING_OFF:
                        
                            /* remove all BLE services */
                            bleDeviceServicesHmap.clear();
                            
                            /* broadcast the new deviceServiceList */
                            if(broadcaster != null)
                            {
                                broadcaster.broadcastDeviceServiceArrayUpdated ();
                            }
                            
                            if(isLeDiscovering)
                            {
                                askForLeDiscovering = true;
                            }

                            bleDisconnect();
                            
                            /* remove all BLE services */
                            bleDeviceServicesHmap.clear();
                            
                            /* broadcast the new deviceServiceList */
                            if(broadcaster != null)
                            {
                                broadcaster.broadcastDeviceServiceArrayUpdated ();
                            }

                            break;
                        }
                    }
                }
            }
        };
    }

    public synchronized void open(ARDiscoveryService broadcaster, Context c)
    {
        ARSALPrint.d(TAG, "Open BLE");
        this.broadcaster = broadcaster;
        this.context = c;
        if (opened)
        {
            return;
        }
        
        mHandler = new Handler();
        
        bleDeviceServicesHmap = new HashMap<String, ARDiscoveryDeviceService> ();

        bleIsAvailable = false;
        getBLEAvailability();

        if (bleIsAvailable)
        {
            initBLE();
        }

        context.registerReceiver(networkStateIntentReceiver, networkStateChangedFilter);

        opened = true;
    }

    public synchronized void close()
    {
        ARSALPrint.d(TAG, "Close BLE");
        if (! opened)
        {
            return;
        }
        
        mHandler.removeCallbacksAndMessages(null);

        context.unregisterReceiver(networkStateIntentReceiver);

        if (this.bleIsAvailable)
        {
            bleDisconnect();
        }

        this.context = null;
        this.broadcaster = null;

        opened = false;
    }

    public void update()
    {
        if ((bleIsAvailable == true) && bluetoothAdapter.isEnabled())
        {
            bleConnect();
        }
        else
        {
            bleDisconnect();
        }
    }
    
    public void start()
    {
        if (!isLeDiscovering)
        {
            if ((bleIsAvailable == true) && bluetoothAdapter.isEnabled())
            {
                bleConnect();
                isLeDiscovering = true;
            }
            else
            {
                askForLeDiscovering = true;
            }
        }
    }
    
    public void stop()
    {
        if (isLeDiscovering)
        {
            /* Stop BLE scan */
            bleDisconnect();
            isLeDiscovering = false;
        }
    }


    private void getBLEAvailability()
    {
        /* check whether BLE is supported on the device  */
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
        {
            ARSALPrint.d(TAG,"BLE Is NOT Available");
            bleIsAvailable = false;
        }
        else
        {
            ARSALPrint.d(TAG,"BLE Is Available");
            bleIsAvailable = true;
        }
    }

    @TargetApi(18)
    private void initBLE()
    {
        /* Initializes Bluetooth adapter. */
        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        bleScanner = new BLEScanner();

        leScanCallback = new BluetoothAdapter.LeScanCallback()
        {
            @Override
            public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord)
            {
                ARSALPrint.d(TAG,"onLeScan");

                bleScanner.bleCallback(device, rssi, scanRecord);
            }
        };
    }

    /* BLE */
    private void bleConnect()
    {
        if (bleIsAvailable)
        {
            bleScanner.start();
        }
    }

    private void bleDisconnect()
    {
        if (bleIsAvailable)
        {
            bleScanner.stop();
        }
    }

    @TargetApi(18)
    private class BLEScanner
    {
        private static final long ARDISCOVERY_BLE_SCAN_PERIOD = 10000;
        private static final long ARDISCOVERY_BLE_SCAN_DURATION = 4000;
        public static final long ARDISCOVERY_BLE_TIMEOUT_DURATION = ARDISCOVERY_BLE_SCAN_PERIOD + ARDISCOVERY_BLE_SCAN_DURATION+6000;
        private boolean isStart;
        private boolean scanning;
        private Handler startBLEHandler;
        private Handler stopBLEHandler;
        private Runnable startScanningRunnable;
        private Runnable stopScanningRunnable;
        private HashMap<String, ARDiscoveryDeviceService> newBLEDeviceServicesHmap;

        private static final int ARDISCOVERY_BLE_MANUFACTURER_DATA_LENGTH_OFFSET = 3;
        private static final int ARDISCOVERY_BLE_MANUFACTURER_DATA_ADTYPE_OFFSET = 4;
        private static final int ARDISCOVERY_BLE_MANUFACTURER_DATA_LENGTH_WITH_ADTYPE = 9;
        private static final int ARDISCOVERY_BLE_MANUFACTURER_DATA_ADTYPE = 0xFF;

        public BLEScanner()
        {
            ARSALPrint.d(TAG,"BLEScanningTask constructor");

            startBLEHandler = new Handler() ;
            stopBLEHandler = new Handler() ;

            startScanningRunnable = new Runnable()
            {
                @Override
                public void run()
                {
                    startScanLeDevice();
                }
            };

            stopScanningRunnable = new Runnable()
            {
                @Override
                public void run()
                {
                    periodScanLeDeviceEnd();
                }
            };
        }

        public void start()
        {
            if (! isStart)
            {
                isStart = true;
                startScanningRunnable.run();
            }

        }

        private void startScanLeDevice()
        {
            /* reset newDeviceServicesHmap */
            newBLEDeviceServicesHmap = new HashMap<String, ARDiscoveryDeviceService>();

            /* Stops scanning after a pre-defined scan duration. */
            stopBLEHandler.postDelayed( stopScanningRunnable , ARDISCOVERY_BLE_SCAN_DURATION);

            scanning = true;
            bluetoothAdapter.startLeScan((BluetoothAdapter.LeScanCallback)leScanCallback);

            /* restart scanning after a pre-defined scan period. */
            startBLEHandler.postDelayed (startScanningRunnable, ARDISCOVERY_BLE_SCAN_PERIOD);
        }

        public void bleCallback (BluetoothDevice bleService, int rssi, byte[] scanRecord)
        {
            ARSALPrint.d(TAG,"bleCallback");

            int productID = getParrotProductID (scanRecord);

            if (productID != 0)
            {
                ARDiscoveryDeviceBLEService deviceBLEService = new ARDiscoveryDeviceBLEService(bleService);
                
                deviceBLEService.setSignal(rssi);
                
                /* add the service in the array*/
                ARDiscoveryDeviceService deviceService = new ARDiscoveryDeviceService (bleService.getName(), deviceBLEService, productID);

                newBLEDeviceServicesHmap.put(deviceService.getName(), deviceService);
            }
        }

        /**
         * @brief get the parrot product id from the BLE scanRecord
         * @param scanRecord BLE scanRecord
         * @return the product ID of the parrot BLE device. return "0" if it is not a parrot device
         */
        private int getParrotProductID (byte[] scanRecord)
        {
            /* read the scanRecord  to check if it is a PARROT Delos device with the good version */

            /* scanRecord :
             * <---------------- 31 oct ------------------>
             * | AD Struct 1    | AD Struct 2 |  AD Struct n |
             * |                 \_____________________
             * | length (1 oct)    | data (length otc) |
             *                     |                     \_______________________
             *                     |AD type (n oct) | AD Data ( length - n oct) |
             *
             * for Delos:
             * AD Struct 1 : (Flags)
             * - length = 0x02
             * - AD Type = 0x01
             * - AD data :
             *
             * AD Struct 2 : (manufacturerData)
             * - length = 0x09
             * - AD Type = 0xFF
             * - AD data : | BTVendorID (2 oct) | USBVendorID (2 oct) | USBProductID (2 oct) | VersionID (2 oct) |
             */

            int parrotProductID = 0;

            final int MASK = 0xFF;

            /* get the length of the manufacturerData */
            byte[] data = (byte[]) Arrays.copyOfRange(scanRecord, ARDISCOVERY_BLE_MANUFACTURER_DATA_LENGTH_OFFSET, ARDISCOVERY_BLE_MANUFACTURER_DATA_LENGTH_OFFSET + 1);
            int manufacturerDataLenght = (MASK & data[0]);

            /* check if it is the length expected */
            if (manufacturerDataLenght == ARDISCOVERY_BLE_MANUFACTURER_DATA_LENGTH_WITH_ADTYPE)
            {
                /* get the manufacturerData */
                data = (byte[]) Arrays.copyOfRange(scanRecord, ARDISCOVERY_BLE_MANUFACTURER_DATA_ADTYPE_OFFSET , ARDISCOVERY_BLE_MANUFACTURER_DATA_ADTYPE_OFFSET + manufacturerDataLenght);
                int adType = (MASK & data[0]);

                /* check if it is the AD Type expected */
                if (adType == ARDISCOVERY_BLE_MANUFACTURER_DATA_ADTYPE)
                {
                    int btVendorID = (data[1] & MASK) + ((data[2] & MASK) << 8);
                    int usbVendorID = (data[3] & MASK) + ((data[4] & MASK) << 8);
                    int usbProductID = (data[5] & MASK) + ((data[6] & MASK) << 8);

                    /* check the vendorID, the usbVendorID end the productID */
                    if ((btVendorID == ARDISCOVERY_BT_VENDOR_ID) &&
                        (usbVendorID == ARDISCOVERY_USB_VENDOR_ID) &&
                        (usbProductID == ARDiscoveryService.getProductID(ARDISCOVERY_PRODUCT_ENUM.ARDISCOVERY_PRODUCT_MINIDRONE)) )
                    {
                        parrotProductID = usbProductID;
                    }
                }
            }

            return parrotProductID;
        }

        private void periodScanLeDeviceEnd()
        {
            ARSALPrint.d(TAG,"periodScanLeDeviceEnd");
            notificationBLEServiceDeviceUpDate (newBLEDeviceServicesHmap);
            stopScanLeDevice();
        }

        private void stopScanLeDevice()
        {
            ARSALPrint.d(TAG,"ScanLeDeviceAsyncTask stopLeScan");
            scanning = false;
            bluetoothAdapter.stopLeScan((BluetoothAdapter.LeScanCallback)leScanCallback);
        }

        public void stop()
        {
            ARSALPrint.w(TAG,"BLEScanningTask stop");

            if (leScanCallback != null)
            {
                bluetoothAdapter.stopLeScan((BluetoothAdapter.LeScanCallback)leScanCallback);
            }
            
            startBLEHandler.removeCallbacks(startScanningRunnable);
            stopBLEHandler.removeCallbacks(stopScanningRunnable);
            scanning = false;
            isStart = false;
        }

        public Boolean IsScanning()
        {
            return scanning;
        }

        public Boolean IsStart()
        {
            return isStart;
        }

    };

    @TargetApi(18)
    private void notificationBLEServiceDeviceUpDate( HashMap<String, ARDiscoveryDeviceService> newBLEDeviceServicesHmap )
    {
        ARSALPrint.d(TAG,"notificationBLEServiceDeviceAdd");
        mHandler.removeCallbacksAndMessages(null);

        /* if the BLEDeviceServices List has changed */
        if (bleServicesListHasChanged(newBLEDeviceServicesHmap))
        {
            /* get the new BLE Device Services list */
            bleDeviceServicesHmap = newBLEDeviceServicesHmap;

            /* broadcast the new deviceServiceList */
            broadcaster.broadcastDeviceServiceArrayUpdated ();
        }
        
        mHandler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                ARSALPrint.d(TAG,"BLE scan timeout ! clear BLE devices");
                bleDeviceServicesHmap.clear();
                /* broadcast the new deviceServiceList */
                broadcaster.broadcastDeviceServiceArrayUpdated();
            }
        }, BLEScanner.ARDISCOVERY_BLE_TIMEOUT_DURATION);
    }

    private boolean bleServicesListHasChanged ( HashMap<String, ARDiscoveryDeviceService> newBLEDeviceServicesHmap )
    {
        /* check is the list of BLE devices has changed */
        ARSALPrint.d(TAG,"bleServicesListHasChanged");

        boolean res = false;

        if (bleDeviceServicesHmap.size() != newBLEDeviceServicesHmap.size())
        {
            /* if the number of devices has changed */
            res = true;
        }
        else if (!bleDeviceServicesHmap.keySet().equals(newBLEDeviceServicesHmap.keySet()))
        {
            /* if the names of devices has changed */
            res = true;
        }
        else
        {
            for (ARDiscoveryDeviceService bleDevice : bleDeviceServicesHmap.values())
            {
                /* check from the MAC address */
                if (!newBLEDeviceServicesHmap.containsValue(bleDevice))
                {
                    /* if one of the old devices is not present is the new list */
                    res = true;
                }
            }
        }

        return res;
    }

    public List<ARDiscoveryDeviceService> getDeviceServicesArray()
    {
        return new ArrayList<ARDiscoveryDeviceService> (bleDeviceServicesHmap.values());
    }

}
