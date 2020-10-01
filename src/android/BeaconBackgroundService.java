package com.unarin.cordova.beacon;

import android.Manifest;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseSettings;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.outsystems.expertsmobiledev.IbeaconLusiadasSample.MainActivity;
import com.outsystems.expertsmobiledev.IbeaconLusiadasSample.R;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.BeaconTransmitter;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.powersave.BackgroundPowerSaver;
import org.altbeacon.beacon.service.ArmaRssiFilter;
import org.altbeacon.beacon.service.RangedBeacon;
import org.altbeacon.beacon.service.RunningAverageRssiFilter;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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

public class BeaconBackgroundService extends Service implements BeaconConsumer {

    private BackgroundPowerSaver backgroundPowerSaver;
    private BeaconManager iBeaconManager;
    private SharedPreferencesHelper sharedPrefHelper;
    private final IBinder mBinder = new LocalBinder();
    private HashMap<String, Region> monitoringRegions;
    private HashMap<String, Region> rangingRegions;
    Context context;

    public static final String TAG = "com.unarin.beacon";
    private static final String CHANNEL_ID = "com.unarin.cordova.beacon.notifications";

    private BeaconTransmitter beaconTransmitter;

    private boolean debugEnabled = true;
    private IBeaconServiceNotifier beaconServiceNotifier;
    private boolean bound = false;

    public boolean checkAvailability() {
        return iBeaconManager.checkAvailability();
    }

    public class LocalBinder extends Binder {
        BeaconBackgroundService getService() {
            return BeaconBackgroundService.this;
        }
    }

    public void init(Context context,int sampleExpirationMilliseconds,int foregroundBetweenScanPeriod, int foregroundScanPeriod, Boolean enableArmaFilter)  {
        this.context = context;
        sharedPrefHelper = new SharedPreferencesHelper("NotificationsBD",getApplicationContext());
        if(iBeaconManager == null) {
            iBeaconManager = BeaconManager.getInstanceForApplication(getApplicationContext());
            iBeaconManager.bind(this);

            iBeaconManager = BeaconManager.getInstanceForApplication(this);
            iBeaconManager.setForegroundBetweenScanPeriod(foregroundBetweenScanPeriod);
            iBeaconManager.setForegroundScanPeriod(foregroundScanPeriod);
            iBeaconManager.setBackgroundBetweenScanPeriod(foregroundBetweenScanPeriod*2);
            iBeaconManager.setBackgroundScanPeriod(foregroundScanPeriod);

            iBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
            //backgroundPowerSaver = new BackgroundPowerSaver(this);
            iBeaconManager.setDebug(true);

            if (enableArmaFilter) {
                iBeaconManager.setRssiFilterImplClass(ArmaRssiFilter.class);
            } else {
                iBeaconManager.setRssiFilterImplClass(RunningAverageRssiFilter.class);
                RunningAverageRssiFilter.setSampleExpirationMilliseconds(sampleExpirationMilliseconds);
            }
            RangedBeacon.setSampleExpirationMilliseconds(sampleExpirationMilliseconds);
        }
    }



    public void onCreate() {

        Log.d("BeaconBackgroundService", "BACKGROUND: Creating BackgroundBeaconService");

        super.onCreate();

        monitoringRegions = new HashMap<String, Region>();
        rangingRegions = new HashMap<String, Region>();

        debugEnabled = true;
    }

    /////////////////////////Operation Methods//////////////////////////////////////

