package no.ntnuf.tow.towlog2

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import no.ntnuf.tow.towlog2.model.TowEntry
import java.util.Date

class DuringTowingActivity : AppCompatActivity() {

    private var adjustedHeight = 0
    private var runningHeight = 0

    private lateinit var towentry: TowEntry

    private lateinit var pilotnameTextView: TextView
    private lateinit var registrationTextView: TextView

    private lateinit var infoTextView: TextView
    private lateinit var towHeightView: TextView

    private lateinit var incHeightButton: Button
    private lateinit var decHeightButton: Button

    private lateinit var abortTowButton: Button
    private lateinit var releaseButton: Button
    private lateinit var confirmTowButton: Button

    private lateinit var toolbar: Toolbar

    private lateinit var locationManager: LocationManager

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var context: Context

    private val GPS_REFRESH_RATE = 2000

    private lateinit var seekBarLock: SeekBar

    private var forceToggleTowModeCounter = 0
    private var forceDebugModeCounter = 0
    private var clickTime: Date? = null

    private lateinit var settings: SharedPreferences

    private var towing_altitude_increments = 100
    private var towing_round_up_limit = 35

    private fun calculateHeight(): Int {
        return runningHeight
    }

    // This is called by GPS location handler to update the height during pre-tow
    fun updateTaxiHeight(height: Int, auxdata: Int) {
        handler.post {
            infoTextView.text = "Ready, ${height}m MSL, ${auxdata} m/s"
        }
    }

    // This is called by GPS location handler to update the height during towing
    fun updateRunningHeight(towheight: Int) {
        runningHeight = towheight
        handler.post {
            towHeightView.text = "${towheight}m"
            infoTextView.text = "Towing, max height"
        }
        if (towentry.towStarted == null) {
            towentry = towentry.copy(towStarted = Date())
        }
    }

    // Print debug info from GPS handler
    fun updateDebugInfo(info: String) {
        handler.post {
            registrationTextView.text = info
        }
    }

    // This is called by self to round final height
    private fun updateFinishedHeight(offset: Int) {
        // Round to nearest 100, round up if larger than 35m
        val subhundred = adjustedHeight % towing_altitude_increments
        var rounded = (adjustedHeight / towing_altitude_increments) * towing_altitude_increments
        if (subhundred > towing_round_up_limit) {
            rounded += towing_altitude_increments
        }

        adjustedHeight = rounded + offset

        if (adjustedHeight < 0) {
            adjustedHeight = 0
        }

        towHeightView.text = "${adjustedHeight}m"

        if (towentry.towStarted == null) {
            towentry = towentry.copy(towStarted = Date())
        }
    }

