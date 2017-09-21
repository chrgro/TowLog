package no.ntnuf.towlog.towlog2.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import static android.content.Context.MODE_PRIVATE;

/**
 * Created by Christian on 21.09.2017.
 */

public class LogUploader {

    Context context;
    private SharedPreferences settings;
    ZipUtils zip;

    final String fullfilename = "pending_uploads_list";

    PendingUploadsList pending_uploads;

    public LogUploader(Context c, SharedPreferences settings) {
        this.context = c;
        this.settings = settings;
        this.zip = new ZipUtils();

        // Load or create new list of pending uploads
        if (!loadPendingUploadsList()) {
            pending_uploads = new PendingUploadsList();
        }
    }

    // File operations, serialize the list of pending uploads to file
    private void savePendingUploadsList() {
        FileOutputStream fos = null;
        try {
            fos = this.context.openFileOutput(fullfilename, MODE_PRIVATE);
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(pending_uploads);
            os.close();
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e("LOGUPLOADER", "Pending uploads save, File not found" + fullfilename);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("LOGUPLOADER", "Pending uploads save, IO Exception" + fullfilename);
        }
    }

    // File operations, nserialize the list of pending uploads from file
    private boolean loadPendingUploadsList() {
        boolean loadedSuccessfully = false;
        try {
            FileInputStream fis = this.context.openFileInput(fullfilename);
            ObjectInputStream is = new ObjectInputStream(fis);
            pending_uploads = (PendingUploadsList) is.readObject();
            is.close();
            fis.close();
            loadedSuccessfully = true;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e("LOGUPLOADER", "Pending uploads load, file not found: " + fullfilename);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return loadedSuccessfully;
    }

    // Add a log file to the queue
    public void addToUploadQueue(DayLog daylog) {
        File daylogfile = new File(context.getFilesDir(), daylog.getFilename());
        pending_uploads.pending_files.add(daylogfile);
        Log.e("LOGUPLOADER", "Added a log file for future upload: "+daylog.getFilename());
        savePendingUploadsList();
    }

    public void uploadPending() {

        // Task to upload the data to the webserver
        AsyncTask<Void, Void, String> a = new AsyncTask<Void, Void, String>(){

            ArrayList<Integer> completed = new ArrayList<>();

            @Override
            protected String doInBackground(Void... params) {

                // Loop through the pending files and upload them
                ArrayList<Integer> completed = new ArrayList<>();
                int i = -1;

                for (File pending : pending_uploads.pending_files) {
                    i++;
                    DayLog to_upload;
                    boolean loadedSuccessfully = false;
                    try {
                        FileInputStream fis = context.openFileInput(pending.getName());
                        ObjectInputStream is = new ObjectInputStream(fis);
                        to_upload = (DayLog) is.readObject();
                        is.close();
                        fis.close();
                        loadedSuccessfully = true;

                        Log.e("LOGUPLOADER", "Found log for pending upload: "+pending.getName());
                        if (loadedSuccessfully) {
                            boolean success = uploadOne(to_upload);
                            if (success) {
                                completed.add(new Integer(i));
                            }
                        }

                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        Log.e("LOGUPLOADER", "Upload file not found: " + pending.getName());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
                Log.e("LOGUPLOADER", "Uploaded "+completed.size()+" pending day logs to webserver");
                Toast.makeText(context, "Uploaded "+completed.size()+" pending day logs to webserver", Toast.LENGTH_LONG).show();

                // Remove the successfully completed ones
                for (int i = completed.size() - 1; i >= 0; i--) {
                    pending_uploads.pending_files.remove(completed.get(i).intValue());
                }

            }
        };
        a.execute();
    }

    // Task to upload just one log. Must be called from an async task
    public boolean uploadOne (DayLog daylog) {
        // Save a temporary file with the json contents
        FileOutputStream fos = null;
        SimpleDateFormat outdf = new SimpleDateFormat("yyyy-MM-dd");
        final String datestr = outdf.format(daylog.date);
        final String logfilename = "tmp_daylog.json";
        try {
            fos = context.openFileOutput(logfilename, MODE_PRIVATE);
            PrintStream prt = new PrintStream(fos);
            prt.print(daylog.getJSONOutput());
            prt.close();
            fos.close();
            Log.e("LOGUPLOADER", "Saved to file "+logfilename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e("LOGUPLOADER", "Save, File not found" + logfilename);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("LOGUPLOADER", "Save, IO Exception" + logfilename);
        }

        final Context context = this.context;
        final DayLog daylog_f = daylog;

        String charset = "UTF-8";
        String requestURL = settings.getString("upload_log_url", "NO_URL_PROVIDED");
        String response = ""; // response from server.
        String usercredentials = null; // If any HTTP basic auth username/password
        if (settings.getString("upload_log_username", null) != null &&
                settings.getString("upload_log_password", null) != null) {
            usercredentials = settings.getString("upload_log_username", null) + ":" +
                    settings.getString("upload_log_password", null);
        }
        try {
            // Assemble the HTTP post request
            MultipartUtility multipart = new MultipartUtility(requestURL, charset, usercredentials);
            multipart.addFormField("date", datestr);
            multipart.addFilePart("logfile", new File(context.getFilesDir(), logfilename));

            // Find all GPX files
            boolean at_least_one_gpx = false;
            ArrayList<File> gpx_files = new ArrayList<File>();
            for (TowEntry tow : daylog_f.tows) {
                if (tow.gpx_filename != null) {
                    gpx_files.add(new File(context.getFilesDir(), tow.gpx_filename));
                    at_least_one_gpx = true;
                }
            }

            // Give a hint as to how many tracks to expect
            multipart.addFormField("n_tracks", String.valueOf(gpx_files.size()));

            // Add all the tracks
            int i = 0;
            for (File track : gpx_files) {
                multipart.addFilePart("file_"+String.valueOf(i), track);
                multipart.addFormField("filename_"+String.valueOf(i), track.getName());
                i++;
            }

            // Send the request
            response = multipart.finish();
            Log.e("LOGUPLOADER", "Completed uploading one day, response: "+ response);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("LOGUPLOADER", "Failed to upload one day: "+e.toString());
        }
        return false;
    }
}