    public PluginResult startRangingBeaconsInRegion(final Region region) {
        try {
            rangingRegions.put(region.getUniqueId(), region);
            iBeaconManager.startRangingBeaconsInRegion(region);

            if(iBeaconManager.isBackgroundModeUninitialized()) {
                iBeaconManager.setBackgroundMode(true);
            }

            PluginResult result = new PluginResult(PluginResult.Status.OK);
            result.setKeepCallback(true);
            return result;

        } catch (RemoteException e) {
            Log.e(TAG, "'startRangingBeaconsInRegion' service error: " + e.getCause());
            return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "'startRangingBeaconsInRegion' exception " + e.getCause());
            return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
        }
    }

    public PluginResult stopRangingBeaconsInRegion(final Region region) {
        if (rangingRegions.containsKey(region.getUniqueId())) {
            try {
                Region retrievedRegion = rangingRegions.get(region.getUniqueId());
                if (retrievedRegion == null){
                    retrievedRegion = region;
                }
                rangingRegions.remove(region.getUniqueId());
                iBeaconManager.stopRangingBeaconsInRegion(retrievedRegion);

                PluginResult result = new PluginResult(PluginResult.Status.OK);
                result.setKeepCallback(true);
                return result;

            } catch (RemoteException e) {
                Log.e(TAG, "'stopRangingBeaconsInRegion' service error: " + e.getCause());
                return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "'stopRangingBeaconsInRegion' exception " + e.getCause());
                return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
            }
        }else{
            return new PluginResult(PluginResult.Status.ERROR,"No Region with that id is being Ranged!");
        }
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

    public PluginResult getRangedRegions() {
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

    public PluginResult startAdvertising(final JSONArray args) throws JSONException {
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
                .setDataFields(Arrays.asList(new Long[] {0l})) // Remove this for beacon layouts without d: fields
                .build();
        debugLog("[DEBUG] Beacon.Builder: "+beacon);
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

        debugLog("[DEBUG] BeaconTransmitter: "+beaconTransmitter);
        beaconTransmitter.startAdvertising(beacon, new AdvertiseCallback() {

            @Override
            public void onStartFailure(int errorCode) {
                debugWarn("Advertisement start failed with code: "+errorCode);
            }

            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                debugWarn("startAdvertising start succeeded.");
            }
        });

        final PluginResult result = new PluginResult(PluginResult.Status.OK, false);
        result.setKeepCallback(true);
        return result;

    }

    public PluginResult stopAdvertising() {
        debugInfo("LocationManager::stopAdvertising::STOPPING...");
        final BeaconTransmitter beaconTransmitter = createOrGetBeaconTransmitter();
        beaconTransmitter.stopAdvertising();
        debugInfo("LocationManager::stopAdvertising::DONE");

        //not supported on Android
        PluginResult result = new PluginResult(PluginResult.Status.OK, "iBeacon Advertising stopped.");
        result.setKeepCallback(true);
        return result;
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

    ////////////////////// CALLBACKS ////////////////////////////

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
                            if (bound)
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
                    if(bound)
                        callbackContext.sendPluginResult(result);

                } catch (Exception e) {
                    Log.e(TAG, "'rangingBeaconsDidFailForRegion' exception " + e.getCause());
                    beaconServiceNotifier.rangingBeaconsDidFailForRegion(region, e);
                }
        });

    }

    public void createManagerCallbacks(final CallbackContext callbackContext) {
        beaconServiceNotifier = new IBeaconServiceNotifier() {

            @Override
            public void rangingBeaconsDidFailForRegion(final Region region, final Exception exception) {
                sendFailEvent("rangingBeaconsDidFailForRegion", region, exception, callbackContext);

            }

            @Override
            public void monitoringDidFailForRegion(final Region region, final Exception exception) {
                sendFailEvent("monitoringDidFailForRegionWithError", region, exception, callbackContext);
            }

            @Override
            public void didStartMonitoringForRegion(final Region region) {
                try {
                    JSONObject data = new JSONObject();
                    data.put("eventType", "didStartMonitoringForRegion");
                    data.put("region", mapOfRegion(region));

                    debugLog("didStartMonitoringForRegion: " + data.toString());

                    //send and keep reference to callback
                    PluginResult result = new PluginResult(PluginResult.Status.OK, data);
                    result.setKeepCallback(true);
                    if(bound)
                        callbackContext.sendPluginResult(result);

                } catch (Exception e) {
                    Log.e(TAG, "'startMonitoringForRegion' exception " + e.getCause());
                    monitoringDidFailForRegion(region, e);
                }
            }

            @Override
            public void didChangeAuthorizationStatus(final String status) {

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
        int access = context.checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH);
        int adminAccess = context.checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH_ADMIN);

        return (access == PackageManager.PERMISSION_GRANTED) && (adminAccess == PackageManager.PERMISSION_GRANTED);
    }

    ////////////////////////// IBeaconConsumer implementation /////////////////////
    @Override
    public void onBeaconServiceConnect() {
        debugLog("Connected to IBeacon service");
    }

    @Override
    public Context getApplicationContext() {
        return this.context.getApplicationContext();
    }

    @Override
    public void unbindService(ServiceConnection serviceConnection) {
        debugLog("Unbind from IBeacon service");
        bound = false;
        this.getApplicationContext().unbindService(serviceConnection);
    }

    @Override
    public boolean bindService(Intent intent, ServiceConnection serviceConnection, int i) {
        debugLog("Bind to IBeacon service");
        bound = true;
        return this.context.getApplicationContext().bindService(intent, serviceConnection, i);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
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

}
