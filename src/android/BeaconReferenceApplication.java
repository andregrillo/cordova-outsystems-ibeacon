package com.unarin.cordova.beacon;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;

import android.os.Build;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import $appid.MainActivity;
import $appid.R;

import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.powersave.BackgroundPowerSaver;
import org.altbeacon.beacon.startup.RegionBootstrap;
import org.altbeacon.beacon.startup.BootstrapNotifier;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * Created by dyoung on 12/13/13.
 */
public class BeaconReferenceApplication extends Application implements BootstrapNotifier {
    private static final String TAG = "BeaconReferenceApp";
    private RegionBootstrap regionBootstrap;
    private BackgroundPowerSaver backgroundPowerSaver;
    private MainActivity mainActivity = null;
    SharedPreferencesHelper preferencesHelper;
    private boolean debugEnabled;
    private CallbackContext callbackContext;
    private List<PluginResult> results;

    public void onCreate() {
        super.onCreate();
        BeaconManager beaconManager = org.altbeacon.beacon.BeaconManager.getInstanceForApplication(this);

        // By default the AndroidBeaconLibrary will only find AltBeacons.  If you wish to make it
        // find a different type of beacon, you must specify the byte layout for that beacon's
        // advertisement with a line like below.  The example shows how to find a beacon with the
        // same byte layout as AltBeacon but with a beaconTypeCode of 0xaabb.  To find the proper
        // layout expression for other beacon types, do a web search for "setBeaconLayout"
        // including the quotes.
        //
        //beaconManager.getBeaconParsers().clear();
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

        beaconManager.setDebug(true);
        debugEnabled = true;
        results= new ArrayList<>();


        // Uncomment the code below to use a foreground service to scan for beacons. This unlocks
        // the ability to continually scan for long periods of time in the background on Andorid 8+
        // in exchange for showing an icon at the top of the screen and a always-on notification to
        // communicate to users that your app is using resources in the background.
        //

        /*Notification.Builder builder = new Notification.Builder(this);
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setContentTitle("Scanning for Beacons");
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT
        );
        builder.setContentIntent(pendingIntent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("My Notification Channel ID",
                    "My Notification Name", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("My Notification Channel Description");
            NotificationManager notificationManager = (NotificationManager) getSystemService(
                    Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
            builder.setChannelId(channel.getId());
        }
        beaconManager.enableForegroundServiceScanning(builder.build(), 456);
        // For the above foreground scanning service to be useful, you need to disable
        // JobScheduler-based scans (used on Android 8+) and set a fast background scan
        // cycle that would otherwise be disallowed by the operating system.
        //
        beaconManager.setEnableScheduledScanJobs(false);
        beaconManager.setBackgroundBetweenScanPeriod(0);
        beaconManager.setBackgroundScanPeriod(1100);*/

        preferencesHelper = new SharedPreferencesHelper(getPackageName()+"_NotificationsBD",getApplicationContext());


        Log.d(TAG, "setting up background monitoring for beacons and power saving");
        // wake up the app when a beacon is seen
        enableMonitoring();

        // simply constructing this class and holding a reference to it in your custom Application
        // class will automatically cause the BeaconLibrary to save battery whenever the application
        // is not visible.  This reduces bluetooth power usage by about 60%
        backgroundPowerSaver = new BackgroundPowerSaver(this);

        // If you wish to test beacon detection in the Android Emulator, you can use code like this:
        // BeaconManager.setBeaconSimulator(new TimedBeaconSimulator() );
        // ((TimedBeaconSimulator) BeaconManager.getBeaconSimulator()).createTimedSimulatedBeacons();
    }

    public void disableMonitoring() {
        if (regionBootstrap != null) {
            regionBootstrap.disable();
            regionBootstrap = null;
        }
    }
    public void enableMonitoring() {
        Region region = new Region("backgroundRegion",
                null, null, null);
        regionBootstrap = new RegionBootstrap(this, region);
        List<Region> regions = preferencesHelper.getMonitoredRegions();

        for (Region region1 : regions){
            addRegionToMonitor(region1);
        }
    }

