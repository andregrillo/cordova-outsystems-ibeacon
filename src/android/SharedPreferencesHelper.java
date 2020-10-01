package com.unarin.cordova.beacon;

import android.content.Context;
import android.content.SharedPreferences;

import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.Region;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SharedPreferencesHelper {
    SharedPreferences preferences;

    public SharedPreferencesHelper(String Name,Context context){
        preferences = context.getSharedPreferences(Name, Context.MODE_PRIVATE);
    }

    public Boolean setDeepLink(String deepLink){
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("DeepLink",deepLink);
        return editor.commit();
    }

    public String getDeepLink(){
        String deepLink = preferences.getString("DeepLink","");
        return deepLink;
    }

    public JSONArray getNotifications(String beaconId){
        String jsonString = preferences.getString(beaconId,"");
        if (jsonString.equals("")){
            return new JSONArray();
        }
        try {
            JSONArray notifications = new JSONArray(jsonString);
            return notifications;
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    public Boolean addNotification(JSONArray notification){
        try{
            String beaconId = notification.getString(0);
            JSONArray notifications = getNotifications(beaconId);
            if(notifications == null){
                notifications = new JSONArray();
            }
            if (notification == null || notification.length()<1){
                return false;
            }
            notifications.put(notification);

            Date now = new Date();
            for (int i = 0;i<notifications.length();i++) {
                JSONArray checkNotification = notifications.getJSONArray(i);
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy-HH:mm");
                    Date end = sdf.parse(checkNotification.getString(3));
                    if (end.before(now)) {
                        notifications.remove(i);
                        i--;
                    }
                }catch (ParseException | NullPointerException e){}
            }

            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(beaconId,notifications.toString());
            return editor.commit();
        }catch (JSONException e){
            return false;
        }
    }


    public Boolean removeNotifications(String beaconId){
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(beaconId);
        return editor.commit();
    }

    public Boolean setNotifications(String beaconId,JSONArray notifications){
        if(notifications.length()<1){
            removeNotifications(beaconId);
            return true;
        }
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(beaconId,notifications.toString());
        return editor.commit();
    }


    public Boolean addRegion(Region region) {
        List<Region> regions = getMonitoredRegions();
        regions.add(region);
        return setRegions(regions);
    }

    public Boolean removeRegion(Region region) {
        List<Region> regions = getMonitoredRegions();
        regions.remove(region);
        return setRegions(regions);
    }

    private Boolean setRegions(List<Region> regions) {
        SharedPreferences.Editor editor = preferences.edit();
        if(regions.size()<1){
            editor.remove("MonitoredRegions");
            return editor.commit();
        }
        JSONArray regionsJson = new JSONArray();
        try {
            for (Region region : regions) {
                JSONObject regionJson = parseJSONObject(region);
                regionsJson.put(regionJson);
            }
        }catch (JSONException e){
            regionsJson = new JSONArray();
        }

        editor.putString("MonitoredRegions",regionsJson.toString());
        return editor.commit();
    }

    public List<Region> getMonitoredRegions() {
        List<Region> regions = new ArrayList<>();
        String jsonString = preferences.getString("MonitoredRegions","");
        if (jsonString.equals("")){
            return new ArrayList<>();
        }
        try {
            JSONArray regionsJson = new JSONArray(jsonString);
            for (int i = 0;i<regionsJson.length();i++){
                JSONObject regionJson = regionsJson.getJSONObject(i);
                Region region = parseBeaconRegion(regionJson);
                regions.add(region);
            }
            return regions;
        } catch (JSONException e) {
            return new ArrayList<>();
        }
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

    private JSONObject parseJSONObject(Region region)throws JSONException{
        JSONObject regionJson = new JSONObject();
        regionJson.put("identifier",region.getUniqueId());
        regionJson.put("uuid",region.getId1().toString());
        regionJson.put("major",region.getId2().toString());
        regionJson.put("minor",region.getId3().toString());
        return regionJson;
    }
}
