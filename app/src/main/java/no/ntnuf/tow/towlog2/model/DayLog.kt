package no.ntnuf.tow.towlog2.model

import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date

data class DayLog(
    val towpilot: Contact,
    val towplane: String,
    val date: Date,
    val tows: ArrayList<TowEntry> = ArrayList(),
    val logIsLocked: Boolean = false,
    val logHasBeenSent: Boolean = false,
    val logSentNumberOfTimes: Int = 0
) : Serializable {

    companion object {
        private const val serialVersionUID = 7L
        private const val dayLogFileName = "daylog_"
    }

    fun getFilename(): String {
        val outdf = SimpleDateFormat("yyyy_MM_dd")
        val daylogsuffix = outdf.format(date)
        return dayLogFileName + daylogsuffix
    }

    fun setLogHasBeenSent(): DayLog {
        return copy(
            logHasBeenSent = true,
            logIsLocked = true,
            logSentNumberOfTimes = logSentNumberOfTimes + 1
        )
    }

    // Format the daylog to CSV
    fun getCsvOutput(): String {
        val outdf = SimpleDateFormat("cccc d/M/yyyy")
        val time = SimpleDateFormat("HH:mm")

        var ret = "Towing log: \n\n\n"

        ret += "towpilot, ${towpilot.name}\n"
        ret += "towplane, $towplane\n"
        ret += "date, ${outdf.format(date)}\n\n"

        ret += "tow#, registration, pilot (billing), copilot, height, timeOfTow, notes \n\n"

        var i = 1
        for (tow in tows) {
            ret += "#${i}, "
            ret += "${tow.registration}, "
            ret += "${tow.pilot.name}, "

            if (tow.copilot != null) {
                ret += tow.copilot
            }
            ret += ", "

            ret += "${tow.height}m, "
            ret += "${time.format(tow.towStarted)}, "
            ret += "${tow.notes}, "

            ret += "\n"
            i++
        }

        if (logHasBeenSent) {
            ret += "\n"
            ret += "note, Log previously sent $logSentNumberOfTimes time${if (logSentNumberOfTimes > 1) "s" else ""}."
        }

        return ret
    }

    // Generate a JSON representation of the daylog
    fun getJSONOutput(): String {
        val outdf = SimpleDateFormat("yyyy-MM-dd")
        val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")

        var ret = "{ \n"

        ret += "\"towpilot\": \"${towpilot.name}\",\n"
        ret += "\"towplane\": \"$towplane\",\n"

        ret += "\"date\": \"${outdf.format(date)}\",\n"

        ret += "\"sent_times\": \"$logSentNumberOfTimes\",\n"

        ret += "\"tows\": [\n"
        var i = 1
        var first = true
        for (tow in tows) {
            if (!first) {
                ret += " ,\n"
            }
            ret += " {\n"
            ret += "  \"townum\": $i,\n"

            ret += "  \"registration\": \"${tow.registration}\",\n"
            ret += "  \"pilot\": \"${tow.pilot.name}\",\n"

            if (tow.pilot.email != null) {
                ret += "  \"pilot_email\": \"${tow.pilot.email}\",\n"
            }

            if (tow.copilot != null) {
                ret += "  \"copilot\": \"${tow.copilot}\",\n"
                if (tow.copilot.email != null) {
                    ret += "  \"copilot_email\": \"${tow.copilot.email}\",\n"
                }
            }

            ret += "  \"tow_started\": \"${time.format(tow.towStarted)}\",\n"
            ret += "  \"notes\": \"${tow.notes}\",\n"

            if (tow.gpx_filename != null) {
                ret += "  \"gpx_filename\": \"${tow.gpx_filename}\",\n"
            }

            ret += "  \"height\": ${tow.height}\n"

            ret += " }\n"

            first = false
            i++
        }

        ret += "]\n"

        ret += "}\n"

        return ret
    }

    // Really really simple converter from CSV to HTML tables. Does not like quoted commas...
    fun csv2Html(csv: String): String {
        var ret = "<table>\n"

        val lines = csv.split("\n")
        for (line in lines) {
            if (line.trim().isEmpty()) {
                continue
            }
            ret += " <tr>\n"

            val elements = line.split(",")

            for (element in elements) {
                ret += "  <td>"
                ret += element.trim()
                ret += "</td>\n"
            }
            ret += " </tr>\n"
        }

        ret += "</table>"
        return ret
    }

    // Helper function to move entries up or down
    fun moveLogLine(up: Boolean, line: Int): DayLog {
        val newTows = ArrayList(tows)
        if (up) {
            if (line >= 1 && line < newTows.size) {
                val tmp = newTows[line]
                newTows[line] = newTows[line - 1]
                newTows[line - 1] = tmp
            }
        } else {
            if (line >= 0 && line < newTows.size - 1) {
                val tmp = newTows[line]
                newTows[line] = newTows[line + 1]
                newTows[line + 1] = tmp
            }
        }
        return copy(tows = newTows)
    }

    // Helper function to remove a single log line
    fun deleteLogLine(line: Int): DayLog {
        val newTows = ArrayList(tows)
        if (line >= 0 && line < newTows.size) {
            newTows.removeAt(line)
        }
        return copy(tows = newTows)
    }
}
