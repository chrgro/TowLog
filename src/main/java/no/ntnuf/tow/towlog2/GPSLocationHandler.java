package no.ntnuf.tow.towlog2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

/**
 * Created by Christian on 22.10.2015.
 */
public class GPSLocationHandler implements LocationListener {

    LocationManager locationManager;

    public void setLocationManager(LocationManager loc) {
        locationManager = loc;
    }

    boolean noStartHeight = true;
    int startHeight;
    int endHeight;

    int num_good_fixes = 0;

    private enum TowMode{
        TAXI,
        IDLE,
        TOWING
    };

    TowMode towmode = TowMode.IDLE;

    DuringTowingActivity callback;

    public void prepareTowing(DuringTowingActivity callback) {
        Log.e("GPS", "Preparing tow");
        towmode = TowMode.TAXI;
        this.callback = callback;
    }

    public void endTowing() {
        Log.e("GPS", "Ending tow");
        towmode = TowMode.IDLE;
        noStartHeight = true;
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.e("TOW", "Location changed, altitude " + String.valueOf(location.getLatitude()) + " "+ location.toString());

        if (towmode == TowMode.TAXI) {
            if (true || location.hasAltitude() && location.hasSpeed()) {
                num_good_fixes += 1;
            }

            if (num_good_fixes > 10 || location.getSpeed() > 0) {
                towmode = TowMode.TOWING;
            }
            if (num_good_fixes > 3 || noStartHeight) {
                //startHeight = (int)location.getAltitude();
                startHeight = (int)(location.getLatitude()*10000);
                noStartHeight = false;
            }


        } else if (towmode == TowMode.TOWING) {
            if (location.hasAltitude()) {
                //endHeight = (int)location.getAltitude();
                endHeight = (int)(location.getLatitude()*10000);

                int diff = endHeight - startHeight;
                callback.updateRunningHeight(diff);
            }


        } else if (towmode == TowMode.IDLE) {
            // Do nothing
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.e("TOW", "New status");
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.e("TOW", "Provider enabled");
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.e("TOW", "Provider enabled");
    }
}
