package no.ntnuf.tow.towlog2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.location.LocationManager;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class DuringTowingActivity extends AppCompatActivity {

    int adjustedHeight;
    int runningHeight;

    TowEntry towentry;

    TextView pilotnameTextView;
    TextView registrationTextView;

    TextView infoTextView;
    TextView towHeightView;

    Button incHeightButton;
    Button decHeightButton;

    Button abortTowButton;
    Button releaseButton;
    Button confirmTowButton;

    GPSLocationHandler gpslocation;
    LocationManager locationManager;

    private final Handler handler = new Handler();

    public void initGPS() {
        // GPS Stuff
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        gpslocation = new GPSLocationHandler();

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, gpslocation);
        gpslocation.setLocationManager(locationManager);

    }

    private int calculateHeight() {
        return runningHeight;
    }

    public void updateRunningHeight(int height) {
        Log.e("LOG", "Updated running height with " + String.valueOf(height));
        final int lockedheight = height;
        runningHeight = height;
        handler.post(new Runnable() {
            public void run() {
                towHeightView.setText(String.valueOf(lockedheight + "m"));
                infoTextView.setText("Tow in progress, height");
            }
        });
    }

    private void updateFinishedHeight(int offset) {
        // Round to nearest 100, round up if larger than 35m
        int subhundred = (adjustedHeight % 100);
        int rounded = (adjustedHeight / 100) * 100;
        if (subhundred > 35) {
            rounded += 100;
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

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_during_towing);


        pilotnameTextView = (TextView) findViewById(R.id.duringtowingpilot);
        registrationTextView = (TextView) findViewById(R.id.duringtowingregistration);

        infoTextView = (TextView) findViewById(R.id.infoTowText);
        towHeightView = (TextView) findViewById(R.id.currentHeight);

        // Hide +- buttons by default
        // Manually increment height value
        incHeightButton = (Button) findViewById(R.id.incHeightButton);
        incHeightButton.setVisibility(View.INVISIBLE);
        incHeightButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                updateFinishedHeight(+100);

            }
        });

        // Manually decrement height value
        decHeightButton = (Button) findViewById(R.id.decHeightButton);
        decHeightButton.setVisibility(View.INVISIBLE);
        decHeightButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                updateFinishedHeight(-100);
            }
        });


        releaseButton = (Button) findViewById(R.id.releaseButton);
        releaseButton.getBackground().setColorFilter(0xFFFFE205, PorterDuff.Mode.MULTIPLY);
        releaseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                towentry.height = calculateHeight();
                adjustedHeight = towentry.height;

                // Enable +- buttons
                incHeightButton.setVisibility(View.VISIBLE);
                decHeightButton.setVisibility(View.VISIBLE);

                // Gray release button
                releaseButton.getBackground().setColorFilter(0xFFE3E3E3, PorterDuff.Mode.MULTIPLY);
                releaseButton.setEnabled(false);
                releaseButton.setText("Released");

                gpslocation.endTowing();

                confirmTowButton.setVisibility(View.VISIBLE);

                infoTextView.setText("GPS tow height " + String.valueOf(adjustedHeight) + "m");
                updateFinishedHeight(0);

            }
        });

        abortTowButton = (Button) findViewById(R.id.abortTowButton);
        abortTowButton.getBackground().setColorFilter(0xFFFF4747, PorterDuff.Mode.MULTIPLY);
        abortTowButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // End the activity with no further action
                finish();
            }
        });

        confirmTowButton = (Button) findViewById(R.id.confirmTowButton);
        confirmTowButton.setVisibility(View.INVISIBLE);
        confirmTowButton.getBackground().setColorFilter(0xFF54F238, PorterDuff.Mode.MULTIPLY);
        confirmTowButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Build response Intent
                Intent response = new Intent();
                Bundle bundle = new Bundle();

                towentry.height = adjustedHeight;
                bundle.putSerializable("value", towentry);
                response.putExtras(bundle);
                setResult(Activity.RESULT_OK, response);

                // End the activity
                finish();
            }
        });


        // Load the incoming tow info (name, registration etc)
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        towentry = (TowEntry) bundle.getSerializable("value");

        // Update the textviews
        pilotnameTextView.setText(towentry.pilotname);
        registrationTextView.setText(towentry.registration);

        // Start up the GPS
        initGPS();
        gpslocation.prepareTowing(this);


    }

    // Override the back button to avoid accidentally stopping the logging
    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setMessage("Are you sure you want to exit?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // TODO: Handle exit
                        finish();
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }
}
