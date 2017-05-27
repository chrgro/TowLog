package no.ntnuf.towlog.towlog2.common;

import android.content.Context;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * Container for a log for a single day
 */
public class DayLog implements Serializable{

    private static final long serialVersionUID = 7L;

    public Contact towpilot;
    public String towplane;
    public Date date;

    public ArrayList<TowEntry> tows;

    public boolean logIsLocked;
    private boolean logHasBeenSent;
    private int logSentNumberOfTimes;

    public DayLog(Context context) {
        tows = new ArrayList<TowEntry>();
    }

    public void setLogHasBeenSent() {
        logHasBeenSent = true;
        logIsLocked = true;
        logSentNumberOfTimes += 1;
    }

    // Format the daylog to CSV
    public String getCsvOutput() {
        SimpleDateFormat outdf = new SimpleDateFormat("cccc d/M/yyyy");
        SimpleDateFormat time = new SimpleDateFormat("HH:mm");

        String ret = "Towing log: \n\n\n";

        ret += "towpilot, " + towpilot.name + "\n";
        ret += "towplane, " + towplane + "\n";
        ret += "date, " + outdf.format(this.date) + "\n\n";

        ret += "tow#, registration, pilot (billing), copilot, height, timeOfTow, notes \n\n";

        int i = 1;
        for (TowEntry tow : tows) {
            ret += "#" + String.valueOf(i) + ", ";
            ret += tow.registration + ", ";
            ret += tow.pilot.name + ", ";

            if (tow.copilot != null){
                ret += tow.copilot;
            }
            ret += ", ";

            ret += tow.height +"m" + ", ";
            ret += time.format(tow.towStarted) + ", " ;
            ret += tow.notes + ", ";

            ret += "\n";
            i++;
        }

        if (logHasBeenSent) {
            ret += "\n";
            ret += "note, Log previously sent "+logSentNumberOfTimes+
                    " time"+(logSentNumberOfTimes>1?"s":"")+".";
        }



        return ret;
    }

    // Generate a JSON representation of the daylog
    public String getJSONOutput() {
        SimpleDateFormat outdf = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat time = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

        String ret = "{ \n";

        ret += "\"towpilot\": \"" + towpilot.name + "\",\n";
        ret += "\"towplane\": \"" + towplane + "\",\n";

        ret += "\"date\": \"" + outdf.format(this.date) + "\",\n";

        ret += "\"sent_times\": \"" + logSentNumberOfTimes + "\",\n";

        ret += "\"tows\": [\n";
        int i = 1;
        boolean first = true;
        for (TowEntry tow : tows) {
            if (!first) {
                ret += " ,\n";
            }
            ret += " {\n";
            ret += "  \"townum\": " + String.valueOf(i) + ",\n";

            ret += "  \"registration\": \"" + tow.registration + "\",\n";
            ret += "  \"pilot\": \"" + tow.pilot.name + "\",\n";

            if (tow.pilot.email != null) {
                ret += "  \"pilot_email\": \"" + tow.pilot.email + "\",\n";
            }

            if (tow.copilot != null){
                ret += "  \"copilot\": \"" + tow.copilot + "\",\n";
                if (tow.copilot.email != null) {
                    ret += "  \"copilot_email\": \"" + tow.copilot.email + "\",\n";
                }
            }

            ret += "  \"tow_started\": \"" + time.format(tow.towStarted) + "\",\n";
            ret += "  \"notes\": \"" + tow.notes + "\",\n";

            if (tow.gpx_filename != null) {
                ret += "  \"gpx_filename\": \"" + tow.gpx_filename + "\",\n";
            }

            ret += "  \"height\": " + tow.height + "\n";

            ret += " }\n";

            first = false;
            i++;
        }

        ret += "]\n";

        ret += "}\n";

        return ret;
    }

    // Really really simple converter from CSV to HTML tables. Does not like quoted commas...
    public String csv2Html(String csv) {
        String ret = "<table>\n";

        String[] lines = csv.split("\n");
        for (String line : lines) {
            if (line.trim().equals("")) {
                continue;
            }
            ret += " <tr>\n";

            String [] elements = line.split(",");

            for (String element : elements) {
                ret += "  <td>";
                ret += element.trim();
                ret += "</td>\n";
            }
            ret += " </tr>\n";
        }

        ret += "</table>";
        return ret;
    }

    // Helper function to move entries up or down
    public void moveLogLine(boolean up, int line) {
        if (up) {
            if (line >= 1 && line < tows.size()) {
                TowEntry tmp = tows.get(line);
                tows.set(line, tows.get(line - 1));
                tows.set(line - 1, tmp);
            }
        } else {
            if (line >= 0 && line < tows.size()-1) {
                TowEntry tmp = tows.get(line);
                tows.set(line, tows.get(line + 1));
                tows.set(line + 1, tmp);
            }
        }
    }

    // Helper function to remove a single log line
    public void deleteLogLine(int line) {
        if (line >= 0 && line < tows.size()) {
            tows.remove(line);
        }
    }

}
