package com.unarin.cordova.beacon;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

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


}