    private fun lockButtons(lock: Boolean) {
        abortTowButton.isEnabled = !lock

        if (lock) {
            // Locked buttons
            releaseButton.isEnabled = false
            releaseButton.text = "Slide to unlock"

            // Color buttons as disabled
            abortTowButton.alpha = 0.5f
        } else {
            // Unlocked buttons
            releaseButton.isEnabled = true
            releaseButton.text = "Release"

            abortTowButton.alpha = 1.0f

            // Hide lockbar after unlocking
            seekBarLock.visibility = View.INVISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set fullscreen to disable the notification bar
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        super.onCreate(savedInstanceState)

        // Hide navigation bar
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)

        settings = PreferenceManager.getDefaultSharedPreferences(this)
        towing_altitude_increments = settings.getString("towing_altitude_increments", "100")?.toIntOrNull() ?: 100
        towing_round_up_limit = settings.getString("towing_round_up_limit", "35")?.toIntOrNull() ?: 35

        setContentView(R.layout.activity_during_towing)

        context = this

        toolbar = findViewById(R.id.toolbarduringtowing)
        setSupportActionBar(toolbar)

        // Registration name. Can be clicked to show GPS debug info.
        registrationTextView = findViewById(R.id.duringtowingregistration)
        registrationTextView.setOnClickListener {
            forceDebugModeCounter++
            val now = Date()
            if (clickTime == null || now.time - clickTime!!.time > 4000) {
                clickTime = now
                forceDebugModeCounter = 0
            } else {
                if (forceDebugModeCounter >= 8) {
                    // TODO: Enable debug mode when GPSLocationHandler is implemented
                    // gpslocation.setDebugMode(true)
                }
            }
        }

        // Pilot name. Can be clicked to force various towing modes (debug)
        pilotnameTextView = findViewById(R.id.duringtowingpilot)
        pilotnameTextView.setOnClickListener {
            forceToggleTowModeCounter++
            val now = Date()
            if (clickTime == null || now.time - clickTime!!.time > 4000) {
                clickTime = now
                forceToggleTowModeCounter = 0
            } else {
                if (forceToggleTowModeCounter >= 6) {
                    // TODO: Toggle tow mode when GPSLocationHandler is implemented
                    // gpslocation.forceToggleTowMode()
                    forceToggleTowModeCounter = 0
                }
            }
        }

        infoTextView = findViewById(R.id.infoTowText)
        towHeightView = findViewById(R.id.currentHeight)

        seekBarLock = findViewById(R.id.duringtowing_seekbarlock)

        // Hide +- buttons by default
        incHeightButton = findViewById(R.id.incHeightButton)
        incHeightButton.visibility = View.INVISIBLE
        incHeightButton.setOnClickListener {
            updateFinishedHeight(+towing_altitude_increments)
        }

        // Manually decrement height value
        decHeightButton = findViewById(R.id.decHeightButton)
        decHeightButton.visibility = View.INVISIBLE
        decHeightButton.setOnClickListener {
            updateFinishedHeight(-towing_altitude_increments)
        }

        // Button to release tow
        releaseButton = findViewById(R.id.releaseButton)
        releaseButton.setOnClickListener {
            towentry = towentry.copy(height = calculateHeight())
            adjustedHeight = towentry.height

            // Enable +- buttons
            incHeightButton.visibility = View.VISIBLE
            decHeightButton.visibility = View.VISIBLE

            // Disable release button
            releaseButton.isEnabled = false
            releaseButton.text = "Released"

            // TODO: End towing when GPSLocationHandler is implemented
            // gpslocation.endTowing()

            confirmTowButton.visibility = View.VISIBLE

            infoTextView.text = "GPS tow height ${adjustedHeight}m"
            updateFinishedHeight(0)
        }

        // Button to abort/go back
        abortTowButton = findViewById(R.id.abortTowButton)
        abortTowButton.setOnClickListener {
            AlertDialog.Builder(context)
                    .setMessage("Are you sure you want to exit?")
                    .setCancelable(false)
                    .setPositiveButton("Yes") { _, _ ->
                        finish()
                    }
                    .setNegativeButton("No", null)
                    .show()
        }

        // Confirm button shown after towing is completed
        confirmTowButton = findViewById(R.id.confirmTowButton)
        confirmTowButton.visibility = View.INVISIBLE
        confirmTowButton.setOnClickListener {
            // Build response Intent
            val response = Intent()
            val bundle = Bundle()

            towentry = towentry.copy(height = adjustedHeight)
            // TODO: Add GPX body when GPXGenerator is implemented
            // if (gpxgenerator.isEnabled()) {
            //     towentry.gpx_body = gpxgenerator.getTrack()
            //     Log.e("DURINGTOW", "Finished tow GPX track with ${gpxgenerator.getNumPoints()} points")
            // }
            bundle.putSerializable("value", towentry)
            response.putExtras(bundle)
            setResult(Activity.RESULT_OK, response)

            // End the activity
            finish()
        }

        // Screen lock using slide bar
        seekBarLock.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (progress == 100) {
                    lockButtons(false)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.progress = 0
            }
        })

        // Load the incoming tow info (name, registration etc)
        val intent = intent
        val bundle = intent.extras
        towentry = bundle?.getSerializable("value") as TowEntry

        // Update the textviews
        pilotnameTextView.text = towentry.pilot.name
        registrationTextView.text = towentry.registration

        // TODO: Set up GPS track to GPX logging when GPXGenerator is implemented
        // gpxgenerator = GPXGenerator(settings.getBoolean("tow_tracking_enabled", true))

        // GPS Init
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // TODO: Initialize GPS location handler when GPSLocationHandler is implemented
        // gpslocation = GPSLocationHandler()
        // gpslocation.prepareTowing(this, this.settings)
        // gpslocation.setLocationManager(locationManager)
        // gpslocation.setGPXGenerator(gpxgenerator)

        lockButtons(true)
    }

    override fun onResume() {
        super.onResume()
        // TODO: Request location updates when GPSLocationHandler is implemented
        // locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_REFRESH_RATE, 0f, gpslocation)
    }

    override fun onPause() {
        super.onPause()
        Log.e("DURINGTOW", "onPause() called.")
    }

    override fun onStop() {
        super.onStop()
        Log.e("DURINGTOW", "onStop() called")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e("DURINGTOW", "onDestroy() called")
        // TODO: Remove location updates when GPSLocationHandler is implemented
        // locationManager.removeUpdates(gpslocation)
    }

    // Override the back button to not do anything
    override fun onBackPressed() {
        // Do nothing - prevent accidental back press during towing
    }
}
