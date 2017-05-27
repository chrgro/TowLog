package no.ntnuf.towlog.towlog2.duringtowing;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Date;

import no.ntnuf.tow.towlog2.R;
import no.ntnuf.towlog.towlog2.common.ColoringUtil;
import no.ntnuf.towlog.towlog2.common.TowEntry;

public class DuringTowingActivity extends AppCompatActivity {

    private int adjustedHeight;
    private int runningHeight;

    private TowEntry towentry;

    private TextView pilotnameTextView;
    private TextView registrationTextView;

    private TextView infoTextView;
    private TextView towHeightView;

    private Button incHeightButton;
    private Button decHeightButton;

    private Button abortTowButton;
    private Button releaseButton;
    private Button confirmTowButton;

    private Toolbar toolbar;

    private GPSLocationHandler gpslocation;
    private LocationManager locationManager;

    private GPXGenerator gpxgenerator;

    private final Handler handler = new Handler();

    private Context context;

    private final int GPS_REFRESH_RATE = 2000;

    private SeekBar seekBarLock;

    private int forceToggleTowModeCounter = 0;
    private int forceDebugModeCounter = 0;
    private Date clickTime;

    private SharedPreferences settings;

    private int towing_altitude_increments;
    private int towing_round_up_limit;


    private int calculateHeight() {
        return runningHeight;
    }

    // This is called by GPS location handler to update the height during pre-tow
    public void updateTaxiHeight(int height, final int auxdata) {
        final int lockedheight = height;
        final int lockedauxdata = auxdata;
        handler.post(new Runnable() {
            public void run() {
                //infoTextView.setText("Tow when ready, " + String.valueOf(lockedheight) + "m MSL");
                infoTextView.setText("Ready, " + String.valueOf(lockedheight) + "m MSL, "+String.valueOf(lockedauxdata)+" m/s");
            }
        });
    }

    // This is called by GPS location handler to update the height during towing
    public void updateRunningHeight(int towheight) {
        final int lockedheight = towheight;
        runningHeight = towheight;
        handler.post(new Runnable() {
            public void run() {
                towHeightView.setText(String.valueOf(lockedheight + "m"));
                infoTextView.setText("Towing, max height");
            }
        });
        if (towentry.towStarted == null) {
            towentry.towStarted = new Date();
        }
    }

    // Print debug info from GPS handler
    public void updateDebugInfo(String info) {
        final String lockedinfo = info;
        handler.post(new Runnable() {
            public void run() {
                registrationTextView.setText(lockedinfo);
            }
        });
    }

    // This is called by self to round final height
    private void updateFinishedHeight(int offset) {
        // Round to nearest 100, round up if larger than 35m
        int subhundred = (adjustedHeight % towing_altitude_increments);
        int rounded = (adjustedHeight / towing_altitude_increments) * towing_altitude_increments;
        if (subhundred > towing_round_up_limit) {
            rounded += towing_altitude_increments;
        }

        adjustedHeight = rounded;

        adjustedHeight += offset;

        if (adjustedHeight < 0) {
            adjustedHeight = 0;
        }

        if (adjustedHeight == 0) {
            // Alternatively print something else here, for 0 meter tows?
            towHeightView.setText(String.valueOf(adjustedHeight + "m"));
        } else {
            towHeightView.setText(String.valueOf(adjustedHeight + "m"));
        }

        if (towentry.towStarted == null) {
            towentry.towStarted = new Date();
        }
    }

