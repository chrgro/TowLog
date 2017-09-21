package no.ntnuf.towlog.towlog2.duringtowing;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.zip.ZipOutputStream;

import no.ntnuf.towlog.towlog2.common.DayLog;
import no.ntnuf.towlog.towlog2.common.TowEntry;
import no.ntnuf.towlog.towlog2.common.ZipUtils;

/**
 * Created by Christian on 25.05.2017.
 */

public class GPXGenerator implements Serializable{

    private static final long serialVersionUID = 1L;

    String filename;
    String body;
    int num_points;

    boolean enabled;

    boolean done = false;

    public GPXGenerator(boolean enabled) {
        this.body = "";
        this.num_points = 0;
        this.enabled = enabled;
    }

    // For each trackpoint (location)
    public void appendTrackpoint(Location location) {
        if (done || !enabled) return;
        body += "<trkpt lat=\""+location.getLatitude()+"\" lon=\""+location.getLongitude()+"\">";
        body += "<ele>"+location.getAltitude()+"</ele>";
        SimpleDateFormat outdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = new Date(location.getTime());
        String dstr = outdf.format(date);
        body += "<time>"+dstr+"</time>";
        body +="</trkpt>\n";

        num_points += 1;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getNumPoints() {
        return num_points;
    }

    public String getTrack() {
        if (num_points < 5) {
            Log.e("GPX_WRITE", "Too few points to call getTrack()");
            return null;
        }
        return body;
    }

    // Static functions to create the final string and write it out
    public static String storeGPX(Context context, DayLog day, TowEntry tow, String gpxbody) {
        SimpleDateFormat outdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dstr = outdf.format(day.date);
        String header =  "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n" +
                "\n" +
                "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:gpxx=\"http://www.garmin.com/xmlschemas/GpxExtensions/v3\" xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\">\n" +
                "  <metadata>\n" +
                "    <link href=\"http://www.garmin.com\">\n" +
                "      <text>Garmin International</text>\n" +
                "    </link>\n" +
                "    <time>"+dstr+"</time>\n" +
                "  </metadata>\n";

        header += "<trk><name>tow_pilot:"+day.towpilot.name+",tow_plane:"+day.towplane+",pilot:"+tow.pilot.name+",registration:"+tow.registration+"</name>\n";
        header += "<trkseg>\n";

        String tail = "</trkseg>\n";
        tail += "</trk></gpx>\n";

        String gpx_content = header + gpxbody + tail;

        return writeout(gpx_content, context, day, tow);
    }

    // Write the GPX to a file
    private static String writeout(String gpx_content, Context context, DayLog day, TowEntry tow ) {
        // Save GPX track
        FileOutputStream fos = null;
        Calendar c = Calendar.getInstance();
        c.setTime(day.date);
        Calendar t = Calendar.getInstance();
        t.setTime(tow.towStarted);

        // Filename format: tow_YYYY_MM_DD_tHH_MM_SS.gpx
        String daylogsuffix = String.valueOf(c.get(Calendar.YEAR)) + "_" +
                String.valueOf(c.get(Calendar.MONTH) + 1) + "_" +
                String.valueOf(c.get(Calendar.DAY_OF_MONTH));
        String towindex = "T" + String.valueOf(t.get(Calendar.HOUR_OF_DAY))+"_"+
                String.valueOf(t.get(Calendar.MINUTE))+"_"+
                String.valueOf(t.get(Calendar.SECOND));
        String filename = "tow_" + daylogsuffix + "" + towindex + ".gpx";
        String zippedfilename = filename+".zip";

        ZipUtils zip = new ZipUtils();
        try {
            fos = context.openFileOutput(filename, Context.MODE_PRIVATE);
            fos.write(gpx_content.getBytes());
            fos.close();
            File raw_gpx = new File(context.getFilesDir(), filename);
            Log.e("GPX_WRITE", "Saved GPX to file "+raw_gpx.getCanonicalPath());
            File zipped_gpx = new File(context.getFilesDir(), zippedfilename);
            zip.zipFileAtPath(raw_gpx.getCanonicalPath(), zipped_gpx.getCanonicalPath());
            Log.e("GPX_WRITE", "Zipped GPX to file "+zipped_gpx.getCanonicalPath());
            context.deleteFile(filename);
            Log.e("GPX_WRITE", "Deleted unzipped GPX file "+raw_gpx.getCanonicalPath());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e("GPX_WRITE", "Save GPX, File not found" + filename);
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("GPX_WRITE", "Save GPX, IO Exception" + filename);
            return null;
        }
        return zippedfilename;
    }
}
