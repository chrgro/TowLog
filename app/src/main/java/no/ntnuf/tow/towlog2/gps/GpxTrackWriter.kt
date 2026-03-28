package no.ntnuf.tow.towlog2.gps

import android.location.Location
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GpxTrackWriter(private val enabled: Boolean) {

    private data class TrackPoint(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double,
        val timestampMillis: Long
    )

    private val trackPoints: MutableList<TrackPoint> = mutableListOf()

    fun isEnabled(): Boolean = enabled

    fun addPoint(location: Location) {
        if (!enabled) {
            return
        }
        trackPoints.add(
            TrackPoint(
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                timestampMillis = location.time
            )
        )
    }

    fun reset() {
        trackPoints.clear()
    }

    fun getNumPoints(): Int = trackPoints.size

    fun getTrack(): String? {
        if (!enabled || trackPoints.isEmpty()) {
            return null
        }

        val timeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<gpx version=\"1.1\" creator=\"TowLog\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
            append("  <trk>\n")
            append("    <name>Tow track</name>\n")
            append("    <trkseg>\n")
            trackPoints.forEach { point ->
                append("      <trkpt lat=\"")
                append(point.latitude)
                append("\" lon=\"")
                append(point.longitude)
                append("\">\n")
                append("        <ele>")
                append(point.altitude)
                append("</ele>\n")
                append("        <time>")
                append(timeFormat.format(Date(point.timestampMillis)))
                append("</time>\n")
                append("      </trkpt>\n")
            }
            append("    </trkseg>\n")
            append("  </trk>\n")
            append("</gpx>\n")
        }
    }
}

