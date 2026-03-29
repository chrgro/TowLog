package no.ntnuf.tow.towlog2.model

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

        var ret = "{ \n"

        ret += "\"towpilot\": \"${towpilot.name}\",\n"
        ret += "\"towpilot_customer_number\": ${towpilot.customerNumber},\n"
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
            ret += "  \"pilot_customer_number\": ${tow.pilot.customerNumber},\n"

            if (tow.pilot.email != null) {
                ret += "  \"pilot_email\": \"${tow.pilot.email}\",\n"
            }

            if (tow.copilot != null) {
                ret += "  \"copilot\": \"${tow.copilot.name}\",\n"
                ret += "  \"copilot_customer_number\": ${tow.copilot.customerNumber},\n"
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
