package no.ntnuf.tow.towlog2.gps

import android.content.SharedPreferences
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import kotlin.math.max
import kotlin.math.roundToInt

interface GpsTowingCallbacks {
    fun updateTaxiHeight(height: Int, auxdata: Int)
    fun updateRunningHeight(towHeight: Int)
    fun updateDebugInfo(info: String)
}

class GpsTowingTracker(
    private val callbacks: GpsTowingCallbacks,
    private val gpxTrackWriter: GpxTrackWriter
) : LocationListener {

    companion object {
        const val GPS_REFRESH_RATE_MS = 500L
    }

    private var debugMode = false
    private var towMode = false
    private var towingEnded = false

    private var goodFixesNeeded = 10
    private var accuracyThresholdMeters = 40f
    private var towingSpeedThresholdMetersPerSecond = 8.0f

    private var goodFixCount = 0
    private var currentAltitudeMeters = 0
    private var baselineAltitudeMeters = 0
    private var maxTowHeightMeters = 0

    fun prepareTowing(settings: SharedPreferences) {
        goodFixesNeeded = settings.getString("gps_good_fixes_needed", "10")?.toIntOrNull()?.coerceAtLeast(1) ?: 10
        accuracyThresholdMeters = settings.getString("gps_accuracy_threshold", "40")?.toFloatOrNull()?.coerceAtLeast(1f) ?: 40f
        towingSpeedThresholdMetersPerSecond = settings.getString("towing_speed_threshold", "8.0")?.toFloatOrNull()?.coerceAtLeast(1f) ?: 8.0f

        debugMode = false
        towMode = false
        towingEnded = false
        goodFixCount = 0
        maxTowHeightMeters = 0
        currentAltitudeMeters = 0
        baselineAltitudeMeters = 0
        gpxTrackWriter.reset()
    }

    fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
    }

    fun forceToggleTowMode() {
        towMode = !towMode
        if (towMode) {
            baselineAltitudeMeters = currentAltitudeMeters
            maxTowHeightMeters = 0
            towingEnded = false
            callbacks.updateRunningHeight(maxTowHeightMeters)
        }
    }

    fun endTowing() {
        towingEnded = true
        towMode = false
    }

    override fun onLocationChanged(location: Location) {
        gpxTrackWriter.addPoint(location)

        val hasGoodFix = location.accuracy <= accuracyThresholdMeters
        val speedMetersPerSecond = location.speed

        if (hasGoodFix) {
            goodFixCount++
            currentAltitudeMeters = location.altitude.roundToInt()
        } else {
            goodFixCount = 0
        }

        if (towingEnded) {
            emitDebugInfo(location, hasGoodFix, speedMetersPerSecond)
            return
        }

        if (!towMode) {
            if (hasGoodFix) {
                baselineAltitudeMeters = currentAltitudeMeters
                callbacks.updateTaxiHeight(currentAltitudeMeters, speedMetersPerSecond.roundToInt())
            }
            if (goodFixCount >= goodFixesNeeded && speedMetersPerSecond >= towingSpeedThresholdMetersPerSecond) {
                towMode = true
                baselineAltitudeMeters = currentAltitudeMeters
                maxTowHeightMeters = 0
            }
        } else if (hasGoodFix) {
            val relativeHeight = max(0, currentAltitudeMeters - baselineAltitudeMeters)
            if (relativeHeight > maxTowHeightMeters) {
                maxTowHeightMeters = relativeHeight
                callbacks.updateRunningHeight(maxTowHeightMeters)
            }
        }

        emitDebugInfo(location, hasGoodFix, speedMetersPerSecond)
    }

    @Deprecated("Deprecated in API 29")
    @Suppress("DEPRECATION")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        // Kept for API compatibility on older LocationListener callbacks.
    }

    override fun onProviderEnabled(provider: String) {
        // No-op.
    }

    override fun onProviderDisabled(provider: String) {
        // No-op.
    }

    private fun emitDebugInfo(location: Location, hasGoodFix: Boolean, speedMetersPerSecond: Float) {
        if (!debugMode) {
            return
        }
        val info = buildString {
            append("mode=")
            append(if (towMode) "tow" else "taxi")
            append(", ended=")
            append(towingEnded)
            append(", alt=")
            append(currentAltitudeMeters)
            append("m")
            append(", acc=")
            append(location.accuracy.roundToInt())
            append("m")
            append(", speed=")
            append(speedMetersPerSecond.roundToInt())
            append("m/s")
            append(", fix=")
            append(if (hasGoodFix) "good" else "bad")
            append("(")
            append(goodFixCount)
            append("/")
            append(goodFixesNeeded)
            append(")")
            append(", max=")
            append(maxTowHeightMeters)
            append("m")
        }
        callbacks.updateDebugInfo(info)
    }
}


