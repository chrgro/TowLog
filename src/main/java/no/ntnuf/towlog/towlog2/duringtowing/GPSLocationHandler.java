package no.ntnuf.towlog.towlog2.duringtowing;

import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.Arrays;

/**
 * GPS Location handler. Manages the Towing state, and GPS location callbacks.
 */
public class GPSLocationHandler implements LocationListener {

    LocationManager locationManager;

    private float TOWING_SPEED_THRESHOLD;
    private int NUM_GOOD_FIXES_NEEDED;
    private float ACCURACY_THRESHOLD;

    private int num_good_fixes = 0;

    private double[] altitude_history = new double[NUM_GOOD_FIXES_NEEDED];

    double max_tow_height = -1.0;

    private boolean debugMode = false;

    private Location lastlocation;

    public void setLocationManager(LocationManager loc) {
        locationManager = loc;
    }

    private enum TowMode{
        TAXI,
        IDLE,
        TOWING
    }

    private TowMode towmode = TowMode.IDLE;

    private DuringTowingActivity callback;

    public void prepareTowing(DuringTowingActivity callback, SharedPreferences settings) {
        Log.e("GPS", "Preparing tow");
        towmode = TowMode.TAXI;
        this.callback = callback;
        for (int i = 0; i < NUM_GOOD_FIXES_NEEDED; i++) {
            altitude_history[i] = 10000000; // High enough
        }
        lastlocation = new Location("no gps signal yet");

        TOWING_SPEED_THRESHOLD = Float.valueOf(settings.getString("towing_speed_threshold", "8.0f")); // meters/second
        NUM_GOOD_FIXES_NEEDED = Integer.valueOf(settings.getString("gps_good_fixes_needed", "10"));
        ACCURACY_THRESHOLD = Float.valueOf(settings.getString("gps_accuracy_threshold", "40.0f"));
    }

    public void endTowing() {
        Log.e("GPS", "Ending tow");
        towmode = TowMode.IDLE;
    }

    // Toggle the tow mode for debug purposes
    public void forceToggleTowMode() {
        towmode = towmode == TowMode.IDLE ? TowMode.TAXI :
                  towmode == TowMode.TAXI ? TowMode.TOWING :
                                            TowMode.IDLE;

        onLocationChanged(lastlocation);
    }


    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    @Override
    public void onLocationChanged(Location location) {

        lastlocation = location;

        double height = location.getAltitude();

        float speed = location.getSpeed();//getSpeedMS(location);

        if (debugMode) {
            String info = location.getAltitude() + " m Alt, " +
                    location.getAccuracy() + "m acc, " +
                    location.getSpeed() + "m/s, ";

            callback.updateDebugInfo(info);
        }

        // Track the lowest N number of altitude fixes
        // Keep tracking these until we've towed 100 meters
        boolean above_minimum = altitude_history[NUM_GOOD_FIXES_NEEDED-1] <= height;
        boolean good_fix = location.hasAltitude() && location.getAccuracy() < ACCURACY_THRESHOLD;

        if (!above_minimum && good_fix) {
            // Find the max that is higher than height
            for (int i = NUM_GOOD_FIXES_NEEDED-1; i >= 0; i--) {
                if (altitude_history[i] > height) {
                    altitude_history[i] = height;
                    break;
                }
            }
            Arrays.sort(altitude_history);
        }

        if (good_fix) {
            num_good_fixes += 1;
        }

        // Statemachine to tell if we're towing or not (based on speed)
        if (towmode == TowMode.TAXI) {

            // Go to towing state when speed is high enough and fixes are aquired
            if (num_good_fixes >= NUM_GOOD_FIXES_NEEDED && speed >= TOWING_SPEED_THRESHOLD) {
                towmode = TowMode.TOWING;
            }

            // Show MSL while taxiing
            callback.updateTaxiHeight((int)height, (int)speed);

        } else if (towmode == TowMode.TOWING) {
            // Track the highest height difference seen, and display that
            if (location.hasAltitude()) {
                double towheight = height - altitude_history[NUM_GOOD_FIXES_NEEDED-1];
                if (towheight > max_tow_height) {
                    max_tow_height = towheight;
                }
            }
            callback.updateRunningHeight((int) max_tow_height);


        } else if (towmode == TowMode.IDLE) {
            // Do nothing
        }
        /*
        Log.e("TOW", "Location changed: "+ location.toString());
        String s = "";
        for (int i = 0; i < NUM_GOOD_FIXES_NEEDED; i++) {
            s += altitude_history[i] + " ";
        }
        Log.e("TOW", "Previous heights: "+ s);
        */
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        //Log.e("TOW", "New status");
    }

    @Override
    public void onProviderEnabled(String provider) {
        //Log.e("TOW", "Provider enabled");
    }

    @Override
    public void onProviderDisabled(String provider) {
        //Log.e("TOW", "Provider enabled");
    }
}