    public PluginResult addRegionToMonitor(Region region) {
        preferencesHelper.addRegion(region);
        regionBootstrap.addRegion(region);
        PluginResult result = new PluginResult(PluginResult.Status.OK);
        result.setKeepCallback(true);
        return result;
    }

    public PluginResult removeRegionToMonitor(Region region) {

        preferencesHelper.removeRegion(region);
        regionBootstrap.removeRegion(region);
        PluginResult result = new PluginResult(PluginResult.Status.OK);
        result.setKeepCallback(true);
        return result;
    }

    private void sendNotification() {
        NotificationManager notificationManager =
                (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("Beacon Reference Notifications",
                    "Beacon Reference Notifications", NotificationManager.IMPORTANCE_HIGH);
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
            builder = new Notification.Builder(this, channel.getId());
        }
        else {
            builder = new Notification.Builder(this);
            builder.setPriority(Notification.PRIORITY_HIGH);
        }

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addNextIntent(new Intent(this, MainActivity.class));
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setContentTitle("I detect a beacon");
        builder.setContentText("Tap here to see details in the reference app");
        builder.setContentIntent(resultPendingIntent);
        notificationManager.notify(1, builder.build());
    }

    public void setMainActivity(MainActivity activity) {
        this.mainActivity = activity;
    }

    @Override
    public void didEnterRegion(Region region) {
        displayNotificationFor(region.getUniqueId(),1);
        debugLog("didEnterRegion INSIDE for " + region.getUniqueId());
        dispatchMonitorState("didEnterRegion", MonitorNotifier.INSIDE, region);
    }

    @Override
    public void didExitRegion(Region region) {
        displayNotificationFor(region.getUniqueId(),-1);
        debugLog("didExitRegion Outside for " + region.getUniqueId());
        dispatchMonitorState("didEnterRegion", MonitorNotifier.OUTSIDE, region);
    }

    @Override
    public void didDetermineStateForRegion(int state, Region region) {
        debugLog("didDetermineStateForRegion '" + nameOfRegionState(state) + "' for region: " + region.getUniqueId());
        dispatchMonitorState("didDetermineStateForRegion", state, region);
    }
    private void dispatchMonitorState(final String eventType, final int state, final Region region) {

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
            if (callbackContext == null){
                results.add(result);
            }else {
                callbackContext.sendPluginResult(result);
            }

        } catch (Exception e) {
            Log.e(TAG, "'monitoringDidFailForRegion' exception " + e.getCause());
            sendFailEvent("monitoringDidFailForRegionWithError", region, e);

        }
    }

    private void sendFailEvent(String eventType, Region region, Exception exception) {
        try {
            JSONObject data = new JSONObject();
            data.put("eventType", eventType);//not perfect mapping, but it's very unlikely to happen here
            data.put("region", mapOfRegion(region));
            data.put("error", exception.getMessage());

            PluginResult result = new PluginResult(PluginResult.Status.OK, data);
            result.setKeepCallback(true);
            if (callbackContext == null){
                results.add(result);
            }else {
                callbackContext.sendPluginResult(result);
            }
        } catch (Exception e) {
            //still failing, so kill all further event dispatch
            Log.e(TAG, eventType + " error " + e.getMessage());
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, eventType + " error " + e.getMessage());
            if (callbackContext == null){
                results.add(result);
            }else {
                callbackContext.sendPluginResult(result);
            }
        }
    }

    public void createCallback(CallbackContext callbackContext){
        this.callbackContext = callbackContext;
        for (PluginResult result : results){
            callbackContext.sendPluginResult(result);
        }
        results.clear();
    }


    private void debugLog(String message) {
        if (debugEnabled) {
            Log.d(TAG, message);
        }
    }
    private JSONObject mapOfRegion(Region region) throws JSONException {
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

    private Boolean displayNotificationFor(String beaconId,int state){
        JSONArray notifications = preferencesHelper.getNotifications(beaconId);
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
            preferencesHelper.setNotifications(beaconId,notifications);
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
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext(),"com.unarin.cordova.beacon.notifications")
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