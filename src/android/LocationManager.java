/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package com.unarin.cordova.beacon;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;

import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.BeaconTransmitter;
import org.altbeacon.beacon.BleNotAvailableException;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.powersave.BackgroundPowerSaver;
import org.altbeacon.beacon.service.ArmaRssiFilter;
import org.altbeacon.beacon.service.RangedBeacon;
import org.altbeacon.beacon.service.RunningAverageRssiFilter;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.InvalidKeyException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.outsystems.expertsmobiledev.IbeaconLusiadasSample.MainActivity;
import com.outsystems.expertsmobiledev.IbeaconLusiadasSample.R;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class LocationManager extends CordovaPlugin implements BeaconConsumer {

    public static final String TAG = "com.unarin.beacon";
    private static final String FOREGROUND_BETWEEN_SCAN_PERIOD_NAME = "com.unarin.cordova.beacon.android.altbeacon.ForegroundBetweenScanPeriod";
    private static final String FOREGROUND_SCAN_PERIOD_NAME = "com.unarin.cordova.beacon.android.altbeacon.ForegroundScanPeriod";
    private static final int DEFAULT_FOREGROUND_BETWEEN_SCAN_PERIOD = 5000;
    private static final String SAMPLE_EXPIRATION_MILLISECOND = "com.unarin.cordova.beacon.android.altbeacon.SampleExpirationMilliseconds";
    private static final int DEFAULT_SAMPLE_EXPIRATION_MILLISECOND = 20000;
    private static final String ENABLE_ARMA_FILTER_NAME = "com.unarin.cordova.beacon.android.altbeacon.EnableArmaFilter";
    private static final boolean DEFAULT_ENABLE_ARMA_FILTER = false;
    private static final String REQUEST_BT_PERMISSION_NAME = "com.unarin.cordova.beacon.android.altbeacon.RequestBtPermission";
    private static final boolean DEFAULT_REQUEST_BT_PERMISSION = true;
    private static final int DEFAULT_FOREGROUND_SCAN_PERIOD = 1000;
    private static int CDV_LOCATION_MANAGER_DOM_DELEGATE_TIMEOUT = 30;
    private static final int BUILD_VERSION_CODES_M = 23;
    private static final String CHANNEL_ID = "com.unarin.cordova.beacon.notifications";
    private static final String CHANNEL_NAME = "Beacon Notifications";
    private static final String CHANNEL_DESCRIPTION = "Notifications that appear when you enter or exit a designated location";

    private BlockingQueue<Runnable> queue;
    private PausableThreadPoolExecutor threadPoolExecutor;

    private SharedPreferencesHelper sharedPrefHelper;

    private boolean debugEnabled = true;
    private IBeaconServiceNotifier beaconServiceNotifier;

    //listener for changes in state for system Bluetooth service
    private BroadcastReceiver broadcastReceiver;
    private BluetoothAdapter bluetoothAdapter;

    private int sampleExpirationMilliseconds = -1;
    private int foregroundBetweenScanPeriod = -1;
    private int foregroundScanPeriod = -1;
    private boolean enableArmaFilter = false;

    private BackgroundPowerSaver backgroundPowerSaver;
    private BeaconManager iBeaconManager;
    private HashMap<String, Region> monitoringRegions;
    private HashMap<String, Region> rangingRegions;

    private BeaconTransmitter beaconTransmitter;

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null){
            Bundle extras = intent.getExtras();
            if (extras != null){
                if(extras.containsKey("DeepLinkID"))
                {
                    // extract the extra-data in the Notification
                    String deepLinkID = extras.getString("DeepLinkID");
                    if(sharedPrefHelper == null){
                        sharedPrefHelper = new SharedPreferencesHelper("NotificationsBD",cordova.getActivity().getApplicationContext());
                    }
                    sharedPrefHelper.setDeepLink(deepLinkID);
                }
            }
        }
    }


    /**
     * Constructor.
     */
    public LocationManager() {
    }


    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        final Activity cordovaActivity = cordova.getActivity();

        foregroundBetweenScanPeriod = this.preferences.getInteger(
                FOREGROUND_BETWEEN_SCAN_PERIOD_NAME, DEFAULT_FOREGROUND_BETWEEN_SCAN_PERIOD);

        foregroundScanPeriod = this.preferences.getInteger(
                FOREGROUND_SCAN_PERIOD_NAME, DEFAULT_FOREGROUND_SCAN_PERIOD);

        sampleExpirationMilliseconds = this.preferences.getInteger(
                SAMPLE_EXPIRATION_MILLISECOND, DEFAULT_SAMPLE_EXPIRATION_MILLISECOND);

        enableArmaFilter = this.preferences.getBoolean(
                ENABLE_ARMA_FILTER_NAME, DEFAULT_ENABLE_ARMA_FILTER);

        initBluetoothListener();
        initEventQueue();
        pauseEventPropagationToDom(); // Before the DOM is loaded we'll just keep collecting the events and fire them later.

        debugEnabled = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            initBluetoothAdapter();
        }

        sharedPrefHelper = new SharedPreferencesHelper("NotificationsBD",cordovaActivity.getApplicationContext());

        //TODO AddObserver when page loaded

        final boolean requestPermission = this.preferences.getBoolean(
                REQUEST_BT_PERMISSION_NAME, DEFAULT_REQUEST_BT_PERMISSION);
           
        if(requestPermission) {
            tryToRequestMarshmallowLocationPermission();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);

            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setDescription(CHANNEL_DESCRIPTION);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = cordovaActivity.getApplicationContext().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
        iBeaconManager = BeaconManager.getInstanceForApplication(cordovaActivity.getApplicationContext());
        iBeaconManager.bind(this);

        iBeaconManager.setForegroundBetweenScanPeriod(foregroundBetweenScanPeriod);
        iBeaconManager.setForegroundScanPeriod(foregroundScanPeriod);
        iBeaconManager.setBackgroundBetweenScanPeriod(foregroundBetweenScanPeriod*2);
        iBeaconManager.setBackgroundScanPeriod(foregroundScanPeriod);
        iBeaconManager.setDebug(true);

        if (enableArmaFilter) {
            iBeaconManager.setRssiFilterImplClass(ArmaRssiFilter.class);
        } else {
            iBeaconManager.setRssiFilterImplClass(RunningAverageRssiFilter.class);
            RunningAverageRssiFilter.setSampleExpirationMilliseconds(sampleExpirationMilliseconds);
        }
        RangedBeacon.setSampleExpirationMilliseconds(sampleExpirationMilliseconds);

        monitoringRegions = new HashMap<String, Region>();
        rangingRegions = new HashMap<String, Region>();
        List<Region> regions = sharedPrefHelper.getMonitoredRegions();
        for (Region region :
                regions) {
            monitoringRegions.put(region.getUniqueId(),region);
        }
    }


    //////////////// PLUGIN ENTRY POINT /////////////////////////////

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action          The action to execute.
     * @param args            JSONArray of arguments for the plugin.
     * @param callbackContext The callback id used when calling back into JavaScript.
     * @return True if the action was valid, false if not.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("onDomDelegateReady")) {
            onDomDelegateReady(callbackContext);
        } else if (action.equals("disableDebugNotifications")) {
            disableDebugNotifications(callbackContext);
        } else if (action.equals("enableDebugNotifications")) {
            enableDebugNotifications(callbackContext);
        } else if (action.equals("disableDebugLogs")) {
            disableDebugLogs(callbackContext);
        } else if (action.equals("enableDebugLogs")) {
            enableDebugLogs(callbackContext);
        } else if (action.equals("appendToDeviceLog")) {
            appendToDeviceLog(args.optString(0), callbackContext);
        } else if (action.equals("startMonitoringForRegion")) {
            startMonitoringForRegion(args.optJSONObject(0), callbackContext);
        } else if (action.equals("stopMonitoringForRegion")) {
            stopMonitoringForRegion(args.optJSONObject(0), callbackContext);
        } else if (action.equals("startRangingBeaconsInRegion")) {
            startRangingBeaconsInRegion(args.optJSONObject(0), callbackContext);
        } else if (action.equals("setNotificationMessage")) {
            setNotificationMessage(args,callbackContext);
        } else if (action.equals("removeCustomNotificationsForBeacon")) {
            removeCustomNotificationsForBeacon(args.getString(0),callbackContext);
        } else if (action.equals("getDeepLink")) {
            getDeepLink(callbackContext);
        } else if (action.equals("stopRangingBeaconsInRegion")) {
            stopRangingBeaconsInRegion(args.optJSONObject(0), callbackContext);
        } else if (action.equals("isRangingAvailable")) {
            isRangingAvailable(callbackContext);
        } else if (action.equals("getAuthorizationStatus")) {
            getAuthorizationStatus(callbackContext);
        } else if (action.equals("requestWhenInUseAuthorization")) {
            requestWhenInUseAuthorization(callbackContext);
        } else if (action.equals("requestAlwaysAuthorization")) {
            requestAlwaysAuthorization(callbackContext);
        } else if (action.equals("getMonitoredRegions")) {
            getMonitoredRegions(callbackContext);
        } else if (action.equals("getRangedRegions")) {
            getRangedRegions(callbackContext);
        } else if (action.equals("requestStateForRegion")) {
            requestStateForRegion(args.optJSONObject(0), callbackContext);
        } else if (action.equals("registerDelegateCallbackId")) {
            registerDelegateCallbackId(args.optJSONObject(0), callbackContext);
        } else if (action.equals("isMonitoringAvailableForClass")) {
            isMonitoringAvailableForClass(args.optJSONObject(0), callbackContext);
        } else if (action.equals("isAdvertisingAvailable")) {
            isAdvertisingAvailable(callbackContext);
        } else if (action.equals("isAdvertising")) {
            isAdvertising(callbackContext);
        } else if (action.equals("startAdvertising")) {
            startAdvertising(args, callbackContext);
        } else if (action.equals("stopAdvertising")) {
            stopAdvertising(callbackContext);
        } else if (action.equals("isBluetoothEnabled")) {
            isBluetoothEnabled(callbackContext);
        } else if (action.equals("enableBluetooth")) {
            enableBluetooth(callbackContext);
        } else if (action.equals("disableBluetooth")) {
            disableBluetooth(callbackContext);
        } else {
            return false;
        }
        return true;
    }

    ///////////////// SETUP AND VALIDATION /////////////////////////////////


    @TargetApi(BUILD_VERSION_CODES_M)
    private void tryToRequestMarshmallowLocationPermission() {

        if (Build.VERSION.SDK_INT < BUILD_VERSION_CODES_M) {
            Log.i(TAG, "tryToRequestMarshmallowLocationPermission() skipping because API code is " +
                    "below criteria: " + String.valueOf(Build.VERSION.SDK_INT));
            return;
        }

        final Activity activity = cordova.getActivity();

        final Method checkSelfPermissionMethod = getCheckSelfPermissionMethod();

        if (checkSelfPermissionMethod == null) {
            Log.e(TAG, "Could not obtain the method Activity.checkSelfPermission method. Will " +
                    "not check for Location permissions even though we seem to be on a " +
                    "supported version of Android.");
            return;
        }

        try {
            List<String> permissions = new ArrayList<>();
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            }
            for (int i = 0;i<permissions.size();i++){
                final Integer permissionCheckResult = (Integer) checkSelfPermissionMethod.invoke(
                        activity, permissions.get(i));

                Log.i(TAG, "Permission check result for "+permissions.get(i)+": " +
                        String.valueOf(permissionCheckResult));

                if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Permission for " + permissions.get(i) +" has already been granted.");
                    permissions.remove(i);
                    i--;

                }
            }



            final Method requestPermissionsMethod = getRequestPermissionsMethod();

            if (requestPermissionsMethod == null) {
                Log.e(TAG, "Could not obtain the method Activity.requestPermissions. Will " +
                        "not ask for Location permissions even though we seem to be on a " +
                        "supported version of Android.");
                return;
            }
            if (permissions.size()<1){
                return;
            }

            final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle("This app needs location access");
            builder.setMessage("Please grant location access so this app can detect beacons.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @SuppressLint("NewApi")
                @Override
                public void onDismiss(final DialogInterface dialog) {

                    String[] permissionsStrings = new String[permissions.size()];
                    int i = 0;
                    for (String permission : permissions){
                        permissionsStrings[i] = permission;
                        i++;
                    }


                    try {
                        requestPermissionsMethod.invoke(activity,permissionsStrings,1);
                    } catch (IllegalAccessException e) {
                        Log.e(TAG, "IllegalAccessException while requesting permission for " +
                                "Location permissions:", e);
                    } catch (InvocationTargetException e) {
                        Log.e(TAG, "InvocationTargetException while requesting permission for " +
                                "Location permissions:", e);
                    }
                }
            });

            builder.show();

        } catch (final IllegalAccessException e) {
            Log.w(TAG, "IllegalAccessException while checking for Location permissions:", e);
        } catch (final InvocationTargetException e) {
            Log.w(TAG, "InvocationTargetException while checking for Location permissions:", e);
        }
    }

    private Method getCheckSelfPermissionMethod() {
        try {
            return Activity.class.getMethod("checkSelfPermission", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    private Method getRequestPermissionsMethod() {
        try {
            final Class[] parameterTypes = {String[].class, int.class};

            return Activity.class.getMethod("requestPermissions", parameterTypes);

        } catch (Exception e) {
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void initBluetoothAdapter() {
        Activity activity = cordova.getActivity();
        BluetoothManager bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
    }

    private void pauseEventPropagationToDom() {
        checkEventQueue();
        threadPoolExecutor.pause();
    }

    private void resumeEventPropagationToDom() {
        checkEventQueue();
        threadPoolExecutor.resume();
    }

    private void initBluetoothListener() {

        //check access
        if (!hasBlueToothPermission()) {
            debugWarn("Cannot listen to Bluetooth service when BLUETOOTH permission is not added");
            return;
        }

        //check device support
        try {
            iBeaconManager.checkAvailability();
        } catch (BleNotAvailableException e) {
            //if device does not support iBeacons this error is thrown
            debugWarn("Cannot listen to Bluetooth service: " + e.getMessage());
            return;
        } catch (Exception e) {
            debugWarn("Unexpected exception checking for Bluetooth service: " + e.getMessage());
            return;
        }

        if (broadcastReceiver != null) {
            debugWarn("Already listening to Bluetooth service, not adding again");
            return;
        }

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();

                // Only listen for Bluetooth server changes
                if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {

                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    final int oldState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, BluetoothAdapter.ERROR);

                    debugLog("Bluetooth Service state changed from " + getStateDescription(oldState) + " to " + getStateDescription(state));

                    switch (state) {
                        case BluetoothAdapter.ERROR:
                            beaconServiceNotifier.didChangeAuthorizationStatus("AuthorizationStatusNotDetermined");
                            break;
                        case BluetoothAdapter.STATE_OFF:
                        case BluetoothAdapter.STATE_TURNING_OFF:
                            if (oldState == BluetoothAdapter.STATE_ON)
                                beaconServiceNotifier.didChangeAuthorizationStatus("AuthorizationStatusDenied");
                            break;
                        case BluetoothAdapter.STATE_ON:
                            beaconServiceNotifier.didChangeAuthorizationStatus("AuthorizationStatusAuthorized");
                            break;
                        case BluetoothAdapter.STATE_TURNING_ON:
                            break;
                    }
                }
            }

            private String getStateDescription(int state) {
                switch (state) {
                    case BluetoothAdapter.ERROR:
                        return "ERROR";
                    case BluetoothAdapter.STATE_OFF:
                        return "STATE_OFF";
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        return "STATE_TURNING_OFF";
                    case BluetoothAdapter.STATE_ON:
                        return "STATE_ON";
                    case BluetoothAdapter.STATE_TURNING_ON:
                        return "STATE_TURNING_ON";
                }
                return "ERROR" + state;
            }
        };

        // Register for broadcasts on BluetoothAdapter state change
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        cordova.getActivity().registerReceiver(broadcastReceiver, filter);
    }

    private void initEventQueue() {
        //queue is limited to one thread at a time
        queue = new LinkedBlockingQueue<Runnable>();
        threadPoolExecutor = new PausableThreadPoolExecutor(queue);

        //Add a timeout check
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                checkIfDomSignaldDelegateReady();
            }
        }, CDV_LOCATION_MANAGER_DOM_DELEGATE_TIMEOUT * 1000);
    }

    private void checkEventQueue() {
        if (threadPoolExecutor != null && queue != null)
            return;

        debugWarn("WARNING event queue should not be null.");
        queue = new LinkedBlockingQueue<Runnable>();
        threadPoolExecutor = new PausableThreadPoolExecutor(queue);
    }

    private void checkIfDomSignaldDelegateReady() {
        if (threadPoolExecutor != null && !threadPoolExecutor.isPaused())
            return;

        String warning = "WARNING did not receive delegate ready callback from DOM after " + CDV_LOCATION_MANAGER_DOM_DELEGATE_TIMEOUT + " seconds!";
        debugWarn(warning);

        webView.sendJavascript("console.warn('" + warning + "')");
    }

    ///////// CALLBACKS ////////////////////////////

    public void createMonitorCallbacks(final CallbackContext callbackContext) {

        //Monitor callbacks
        iBeaconManager.setMonitorNotifier(new MonitorNotifier() {
            @Override
            public void didEnterRegion(Region region) {
                displayNotificationFor(region.getUniqueId(),1);
                debugLog("didEnterRegion INSIDE for " + region.getUniqueId());
                dispatchMonitorState("didEnterRegion", MonitorNotifier.INSIDE, region, callbackContext);
            }

            @Override
            public void didExitRegion(Region region) {
                displayNotificationFor(region.getUniqueId(),-1);
                debugLog("didExitRegion OUTSIDE for " + region.getUniqueId());
                dispatchMonitorState("didExitRegion", MonitorNotifier.OUTSIDE, region, callbackContext);
            }

            @Override
            public void didDetermineStateForRegion(int state, Region region) {
                debugLog("didDetermineStateForRegion '" + nameOfRegionState(state) + "' for region: " + region.getUniqueId());
                dispatchMonitorState("didDetermineStateForRegion", state, region, callbackContext);
            }

            // Send state to JS callback until told to stop
            private void dispatchMonitorState(final String eventType, final int state, final Region region, final CallbackContext callbackContext) {

                try {
                    JSONObject data = new JSONObject();
                    data.put("eventType", eventType);
                    data.put("region", mapOfRegion(region));

                    if (eventType.equals("didDetermineStateForRegion")) {
                        String stateName = nameOfRegionState(state);
                        data.put("state", stateName);
                    }
                    //send and keep reference to callback
                    PluginResult result = new PluginResult(PluginResult.Status.OK, data);
                    result.setKeepCallback(true);
                    callbackContext.sendPluginResult(result);

                } catch (Exception e) {
                    Log.e(TAG, "'monitoringDidFailForRegion' exception " + e.getCause());
                    beaconServiceNotifier.monitoringDidFailForRegion(region, e);

                }
            }
        });

    }

    public void createRangingCallbacks(final CallbackContext callbackContext) {

        iBeaconManager.setRangeNotifier((iBeacons, region) -> {

            try {
                JSONObject data = new JSONObject();
                JSONArray beaconData = new JSONArray();
                for (Beacon beacon : iBeacons) {
                    beaconData.put(mapOfBeacon(beacon));
                }
                data.put("eventType", "didRangeBeaconsInRegion");
                data.put("region", mapOfRegion(region));
                data.put("beacons", beaconData);

                debugLog("didRangeBeacons: " + data.toString());

                //send and keep reference to callback
                PluginResult result = new PluginResult(PluginResult.Status.OK, data);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);

            } catch (Exception e) {
                Log.e(TAG, "'rangingBeaconsDidFailForRegion' exception " + e.getCause());
                beaconServiceNotifier.rangingBeaconsDidFailForRegion(region, e);
            }
        });

    }

    private void createManagerCallbacks(final CallbackContext callbackContext) {

        beaconServiceNotifier = new IBeaconServiceNotifier() {

            @Override
            public void rangingBeaconsDidFailForRegion(final Region region, final Exception exception) {
                threadPoolExecutor.execute(new Runnable() {
                    public void run() {

                        sendFailEvent("rangingBeaconsDidFailForRegion", region, exception, callbackContext);
                    }
                });
            }

            @Override
            public void monitoringDidFailForRegion(final Region region, final Exception exception) {
                threadPoolExecutor.execute(new Runnable() {
                    public void run() {

                        sendFailEvent("monitoringDidFailForRegionWithError", region, exception, callbackContext);
                    }
                });
            }

            @Override
            public void didStartMonitoringForRegion(final Region region) {
                threadPoolExecutor.execute(new Runnable() {
                    public void run() {

                        try {
                            JSONObject data = new JSONObject();
                            data.put("eventType", "didStartMonitoringForRegion");
                            data.put("region", mapOfRegion(region));

                            debugLog("didStartMonitoringForRegion: " + data.toString());

                            //send and keep reference to callback
                            PluginResult result = new PluginResult(PluginResult.Status.OK, data);
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);

                        } catch (Exception e) {
                            Log.e(TAG, "'startMonitoringForRegion' exception " + e.getCause());
                            monitoringDidFailForRegion(region, e);
                        }
                    }
                });
            }

            @Override
            public void didChangeAuthorizationStatus(final String status) {
                threadPoolExecutor.execute(new Runnable() {
                    public void run() {

                        try {
                            JSONObject data = new JSONObject();
                            data.put("eventType", "didChangeAuthorizationStatus");
                            data.put("authorizationStatus", status);
                            debugLog("didChangeAuthorizationStatus: " + data.toString());

                            //send and keep reference to callback
                            PluginResult result = new PluginResult(PluginResult.Status.OK, data);
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);

                        } catch (Exception e) {
                            callbackContext.error("didChangeAuthorizationStatus error: " + e.getMessage());
                        }
                    }
                });
            }

            private void sendFailEvent(String eventType, Region region, Exception exception, final CallbackContext callbackContext) {
                try {
                    JSONObject data = new JSONObject();
                    data.put("eventType", eventType);//not perfect mapping, but it's very unlikely to happen here
                    data.put("region", mapOfRegion(region));
                    data.put("error", exception.getMessage());

                    PluginResult result = new PluginResult(PluginResult.Status.OK, data);
                    result.setKeepCallback(true);
                    callbackContext.sendPluginResult(result);
                } catch (Exception e) {
                    //still failing, so kill all further event dispatch
                    Log.e(TAG, eventType + " error " + e.getMessage());
                    callbackContext.error(eventType + " error " + e.getMessage());
                }
            }
        };
    }

    //--------------------------------------------------------------------------
    // PLUGIN METHODS
    //--------------------------------------------------------------------------

    /*
     *  onDomDelegateReady:
     *
     *  Discussion:
     *      Called from the DOM by the LocationManager Javascript object when it's delegate has been set.
     *      This is to notify the native layer that it can start sending queued up events, like didEnterRegion,
     *      didDetermineState, etc.
     *
     *      Without this mechanism, the messages would get lost in background mode, because the native layer
     *      has no way of knowing when the consumer Javascript code will actually set it's delegate on the
     *      LocationManager of the DOM.
     */
    private void startMonitoringForRegion(JSONObject arguments, CallbackContext callbackContext) {
        _handleCallSafely(callbackContext, () -> {

            Region region = null;
            try {
                region = parseRegion(arguments);
                PluginResult result;
                BeaconReferenceApplication application = ((BeaconReferenceApplication) cordova.getActivity().getApplicationContext());
                result = application.addRegionToMonitor(region);
                monitoringRegions.put(region.getUniqueId(), region);
                //iBeaconManager.startMonitoringBeaconsInRegion(region);
                //if(iBeaconManager.isBackgroundModeUninitialized()) {
                //    iBeaconManager.setBackgroundMode(true);
                //}

                //result = new PluginResult(PluginResult.Status.OK);
                //result.setKeepCallback(true);
                beaconServiceNotifier.didStartMonitoringForRegion(region);
                return result;
            } catch (InvalidKeyException | JSONException e) {
                Log.e(TAG, "'startMonitoringForRegion' service error: " + e.getCause());
                beaconServiceNotifier.monitoringDidFailForRegion(region, e);
                return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
            }
        });
    }
    private void stopMonitoringForRegion(JSONObject arguments, CallbackContext callbackContext) {
        _handleCallSafely(callbackContext, () -> {

            Region region = null;
            try {
                region = parseRegion(arguments);
                if (monitoringRegions.containsKey(region.getUniqueId())) {

                    Region retrievedRegion = monitoringRegions.get(region.getUniqueId());
                    if (retrievedRegion == null){
                        retrievedRegion = region;
                    }

                    monitoringRegions.remove(region.getUniqueId());

                    try {
                        //iBeaconManager.stopMonitoringBeaconsInRegion(retrievedRegion);
                        BeaconReferenceApplication application = ((BeaconReferenceApplication) cordova.getActivity().getApplicationContext());
                        return application.removeRegionToMonitor(retrievedRegion);

                        //PluginResult result = new PluginResult(PluginResult.Status.OK);
                        //result.setKeepCallback(true);
                        //return result;

                    } catch (Exception e) {
                        Log.e(TAG, "'stopMonitoringForRegion' exception " + e.getCause());
                        return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                    }
                }else{
                    return new PluginResult(PluginResult.Status.ERROR, "No Region with that id is being monitored!");
                }
            } catch (InvalidKeyException | JSONException e) {
                Log.e(TAG, "'stopMonitoringForRegion' service error: " + e.getCause());
                beaconServiceNotifier.monitoringDidFailForRegion(region, e);
                return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
            }
        });
    }
    private void startRangingBeaconsInRegion(JSONObject arguments, CallbackContext callbackContext) {
        _handleCallSafely(callbackContext, () -> {

            Region region = null;
            try {
                region = parseRegion(arguments);
                rangingRegions.put(region.getUniqueId(), region);
                iBeaconManager.startRangingBeaconsInRegion(region);

                if(iBeaconManager.isBackgroundModeUninitialized()) {
                    iBeaconManager.setBackgroundMode(true);
                }

                PluginResult result = new PluginResult(PluginResult.Status.OK);
                result.setKeepCallback(true);
                return result;

            } catch (InvalidKeyException | JSONException | RemoteException e) {
                Log.e(TAG, "'startRangingBeaconsInRegion' service error: " + e.getCause());
                beaconServiceNotifier.monitoringDidFailForRegion(region, e);
                return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
            }
        });
    }
    private void stopRangingBeaconsInRegion(JSONObject arguments, CallbackContext callbackContext) {
        _handleCallSafely(callbackContext, () -> {

            Region region = null;
            try {
                region = parseRegion(arguments);
                PluginResult result;
                if(rangingRegions.containsKey(region.getUniqueId())){
                    Region retrievedRegion = rangingRegions.get(region.getUniqueId());
                    if (retrievedRegion == null){
                        retrievedRegion = region;
                    }
                    rangingRegions.remove(region.getUniqueId());
                    iBeaconManager.stopRangingBeaconsInRegion(retrievedRegion);

                    result = new PluginResult(PluginResult.Status.OK);
                    result.setKeepCallback(true);
                    return result;
                }else{
                    result = new PluginResult(PluginResult.Status.ERROR,"Could not find region in ranging regions list!");
                }
                return result;
            } catch (InvalidKeyException | JSONException | RemoteException e) {
                Log.e(TAG, "'stopRangingBeaconsInRegion' service error: " + e.getCause());
                beaconServiceNotifier.monitoringDidFailForRegion(region, e);
                return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
            }
        });
    }

    private void getMonitoredRegions(CallbackContext callbackContext) {

        _handleCallSafely(callbackContext, new ILocationManagerCommand() {

            @Override
            public PluginResult run() {
                try {
                    Collection<Region> regions = iBeaconManager.getMonitoredRegions();
                    JSONArray regionArray = new JSONArray();
                    for (Region region : regions) {
                        regionArray.put(mapOfRegion(region));
                    }

                    return new PluginResult(PluginResult.Status.OK, regionArray);
                } catch (JSONException e) {
                    debugWarn("'getRangedRegions' exception: " + e.getMessage());
                    return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                }
            }
        });
    }

    public void getRangedRegions(CallbackContext callbackContext) {
        _handleCallSafely(callbackContext, new ILocationManagerCommand() {

            @Override
            public PluginResult run() {
                try {
                    Collection<Region> regions = iBeaconManager.getRangedRegions();
                    JSONArray regionArray = new JSONArray();
                    for (Region region : regions) {
                        regionArray.put(mapOfRegion(region));
                    }

                    return new PluginResult(PluginResult.Status.OK, regionArray);
                } catch (JSONException e) {
                    debugWarn("'getRangedRegions' exception: " + e.getMessage());
                    return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                }
            }
        });
    }

    public void startAdvertising(final JSONArray args,CallbackContext callbackContext) {
        _handleCallSafely(callbackContext, new ILocationManagerCommand() {

            @Override
            public PluginResult run() {
                try {
                    debugLog("Advertisement start START BEACON ");
                    debugLog(args.toString(4));
                /*
                Advertisement start START BEACON
                    [
                        {
                            "identifier": "beaconAsMesh",
                            "uuid": "e80300fe-ff4b-0c37-5149-d9f394b5ca39",
                            "major": 0,
                            "minor": 30463,
                            "notifyEntryStateOnDisplay": true,
                            "typeName": "BeaconRegion"
                        },
                        7
                    ]
                */

                    JSONObject arguments = args.optJSONObject(0); // get first object
                    String identifier = arguments.getString("identifier");

                    //For Android, uuid can be null when scanning for all beacons (I think)
                    final String uuid = arguments.has("uuid") && !arguments.isNull("uuid") ? arguments.getString("uuid") : null;
                    final String major = arguments.has("major") && !arguments.isNull("major") ? arguments.getString("major") : null;
                    final String minor = arguments.has("minor") && !arguments.isNull("minor") ? arguments.getString("minor") : null;

                    // optinal second member in JSONArray is just a number
                    final int measuredPower = args.length() > 1 ? args.getInt(1) : -55;

                    if (major == null && minor != null)
                        throw new UnsupportedOperationException("Unsupported combination of 'major' and 'minor' parameters.");

                    debugLog("Advertisement start STEP Beacon.Builder ");

                    Beacon beacon = new Beacon.Builder()
                            .setId1(uuid) // UUID for beacon
                            .setId2(major) // Major for beacon
                            .setId3(minor) // Minor for beacon
                            .setManufacturer(0x004C) // Radius Networks.0x0118  Change this for other beacon layouts//0x004C for iPhone
                            .setTxPower(measuredPower) // Power in dB
                            .setDataFields(Arrays.asList(new Long[]{0l})) // Remove this for beacon layouts without d: fields
                            .build();
                    debugLog("[DEBUG] Beacon.Builder: " + beacon);
                /*
                Beacon beacon = new Beacon.Builder()
                        .setId1("00000000-2016-0000-0000-000000000000") // UUID for beacon
                        .setId2("5") // Major for beacon
                        .setId3("2000") // Minor for beacon
                        .setManufacturer(0x004C) // Radius Networks.0x0118  Change this for other beacon layouts//0x004C for iPhone
                        .setTxPower(-56) // Power in dB
                        .setDataFields(Arrays.asList(new Long[] {0l})) // Remove this for beacon layouts without d: fields
                        .build();
                */
                    debugLog("Advertisement start STEP BeaconParser ");

                    debugLog("Advertisement start STEP BeaconTransmitter ");
                    final BeaconTransmitter beaconTransmitter = createOrGetBeaconTransmitter();

                    debugLog("[DEBUG] BeaconTransmitter: " + beaconTransmitter);
                    beaconTransmitter.startAdvertising(beacon, new AdvertiseCallback() {

                        @Override
                        public void onStartFailure(int errorCode) {
                            debugWarn("Advertisement start failed with code: " + errorCode);
                        }

                        @Override
                        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                            debugWarn("startAdvertising start succeeded.");
                        }
                    });

                    final PluginResult result = new PluginResult(PluginResult.Status.OK, false);
                    result.setKeepCallback(true);
                    return result;
                }catch (JSONException e){
                    final PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                    return result;
                }
            }
        });
    }

    public void stopAdvertising(CallbackContext callbackContext) {
        _handleCallSafely(callbackContext, new ILocationManagerCommand() {

            @Override
            public PluginResult run() {
                debugInfo("LocationManager::stopAdvertising::STOPPING...");
                final BeaconTransmitter beaconTransmitter = createOrGetBeaconTransmitter();
                beaconTransmitter.stopAdvertising();
                debugInfo("LocationManager::stopAdvertising::DONE");

                //not supported on Android
                PluginResult result = new PluginResult(PluginResult.Status.OK, "iBeacon Advertising stopped.");
                result.setKeepCallback(true);
                return result;
            }
        });
    }

    private void onDomDelegateReady(CallbackContext callbackContext) {

        _handleCallSafely(callbackContext, () -> {
            resumeEventPropagationToDom();
            return new PluginResult(PluginResult.Status.OK);
        });
    }

    private void isBluetoothEnabled(CallbackContext callbackContext) {

        _handleCallSafely(callbackContext, () -> {
            try {
                //Check the Bluetooth service is running
                boolean available = bluetoothAdapter != null && bluetoothAdapter.isEnabled();
                return new PluginResult(PluginResult.Status.OK, available);

            } catch (Exception e) {
                debugWarn("'isBluetoothEnabled' exception " + e.getMessage());
                return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
            }
        });
    }


    private void enableBluetooth(CallbackContext callbackContext) {

        _handleCallSafely(callbackContext, () -> {
            try {
                bluetoothAdapter.enable();
                PluginResult result = new PluginResult(PluginResult.Status.OK);
                result.setKeepCallback(true);
                return result;
            } catch (Exception e) {
                Log.e(TAG, "'enableBluetooth' service error: " + e.getCause());
                return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
            }
        });
    }

    private void disableBluetooth(CallbackContext callbackContext) {

        _handleCallSafely(callbackContext, () -> {
            try {
                bluetoothAdapter.disable();
                PluginResult result = new PluginResult(PluginResult.Status.OK);
                result.setKeepCallback(true);
                return result;
            } catch (Exception e) {
                Log.e(TAG, "'disableBluetooth' service error: " + e.getCause());
                return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
            }
        });
    }

    private void disableDebugNotifications(CallbackContext callbackContext) {

        _handleCallSafely(callbackContext, () -> {
            debugEnabled = false;
            BeaconManager.setDebug(false);
            //android.bluetooth.BluetoothAdapter.DBG = false;
            return new PluginResult(PluginResult.Status.OK);
        });
    }

    private void enableDebugNotifications(CallbackContext callbackContext) {
        _handleCallSafely(callbackContext, () -> {
            debugEnabled = true;
            //android.bluetooth.BluetoothAdapter.DBG = true;
            BeaconManager.setDebug(true);
            return new PluginResult(PluginResult.Status.OK);
        });
    }


    private void disableDebugLogs(CallbackContext callbackContext) {

        _handleCallSafely(callbackContext, () -> {
            debugEnabled = false;
            BeaconManager.setDebug(false);
            //android.bluetooth.BluetoothAdapter.DBG = false;
            return new PluginResult(PluginResult.Status.OK);
        });
    }

    private void enableDebugLogs(CallbackContext callbackContext) {
        _handleCallSafely(callbackContext, () -> {
            debugEnabled = true;
            //android.bluetooth.BluetoothAdapter.DBG = true;
            BeaconManager.setDebug(true);
            return new PluginResult(PluginResult.Status.OK);
        });
    }

    private void appendToDeviceLog(final String message, CallbackContext callbackContext) {
        _handleCallSafely(callbackContext, () -> {

            if (message != null && !message.isEmpty()) {
                debugLog("[DOM] " + message);
                return new PluginResult(PluginResult.Status.OK, message);
            } else {
                return new PluginResult(PluginResult.Status.ERROR, "Log message not provided");
            }
        });
    }

    private void removeCustomNotificationsForBeacon(String beaconID, CallbackContext callbackContext) {
        _handleCallSafely(callbackContext, () -> {
            Boolean success = sharedPrefHelper.removeNotifications(beaconID);
            if(success){
                return new PluginResult(PluginResult.Status.OK);
            }else{
                return new PluginResult(PluginResult.Status.ERROR,"Could not remove Notifications from preferences");
            }
        });
    }

    private void setNotificationMessage(JSONArray arguments, CallbackContext callbackContext) {
        _handleCallSafely(callbackContext, () -> {
            Boolean success = sharedPrefHelper.addNotification(arguments);
            if (success) {
                return new PluginResult(PluginResult.Status.OK);
            }else{
                return new PluginResult(PluginResult.Status.ERROR,"Could not add Notification to Notification List for the beacon specified");
            }
        });
    }

    private void getDeepLink(CallbackContext callbackContext){
        _handleCallSafely(callbackContext, () -> {
            String deepLink = sharedPrefHelper.getDeepLink();
            sharedPrefHelper.setDeepLink("");
            if (!deepLink.equals("")) {
                return new PluginResult(PluginResult.Status.OK,deepLink);
            }else{
                return new PluginResult(PluginResult.Status.ERROR,"Could not retrieve Deeplink or it was empty!");
            }
        });
    }

    private void getAuthorizationStatus(CallbackContext callbackContext) {
        _handleCallSafely(callbackContext, new ILocationManagerCommand() {

            @Override
            public PluginResult run() {

                try {

                    //Check app has the necessary permissions
                    if (!hasBlueToothPermission()) {
                        return new PluginResult(PluginResult.Status.ERROR, "Application does not BLUETOOTH or BLUETOOTH_ADMIN permissions");
                    }

                    //Check the Bluetooth service is running
                    String authStatus = iBeaconManager.checkAvailability()
                            ? "AuthorizationStatusAuthorized" : "AuthorizationStatusDenied";
                    JSONObject result = new JSONObject();
                    result.put("authorizationStatus", authStatus);
                    return new PluginResult(PluginResult.Status.OK, result);

                } catch (BleNotAvailableException e) {
                    //if device does not support iBeacons and error is thrown
                    debugLog("'getAuthorizationStatus' Device not supported: " + e.getMessage());
                    return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                } catch (Exception e) {
                    debugWarn("'getAuthorizationStatus' exception " + e.getMessage());
                    return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                }

            }
        });
    }

    private void requestWhenInUseAuthorization(CallbackContext callbackContext) {
        _handleCallSafely(callbackContext, new ILocationManagerCommand() {

            @Override
            public PluginResult run() {
                return new PluginResult(PluginResult.Status.OK);
            }
        });
    }

    private void requestAlwaysAuthorization(CallbackContext callbackContext) {
        _handleCallSafely(callbackContext, new ILocationManagerCommand() {

            @Override
            public PluginResult run() {
                return new PluginResult(PluginResult.Status.OK);
            }
        });
    }

    //NOT IMPLEMENTED: Manually request monitoring scan for region.
    //This might not even be needed for Android as it should happen no matter what
    private void requestStateForRegion(final JSONObject arguments, CallbackContext callbackContext) {

        _handleCallSafely(callbackContext, new ILocationManagerCommand() {
            @Override
            public PluginResult run() {

                //not supported on Android
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, "Manual request for monitoring update is not supported on Android");
                result.setKeepCallback(true);
                return result;

            }
        });
    }

    private void isRangingAvailable(CallbackContext callbackContext) {

        _handleCallSafely(callbackContext, new ILocationManagerCommand() {

            @Override
            public PluginResult run() {
                try {

                    //Check the Bluetooth service is running
                    boolean available = iBeaconManager.checkAvailability();
                    return new PluginResult(PluginResult.Status.OK, available);

                } catch (BleNotAvailableException e) {
                    //if device does not support iBeacons and error is thrown
                    debugLog("'isRangingAvailable' Device not supported: " + e.getMessage());
                    return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                } catch (Exception e) {
                    debugWarn("'isRangingAvailable' exception " + e.getMessage());
                    return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                }
            }
        });
    }

    private void registerDelegateCallbackId(JSONObject arguments, final CallbackContext callbackContext) {

        _handleCallSafely(callbackContext, new ILocationManagerCommand() {

            @Override
            public PluginResult run() {
                debugLog("Registering delegate callback ID: " + callbackContext.getCallbackId());
                //delegateCallbackId = callbackContext.getCallbackId();

                createMonitorCallbacks(callbackContext);
                createRangingCallbacks(callbackContext);
                createManagerCallbacks(callbackContext);

                PluginResult result = new PluginResult(PluginResult.Status.OK);
                result.setKeepCallback(true);
                return result;
            }
        });

    }

    /*
     * Checks if the region is supported, both for type and content
     */
    private void isMonitoringAvailableForClass(final JSONObject arguments, final CallbackContext callbackContext) {
        _handleCallSafely(callbackContext, new ILocationManagerCommand() {

            @Override
            public PluginResult run() {

                boolean isValid = true;
                try {
                    parseRegion(arguments);
                } catch (Exception e) {
                    //will fail is the region is circular or some expected structure is missing
                    isValid = false;
                }

                PluginResult result = new PluginResult(PluginResult.Status.OK, isValid);
                result.setKeepCallback(true);
                return result;

            }
        });
    }

    private void isAdvertisingAvailable(CallbackContext callbackContext) {

        _handleCallSafely(callbackContext, new ILocationManagerCommand() {
            @Override
            public PluginResult run() {

                //not supported at Android yet (see Android L)
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // only for ANDROID >= 5.0
                    PluginResult result = new PluginResult(PluginResult.Status.OK, true);
                    result.setKeepCallback(true);
                    return result;
                }else{
                    PluginResult result = new PluginResult(PluginResult.Status.OK, false);
                    result.setKeepCallback(true);
                    return result;
                }

            }
        });

    }

    private void isAdvertising(CallbackContext callbackContext) {

        _handleCallSafely(callbackContext, new ILocationManagerCommand() {
            @Override
            public PluginResult run() {

                //not supported on Android
                PluginResult result = new PluginResult(PluginResult.Status.OK, false);
                result.setKeepCallback(true);
                return result;

            }
        });

    }

    /////////// SERIALISATION /////////////////////

    private Region parseRegion(JSONObject json) throws JSONException, InvalidKeyException, UnsupportedOperationException {

        if (!json.has("typeName"))
            throw new InvalidKeyException("'typeName' is missing, cannot parse Region.");

        if (!json.has("identifier"))
            throw new InvalidKeyException("'identifier' is missing, cannot parse Region.");

        String typeName = json.getString("typeName");
        if (typeName.equals("BeaconRegion")) {
            return parseBeaconRegion(json);
        } else if (typeName.equals("CircularRegion")) {
            return parseCircularRegion(json);

        } else {
            throw new UnsupportedOperationException("Unsupported region type");
        }

    }

    /* NOT SUPPORTED, a possible enhancement later */
    private Region parseCircularRegion(JSONObject json) throws JSONException, InvalidKeyException, UnsupportedOperationException {

        if (!json.has("latitude"))
            throw new InvalidKeyException("'latitude' is missing, cannot parse CircularRegion.");

        if (!json.has("longitude"))
            throw new InvalidKeyException("'longitude' is missing, cannot parse CircularRegion.");

        if (!json.has("radius"))
            throw new InvalidKeyException("'radius' is missing, cannot parse CircularRegion.");

     	/*String identifier = json.getString("identifier");
         double latitude = json.getDouble("latitude");
     	double longitude = json.getDouble("longitude");
     	double radius = json.getDouble("radius");
    	*/
        throw new UnsupportedOperationException("Circular regions are not supported at present");
    }

    private Region parseBeaconRegion(JSONObject json) throws JSONException, UnsupportedOperationException {

        String identifier = json.getString("identifier");

        //For Android, uuid can be null when scanning for all beacons (I think)
        String uuid = json.has("uuid") && !json.isNull("uuid") ? json.getString("uuid") : null;
        String major = json.has("major") && !json.isNull("major") ? json.getString("major") : null;
        String minor = json.has("minor") && !json.isNull("minor") ? json.getString("minor") : null;

        if (major == null && minor != null)
            throw new UnsupportedOperationException("Unsupported combination of 'major' and 'minor' parameters.");

        Identifier id1 = uuid != null ? Identifier.parse(uuid) : null;
        Identifier id2 = major != null ? Identifier.parse(major) : null;
        Identifier id3 = minor != null ? Identifier.parse(minor) : null;
        return new Region(identifier, id1, id2, id3);
    }


    private String nameOfRegionState(int state) {
        switch (state) {
            case MonitorNotifier.INSIDE:
                return "CLRegionStateInside";
            case MonitorNotifier.OUTSIDE:
                return "CLRegionStateOutside";
            /*case MonitorNotifier.UNKNOWN:
                return "CLRegionStateUnknown";*/
            default:
                return "ErrorUnknownCLRegionStateObjectReceived";
        }
    }

    private JSONObject mapOfRegion(Region region) throws JSONException {

        //NOTE: NOT SUPPORTING CIRCULAR REGIONS
        return mapOfBeaconRegion(region);

    }

    private JSONObject mapOfBeaconRegion(Region region) throws JSONException {
        JSONObject dict = new JSONObject();

        // identifier
        if (region.getUniqueId() != null) {
            dict.put("identifier", region.getUniqueId());
        }

        dict.put("uuid", region.getId1());

        if (region.getId2() != null) {
            dict.put("major", region.getId2());
        }

        if (region.getId3() != null) {
            dict.put("minor", region.getId3());
        }

        dict.put("typeName", "BeaconRegion");

        return dict;

    }

    /* NOT SUPPORTED */
    /*private JSONObject mapOfCircularRegion(Region region) throws JSONException {
        JSONObject dict = new JSONObject();

        // identifier
        if (region.getUniqueId() != null) {
       	 dict.put("identifier", region.getUniqueId());
       }

       //NOT SUPPORTING CIRCULAR REGIONS
       //dict.put("radius", region.getRadius());
       //JSONObject coordinates = new JSONObject();
       //coordinates.put("latitude", 0.0d);
       //coordinates.put("longitude", 0.0d);
       //dict.put("center", coordinates);
       //dict.put("typeName", "CircularRegion");

       return dict;

    }*/

    private JSONObject mapOfBeacon(Beacon region) throws JSONException {
        JSONObject dict = new JSONObject();

        //beacon id
        dict.put("uuid", region.getId1());
        dict.put("major", region.getId2());
        dict.put("minor", region.getId3());

        // proximity
        dict.put("proximity", nameOfProximity(region.getDistance()));

        // signal strength and transmission power
        dict.put("rssi", region.getRssi());
        dict.put("tx", region.getTxPower());

        // accuracy = rough distance estimate limited to two decimal places (in metres)
        // NO NOT ASSUME THIS IS ACCURATE - it is effected by radio interference and obstacles
        dict.put("accuracy", Math.round(region.getDistance() * 100.0) / 100.0);

        return dict;
    }

    private String nameOfProximity(double accuracy) {

        if (accuracy < 0) {
            return "ProximityUnknown";
            // is this correct?  does proximity only show unknown when accuracy is negative?  I have seen cases where it returns unknown when
            // accuracy is -1;
        }
        if (accuracy < 0.5) {
            return "ProximityImmediate";
        }
        // forums say 3.0 is the near/far threshold, but it looks to be based on experience that this is 4.0
        if (accuracy <= 4.0) {
            return "ProximityNear";
        }
        // if it is > 4.0 meters, call it far
        return "ProximityFar";
    }

    private boolean hasBlueToothPermission() {
        Context context = cordova.getActivity();
        int access = context.checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH);
        int adminAccess = context.checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH_ADMIN);

        return (access == PackageManager.PERMISSION_GRANTED) && (adminAccess == PackageManager.PERMISSION_GRANTED);
    }

    //////// Async Task Handling ////////////////////////////////

    private void _handleCallSafely(CallbackContext callbackContext, final ILocationManagerCommand task) {
        _handleCallSafely(callbackContext, task, true);
    }

    private void _handleCallSafely(final CallbackContext callbackContext, final ILocationManagerCommand task, boolean runInBackground) {
        if (runInBackground) {
            new AsyncTask<Void, Void, Void>() {

                @Override
                protected Void doInBackground(final Void... params) {

                    try {
                        _sendResultOfCommand(callbackContext, task.run());
                    } catch (Exception ex) {
                        _handleExceptionOfCommand(callbackContext, ex);
                    }
                    return null;
                }

            }.execute();
        } else {
            try {
                _sendResultOfCommand(callbackContext, task.run());
            } catch (Exception ex) {
                _handleExceptionOfCommand(callbackContext, ex);
            }
        }
    }

    private void _handleExceptionOfCommand(CallbackContext callbackContext, Exception exception) {

        Log.e(TAG, "Uncaught exception: " + exception.getMessage());
        final StackTraceElement[] stackTrace = exception.getStackTrace();
        final String stackTraceElementsAsString = Arrays.toString(stackTrace);
        Log.e(TAG, "Stack trace: " + stackTraceElementsAsString);

        // When calling without a callback from the client side the command can be null.
        if (callbackContext == null) {
            return;
        }

        callbackContext.error(exception.getMessage());
    }

    private void _sendResultOfCommand(CallbackContext callbackContext, PluginResult pluginResult) {

        //debugLog("Send result: " + pluginResult.getMessage());
        if (pluginResult.getStatus() != PluginResult.Status.OK.ordinal())
            debugWarn("WARNING: " + PluginResult.StatusMessages[pluginResult.getStatus()]);

        // When calling without a callback from the client side the command can be null.
        if (callbackContext == null) {
            return;
        }

        callbackContext.sendPluginResult(pluginResult);
    }

    private void debugInfo(String message) {
        if (debugEnabled) {
            Log.i(TAG, message);
        }
    }
    private void debugLog(String message) {
        if (debugEnabled) {
            Log.d(TAG, message);
        }
    }

    private void debugWarn(String message) {
        if (debugEnabled) {
            Log.w(TAG, message);
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        BeaconReferenceApplication application = ((BeaconReferenceApplication) cordova.getActivity().getApplicationContext());
        application.setMainActivity((MainActivity) cordova.getActivity());
    }

    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
        ((BeaconReferenceApplication) cordova.getActivity().getApplicationContext()).setMainActivity(null);
    }
    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        Boolean notGranted = false;
        for(int i = 0;i<permissions.length;i++){
            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, permissions[i]+" granted");
            } else {
                notGranted = true;
            }
        }
        if (notGranted) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(cordova.getActivity());
            builder.setTitle("Functionality limited");
            builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons at all times.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                @Override
                public void onDismiss(DialogInterface dialog) {
                }

            });
            builder.show();
        }
    }

    @Override
    public void onBeaconServiceConnect() {

    }

    @Override
    public Context getApplicationContext() {
        return cordova.getActivity().getApplicationContext();
    }

    @Override
    public void unbindService(ServiceConnection serviceConnection) {
        debugLog("Unbind from IBeacon service");
        this.getApplicationContext().unbindService(serviceConnection);
    }

    @Override
    public boolean bindService(Intent intent, ServiceConnection serviceConnection, int i) {
        debugLog("Bind to IBeacon service");
        return getApplicationContext().bindService(intent, serviceConnection, i);
    }
    public PluginResult getMonitoredRegions() {
        try {
            Collection<Region> regions = iBeaconManager.getMonitoredRegions();
            JSONArray regionArray = new JSONArray();
            for (Region region : regions) {
                regionArray.put(mapOfRegion(region));
            }

            return new PluginResult(PluginResult.Status.OK, regionArray);
        } catch (JSONException e) {
            debugWarn("'getMonitoredRegions' exception: " + e.getMessage());
            return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
        }
    }
    ///////////////// SETUP AND VALIDATION /////////////////////////////////

    private BeaconTransmitter createOrGetBeaconTransmitter() {
        if (this.beaconTransmitter == null) {
            final BeaconParser beaconParser = new BeaconParser()
                    .setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24");

            this.beaconTransmitter = new BeaconTransmitter(getApplicationContext(), beaconParser);
        }
        return this.beaconTransmitter;
    }

    private Boolean displayNotificationFor(String beaconId,int state){
        JSONArray notifications = sharedPrefHelper.getNotifications(beaconId);
        try {
            Date now = new Date();
            List<Integer> toRemove = new ArrayList<>();
            for (int i = 0; i < notifications.length(); i++) {
                JSONArray notification = notifications.getJSONArray(i);
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy-HH:mm");
                    Date start = sdf.parse(notification.getString(2));
                    Date end = sdf.parse(notification.getString(3));

                    Boolean sent = false;
                    if(end.after(now)) {
                        if (start.before(now)) {
                            if (state > 0) {
                                //Enter
                                sent = sendNotification(notification.getString(4), notification.getString(5), notification.getString(1));
                            } else {
                                sent = sendNotification(notification.getString(6), notification.getString(7), notification.getString(1));
                                //Exit
                            }
                        }
                    }else{
                        sent = true;
                    }

                    if (sent){
                        toRemove.add(i-toRemove.size());
                    }
                }catch (ParseException | NullPointerException e){
                    debugLog(e.getMessage());
                }
            }
            for (int id : toRemove){
                notifications.remove(id);
            }
            sharedPrefHelper.setNotifications(beaconId,notifications);
            return true;
        }catch(JSONException e){
            return false;
        }
    }

    private Boolean sendNotification(String title, String message, String deepLink){
        if (message.equals("disabled")){
            return false;
        }
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("DeepLinkID",deepLink);
        PendingIntent notifyPendingIntent = PendingIntent.getActivity(
                getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext(),CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher) // notification icon
                //.setExtras()//Add DeeplinkID
                .setContentIntent(notifyPendingIntent)
                .setAutoCancel(true); // clear notification when clicked
        if(!title.equals("")){
            mBuilder.setContentTitle(title);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            mBuilder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        }
        mBuilder.setContentText(message);
        mBuilder.setStyle(new NotificationCompat.BigTextStyle()
                .bigText(message));
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());

        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(new Random().nextInt(100), mBuilder.build());//Figure out what is notificationID
        return true;
    }

}
