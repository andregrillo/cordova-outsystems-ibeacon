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
import android.util.Log;

import com.outsystems.expertsmobiledev.IbeaconLusiadasSample.MainActivity;
import com.outsystems.expertsmobiledev.IbeaconLusiadasSample.R;

import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.powersave.BackgroundPowerSaver;
import org.altbeacon.beacon.startup.RegionBootstrap;
import org.altbeacon.beacon.startup.BootstrapNotifier;
import org.apache.cordova.PluginResult;

import java.util.List;

/**
 * Created by dyoung on 12/13/13.
 */
public class BeaconReferenceApplication extends Application implements BootstrapNotifier {
    private static final String TAG = "BeaconReferenceApp";
    private RegionBootstrap regionBootstrap;
    private BackgroundPowerSaver backgroundPowerSaver;
    private MainActivity mainActivity = null;
    SharedPreferencesHelper preferencesHelper;

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


        // Uncomment the code below to use a foreground service to scan for beacons. This unlocks
        // the ability to continually scan for long periods of time in the background on Andorid 8+
        // in exchange for showing an icon at the top of the screen and a always-on notification to
        // communicate to users that your app is using resources in the background.
        //

        Notification.Builder builder = new Notification.Builder(this);
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
        beaconManager.setBackgroundScanPeriod(1100);

        preferencesHelper = new SharedPreferencesHelper("NotificationsBD",getApplicationContext());


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

    }

    @Override
    public void didExitRegion(Region region) {

    }

    @Override
    public void didDetermineStateForRegion(int i, Region region) {

    }
}
