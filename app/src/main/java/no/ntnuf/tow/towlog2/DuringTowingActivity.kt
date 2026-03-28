package no.ntnuf.tow.towlog2

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import no.ntnuf.tow.towlog2.gps.GpsTowingCallbacks
import no.ntnuf.tow.towlog2.gps.GpsTowingTracker
import no.ntnuf.tow.towlog2.gps.GpxTrackWriter
import no.ntnuf.tow.towlog2.model.TowEntry
import java.util.Date

@Suppress("DEPRECATION")
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
    private lateinit var gpsTracker: GpsTowingTracker
    private lateinit var gpxTrackWriter: GpxTrackWriter
    private var locationUpdatesRequested = false

    private val handler = Handler(Looper.getMainLooper())

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

    @Suppress("unused")
    // This is called by GPS location handler to update the height during pre-tow
    fun updateTaxiHeight(height: Int, auxdata: Int) {
        handler.post {
            infoTextView.text = getString(R.string.during_tow_ready, height, auxdata)
        }
    }

    @Suppress("unused")
    // This is called by GPS location handler to update the height during towing
    fun updateRunningHeight(towheight: Int) {
        runningHeight = towheight
        handler.post {
            towHeightView.text = getString(R.string.during_tow_height_m, towheight)
            infoTextView.text = getString(R.string.during_tow_towing_max_height)
        }
    }

    @Suppress("unused")
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

        towHeightView.text = getString(R.string.during_tow_height_m, adjustedHeight)

    }

    private fun lockButtons(lock: Boolean) {
        abortTowButton.isEnabled = !lock

        if (lock) {
            // Locked buttons
            releaseButton.isEnabled = false
            releaseButton.text = getString(R.string.during_tow_slide_to_unlock)

            // Color buttons as disabled
            abortTowButton.alpha = 0.5f
        } else {
            // Unlocked buttons
            releaseButton.isEnabled = true
            releaseButton.text = getString(R.string.during_tow_release)

            abortTowButton.alpha = 1.0f

            // Hide lockbar after unlocking
            seekBarLock.visibility = View.INVISIBLE
        }
    }

    private fun applySystemBarMarginsToBottomEnd(view: View) {
        val marginLayoutParams = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val initialBottom = marginLayoutParams.bottomMargin
        val initialEnd = marginLayoutParams.marginEnd
        val initialRight = marginLayoutParams.rightMargin
        val extraBottomPx = (8 * resources.displayMetrics.density + 0.5f).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val updated = target.layoutParams as? ViewGroup.MarginLayoutParams
                    ?: return@setOnApplyWindowInsetsListener insets

            updated.bottomMargin = initialBottom + systemBars.bottom + extraBottomPx
            updated.marginEnd = initialEnd + systemBars.right
            updated.rightMargin = initialRight + systemBars.right
            target.layoutParams = updated
            insets
        }

        ViewCompat.requestApplyInsets(view)
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
                    gpsTracker.setDebugMode(true)
                    forceDebugModeCounter = 0
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
                    gpsTracker.forceToggleTowMode()
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
            releaseButton.text = getString(R.string.during_tow_released)

            gpsTracker.endTowing()

            confirmTowButton.visibility = View.VISIBLE

            infoTextView.text = getString(R.string.during_tow_gps_height, adjustedHeight)
            updateFinishedHeight(0)
        }

        // Button to abort/go back
        abortTowButton = findViewById(R.id.abortTowButton)
        abortTowButton.setOnClickListener {
            AlertDialog.Builder(this)
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
        // Keep the confirm action clear of transient system navigation/back controls.
        applySystemBarMarginsToBottomEnd(confirmTowButton)
        confirmTowButton.visibility = View.INVISIBLE
        confirmTowButton.setOnClickListener {
            // Build response Intent
            val response = Intent()
            val bundle = Bundle()

            towentry = towentry.copy(height = adjustedHeight)
            if (gpxTrackWriter.isEnabled()) {
                towentry = towentry.copy(gpx_body = gpxTrackWriter.getTrack())
                Log.e("DURINGTOW", "Finished tow GPX track with ${gpxTrackWriter.getNumPoints()} points")
            }
            bundle.putSerializable("value", towentry)
            response.putExtras(bundle)
            setResult(RESULT_OK, response)

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
        val loadedTowEntry = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            bundle?.getSerializable("value", TowEntry::class.java)
        } else {
            @Suppress("DEPRECATION")
            bundle?.getSerializable("value") as? TowEntry
        }
        towentry = loadedTowEntry ?: run {
            finish()
            return
        }

        // Update the textviews
        pilotnameTextView.text = towentry.pilot.name
        registrationTextView.text = towentry.registration

        // GPS Init
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        gpxTrackWriter = GpxTrackWriter(settings.getBoolean("tow_tracking_enabled", true))

        gpsTracker = GpsTowingTracker(
            callbacks = object : GpsTowingCallbacks {
                override fun updateTaxiHeight(height: Int, auxdata: Int) {
                    this@DuringTowingActivity.updateTaxiHeight(height, auxdata)
                }

                override fun updateRunningHeight(towHeight: Int) {
                    this@DuringTowingActivity.updateRunningHeight(towHeight)
                }

                override fun updateDebugInfo(info: String) {
                    this@DuringTowingActivity.updateDebugInfo(info)
                }
            },
            gpxTrackWriter = gpxTrackWriter
        )
        gpsTracker.prepareTowing(settings)

        lockButtons(true)

        // Keep legacy behavior: ignore physical/gesture back in this screen.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intentionally ignored to avoid accidental abort while towing.
            }
        })
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    override fun onResume() {
        super.onResume()
        if (locationUpdatesRequested) {
            return
        }
        if (!hasLocationPermission()) {
            infoTextView.text = "Location permission missing"
            return
        }

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                GpsTowingTracker.GPS_REFRESH_RATE_MS,
                0f,
                gpsTracker,
                Looper.getMainLooper()
            )
            locationUpdatesRequested = true
        } catch (securityException: SecurityException) {
            infoTextView.text = "Location permission missing"
            Log.e("DURINGTOW", "Failed to request GPS updates", securityException)
        } catch (illegalArgumentException: IllegalArgumentException) {
            infoTextView.text = "GPS provider unavailable"
            Log.e("DURINGTOW", "GPS provider unavailable", illegalArgumentException)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.e("DURINGTOW", "onPause() called.")
        if (locationUpdatesRequested) {
            try {
                locationManager.removeUpdates(gpsTracker)
            } catch (securityException: SecurityException) {
                Log.e("DURINGTOW", "Failed to remove GPS updates", securityException)
            }
            locationUpdatesRequested = false
        }
    }

    override fun onStop() {
        super.onStop()
        Log.e("DURINGTOW", "onStop() called")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e("DURINGTOW", "onDestroy() called")
        if (locationUpdatesRequested) {
            try {
                locationManager.removeUpdates(gpsTracker)
            } catch (securityException: SecurityException) {
                Log.e("DURINGTOW", "Failed to remove GPS updates", securityException)
            }
            locationUpdatesRequested = false
        }
    }

}
