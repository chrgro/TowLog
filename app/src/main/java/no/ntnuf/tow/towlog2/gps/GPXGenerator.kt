package no.ntnuf.tow.towlog2.gps

import android.content.Context
import android.util.Log
import no.ntnuf.tow.towlog2.model.DayLog
import no.ntnuf.tow.towlog2.model.TowEntry
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object GPXGenerator {

    // Build and persist a GPX track in app-private storage and return the zip filename.
    fun storeGPX(context: Context, day: DayLog, tow: TowEntry, gpxBody: String): String? {
        val metadataTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(day.date)

        val header = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n")
            append("\n")
            append("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:gpxx=\"http://www.garmin.com/xmlschemas/GpxExtensions/v3\" xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\">\n")
            append("  <metadata>\n")
            append("    <link href=\"http://www.garmin.com\">\n")
            append("      <text>Garmin International</text>\n")
            append("    </link>\n")
            append("    <time>")
            append(metadataTime)
            append("</time>\n")
            append("  </metadata>\n")
            append("<trk><name>tow_pilot:")
            append(day.towpilot.name)
            append(",tow_plane:")
            append(day.towplane)
            append(",pilot:")
            append(tow.pilot.name)
            append(",registration:")
            append(tow.registration)
            append("</name>\n")
            append("<trkseg>\n")
        }

        val tail = "</trkseg>\n</trk></gpx>\n"
        val gpxContent = header + gpxBody + tail

        return writeOut(gpxContent, context, day, tow)
    }

    private fun writeOut(gpxContent: String, context: Context, day: DayLog, tow: TowEntry): String? {
        val dayCalendar = Calendar.getInstance().apply { time = day.date }
        val towCalendar = Calendar.getInstance().apply { time = tow.towStarted }

        // Legacy filename shape: tow_YYYY_M_DTH_M_S.gpx.zip
        val daySuffix = "${dayCalendar.get(Calendar.YEAR)}_${dayCalendar.get(Calendar.MONTH) + 1}_${dayCalendar.get(Calendar.DAY_OF_MONTH)}"
        val towIndex = "T${towCalendar.get(Calendar.HOUR_OF_DAY)}_${towCalendar.get(Calendar.MINUTE)}_${towCalendar.get(Calendar.SECOND)}"
        val filename = "tow_${daySuffix}${towIndex}.gpx"
        val zippedFilename = "$filename.zip"

        return try {
            context.openFileOutput(filename, Context.MODE_PRIVATE).use { fos ->
                fos.write(gpxContent.toByteArray(Charsets.UTF_8))
            }

            val rawGpx = File(context.filesDir, filename)
            val zippedGpx = File(context.filesDir, zippedFilename)
            zipFile(rawGpx, filename, zippedGpx)
            context.deleteFile(filename)

            Log.e("GPX_WRITE", "Saved GPX zip to file ${zippedGpx.canonicalPath}")
            zippedFilename
        } catch (e: FileNotFoundException) {
            Log.e("GPX_WRITE", "Save GPX, file not found: $filename", e)
            null
        } catch (e: IOException) {
            Log.e("GPX_WRITE", "Save GPX, IO exception: $filename", e)
            null
        }
    }

    private fun zipFile(inputFile: File, entryName: String, outputZip: File) {
        ZipOutputStream(outputZip.outputStream().buffered()).use { zipOut ->
            zipOut.putNextEntry(ZipEntry(entryName))
            inputFile.inputStream().buffered().use { input ->
                input.copyTo(zipOut)
            }
            zipOut.closeEntry()
        }
    }
}