    private void lockButtons(boolean lock) {
        abortTowButton.setEnabled(!lock);

        if (lock) {
            // Locked buttons
            ColoringUtil.colorMe(releaseButton, getResources().getColor(R.color.release_button_disabled));
            releaseButton.setEnabled(false);
            releaseButton.setText("Slide to unlock");

            ColoringUtil.colorMe(abortTowButton, getResources().getColor(R.color.release_button_disabled));
        } else {
            // Unlocked buttons
            ColoringUtil.colorMe(releaseButton, getResources().getColor(R.color.release_button));
            releaseButton.setEnabled(true);
            releaseButton.setText("Release");

            ColoringUtil.colorMe(abortTowButton, getResources().getColor(R.color.abort_button));

            // Hide lockbar after unlocking
            seekBarLock.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Set fullscreen to disable the notification bar
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        super.onCreate(savedInstanceState);

        settings = PreferenceManager.getDefaultSharedPreferences(this);
        towing_altitude_increments = Integer.valueOf(settings.getString("towing_altitude_increments", "100"));
        towing_round_up_limit = Integer.valueOf(settings.getString("towing_round_up_limit", "35"));


        setContentView(R.layout.activity_during_towing);

        context = this;

        toolbar = (Toolbar) findViewById(R.id.toolbarduringtowing);
        setSupportActionBar(toolbar);

        // Registration name. Can be clicked to show GPS debug info.
        // Click 6 times within 4 seconds to activate
        registrationTextView = (TextView) findViewById(R.id.duringtowingregistration);
        registrationTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                forceDebugModeCounter++;
                Date now = new Date();
                if (clickTime == null || now.getTime() - clickTime.getTime() > 4000) {
                    clickTime = now;
                    forceDebugModeCounter = 0;
                } else {
                    if (forceDebugModeCounter >= 8) {
                        gpslocation.setDebugMode(true);
                    }
                }
            }
        });

        // Pilot name. Can be clicked to force various towing modes (debug)
        // Click 6 times within 4 seconds to activate
        pilotnameTextView = (TextView) findViewById(R.id.duringtowingpilot);
        pilotnameTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                forceToggleTowModeCounter++;
                Date now = new Date();
                if (clickTime == null || now.getTime() - clickTime.getTime() > 4000) {
                    clickTime = now;
                    forceToggleTowModeCounter = 0;
                } else {
                    if (forceToggleTowModeCounter >= 6) {
                        gpslocation.forceToggleTowMode();
                        forceToggleTowModeCounter = 0;
                    }
                }
            }
        });

        infoTextView = (TextView) findViewById(R.id.infoTowText);
        towHeightView = (TextView) findViewById(R.id.currentHeight);

        seekBarLock = (SeekBar) findViewById(R.id.duringtowing_seekbarlock);

        // Hide +- buttons by default
        // Manually increment height value
        incHeightButton = (Button) findViewById(R.id.incHeightButton);
        incHeightButton.setVisibility(View.INVISIBLE);
        incHeightButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                updateFinishedHeight(+towing_altitude_increments);

            }
        });

        // Manually decrement height value
        decHeightButton = (Button) findViewById(R.id.decHeightButton);
        decHeightButton.setVisibility(View.INVISIBLE);
        decHeightButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                updateFinishedHeight(-towing_altitude_increments);
            }
        });

        // Button to release tow
        releaseButton = (Button) findViewById(R.id.releaseButton);
        ColoringUtil.colorMe(releaseButton, getResources().getColor(R.color.release_button));
        releaseButton.setTextColor(getResources().getColor(R.color.white));
        releaseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                towentry.height = calculateHeight();
                adjustedHeight = towentry.height;

                // Enable +- buttons
                incHeightButton.setVisibility(View.VISIBLE);
                decHeightButton.setVisibility(View.VISIBLE);

                // Gray release button
                ColoringUtil.colorMe(releaseButton, getResources().getColor(R.color.release_button_disabled));
                releaseButton.setEnabled(false);
                releaseButton.setText("Released");

                gpslocation.endTowing();

                confirmTowButton.setVisibility(View.VISIBLE);

                infoTextView.setText("GPS tow height " + String.valueOf(adjustedHeight) + "m");
                updateFinishedHeight(0);

            }
        });

        // Button to abort/go back
        abortTowButton = (Button) findViewById(R.id.abortTowButton);
        ColoringUtil.colorMe(abortTowButton, getResources().getColor(R.color.abort_button));
        abortTowButton.setTextColor(getResources().getColor(R.color.white));
        abortTowButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(context)
                        .setMessage("Are you sure you want to exit?")
                        .setCancelable(false)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                finish();
                            }
                        })
                        .setNegativeButton("No", null)
                        .show();
            }
        });

        // Confirm button shown after towing is completed
        confirmTowButton = (Button) findViewById(R.id.confirmTowButton);
        confirmTowButton.setVisibility(View.INVISIBLE);
        ColoringUtil.colorMe(confirmTowButton, getResources().getColor(R.color.confirm_button));
        confirmTowButton.setTextColor(getResources().getColor(R.color.white));
        confirmTowButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Build response Intent
                Intent response = new Intent();
                Bundle bundle = new Bundle();

                towentry.height = adjustedHeight;
                if (gpxgenerator.isEnabled()) {
                    towentry.gpx_body = gpxgenerator.getTrack();
                    Log.e("DURINGTOW", "Finished tow GPX track with "+gpxgenerator.getNumPoints() +" points");
                }
                bundle.putSerializable("value", towentry);
                response.putExtras(bundle);
                setResult(Activity.RESULT_OK, response);

                // End the activity
                myfinish();
            }
        });

        // Screen lock using slide bar
        seekBarLock.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress == 100) {
                    lockButtons(false);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.setProgress(0);
            }
        });


        // Load the incoming tow info (name, registration etc)
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        towentry = (TowEntry) bundle.getSerializable("value");

        // Update the textviews
        pilotnameTextView.setText(towentry.pilot.name);
        registrationTextView.setText(towentry.registration);

        // Set up GPS track to GPX logging
        gpxgenerator = new GPXGenerator(settings.getBoolean("tow_tracking_enabled", true));

        // GPS Init
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        gpslocation = new GPSLocationHandler();

        gpslocation.prepareTowing(this, this.settings);
        gpslocation.setLocationManager(locationManager);
        gpslocation.setGPXGenerator(gpxgenerator);

        // GPS location updates are requested in onResume

        lockButtons(true);
    }

    private void myfinish() {
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_REFRESH_RATE, 0, gpslocation);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.e("DURINGTOW", "onPause() called.");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.e("DURINGTOW", "onStop() called");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e("DURINGTOW", "onDestroy() called");
        locationManager.removeUpdates(gpslocation);
    }

    // Override the back button to not do anything
    @Override
    public void onBackPressed() {
        if (true)
            return;

        // Old code:
        // Override the back button to avoid accidentally stopping the logging
        new AlertDialog.Builder(this)
                .setMessage("Are you sure you want to exit?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }
}
