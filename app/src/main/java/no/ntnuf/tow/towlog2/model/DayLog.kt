package no.ntnuf.tow.towlog2.model

import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

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
        private const val serialVersionUID = 8L
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

    // Format the daylog as an email-friendly plain text report.
    fun getCsvOutput(): String {
        val norwegianLocale = Locale("no", "NO")
        val outdf = SimpleDateFormat("EEEE yyyy-MM-dd", norwegianLocale)
        val time = SimpleDateFormat("HH:mm", Locale.ENGLISH)
        val report = StringBuilder()

        report.appendLine("Tow Log")
        report.appendLine("=======")
        report.appendLine()
        report.appendLine(outdf.format(date).lowercase(norwegianLocale))
        report.appendLine("-------")
        report.appendLine()

        report.appendLine("Tow Pilot:")
        report.appendLine("  ${towpilot.name}")
        report.appendLine()

        report.appendLine("Tow Plane:")
        report.appendLine("  $towplane")
        report.appendLine()

        report.appendLine("Tows")
        report.appendLine("-------")
        report.appendLine()

        if (tows.isEmpty()) {
            report.appendLine("No tows logged.")
        }

        for ((index, tow) in tows.withIndex()) {
            report.appendLine("${index + 1}. ${tow.registration}")

            val crewLine = StringBuilder()
            crewLine.append(formatContactWithRoleAndAccount(tow.pilot, "billing"))
            if (tow.copilot != null) {
                crewLine.append(", ")
                crewLine.append(formatContactWithRoleAndAccount(tow.copilot, "copilot"))
            }
            report.appendLine("   $crewLine")

            report.appendLine("   Height ${tow.height}m, Tow Takeoff Time ${time.format(tow.towStarted)}")

            if (tow.notes.isNotBlank()) {
                report.appendLine("   Notes: ${tow.notes}")
            }
        }

        if (logHasBeenSent) {
            report.appendLine()
            report.appendLine("Note: Log previously sent $logSentNumberOfTimes time${if (logSentNumberOfTimes > 1) "s" else ""}.")
        }

        return report.toString().trimEnd()
    }

    // Generate a JSON representation of the daylog
    fun getJSONOutput(): String {
        val outdf = SimpleDateFormat("yyyy-MM-dd")
        val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")

        val towsJson = JSONArray()
        tows.forEachIndexed { index, tow ->
            val towJson = JSONObject()
                .put("townum", index + 1)
                .put("registration", tow.registration)
                .put("pilot", tow.pilot.name)
                .put("pilot_customer_number", tow.pilot.customerNumber)
                .put("tow_started", time.format(tow.towStarted))
                .put("notes", tow.notes)
                .put("height", tow.height)

            tow.pilot.email?.let { towJson.put("pilot_email", it) }
            tow.copilot?.let { copilot ->
                towJson
                    .put("copilot", copilot.name)
                    .put("copilot_customer_number", copilot.customerNumber)
                copilot.email?.let { towJson.put("copilot_email", it) }
            }
            tow.gpx_filename?.let { towJson.put("gpx_filename", it) }

            towsJson.put(towJson)
        }

        val dayLogJson = JSONObject()
            .put("towpilot", towpilot.name)
            .put("towpilot_customer_number", towpilot.customerNumber)
            .put("towplane", towplane)
            .put("date", outdf.format(date))
            .put("sent_times", logSentNumberOfTimes.toString())
            .put("tows", towsJson)

        return dayLogJson.toString(2)
    }

    // Convert plain text output to a simple HTML document preserving line breaks.
    fun csv2Html(csv: String): String {
        val escaped = csv
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        return "<html><body><pre>$escaped</pre></body></html>"
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

    private fun formatCustomerNumber(contact: Contact?): String {
        if (contact == null || contact.customerNumber <= 0) {
            return ""
        }
        return contact.customerNumber.toString()
    }

    private fun formatContactWithRoleAndAccount(contact: Contact?, role: String): String {
        if (contact == null) {
            return ""
        }

        val customerNumber = formatCustomerNumber(contact)
        if (customerNumber.isEmpty()) {
            return "${contact.name} ($role)"
        }

        return "${contact.name} ($role, account $customerNumber)"
    }
}
