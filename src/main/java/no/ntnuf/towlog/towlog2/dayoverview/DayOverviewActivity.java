package no.ntnuf.towlog.towlog2.dayoverview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
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
import java.util.Calendar;
import java.util.Date;
import java.util.zip.ZipOutputStream;

import no.ntnuf.tow.towlog2.R;
import no.ntnuf.towlog.towlog2.common.LogUploader;
import no.ntnuf.towlog.towlog2.common.MultipartUtility;
import no.ntnuf.towlog.towlog2.common.ZipUtils;
import no.ntnuf.towlog.towlog2.duringtowing.GPXGenerator;
import no.ntnuf.towlog.towlog2.main.SettingsActivity;
import no.ntnuf.towlog.towlog2.common.Contact;
import no.ntnuf.towlog.towlog2.common.ContactListManager;
import no.ntnuf.towlog.towlog2.common.DayLog;
import no.ntnuf.towlog.towlog2.common.RegistrationList;
import no.ntnuf.towlog.towlog2.common.TowEntry;
import no.ntnuf.towlog.towlog2.fiken.FikenContactRequestTask;
import no.ntnuf.towlog.towlog2.fiken.FikenInvoicePushTask;
import no.ntnuf.towlog.towlog2.newtow.NewTowActivity;


public class DayOverviewActivity extends AppCompatActivity {

    private DayLog daylog;

    private TableLayout tableLayout;
    private Toolbar toolbar;
    private AlertDialog loadfikencontactsdialog;
    private FloatingActionButton floatingactionbutton;

    private final String dayLogFileName = "daylog_";
    private String daylogsuffix = "";

    private Menu menu;

    private boolean editMode = false;

    private SharedPreferences settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_day_overview);

        Log.e("DAYOVERVIEW", "onCreate() called");

        Bundle bundle;
        if (savedInstanceState != null) {
            bundle = savedInstanceState;
        } else {
            bundle = getIntent().getExtras();
        }

        settings = PreferenceManager.getDefaultSharedPreferences(this);

        // Date and action are mandatory
        String action = (String) bundle.getSerializable("action");
        Date date = (Date) bundle.getSerializable("date");

        // Tow pilot and plane are optional (only needed for new logs)
        Contact towpilot = (Contact) bundle.getSerializable("towpilot");
        String towplane = (String) bundle.getSerializable("towplane");
        SimpleDateFormat outdf = new SimpleDateFormat("cccc d/M");
        String strdate = outdf.format(date);

        toolbar = (Toolbar) findViewById(R.id.toolbardayoverview);
        String toolbartitle = "Day Log" + "  -  " + strdate;
        toolbar.setTitle(toolbartitle);
        setSupportActionBar(toolbar);

        tableLayout = (TableLayout) findViewById(R.id.dayOverViewTableLayout);

        // Load/create daylog
        if (action.equals("new") || !loadDayLog(date)) {
            if (action.equals("new")) {
                Log.e("DAYOVERVIEW", "Creating new day log for date "+date.toString());
                Snackbar.make(tableLayout, "New daily log", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                daylog = new DayLog(this);
                daylog.towpilot = towpilot;
                daylog.towplane = towplane;
                daylog.date = date;

                saveDayLog();

            } else {
                Log.e("DAYOVERVIEW", "Unable to load previous log, so creating a new one");
                Snackbar.make(tableLayout, "Problem loading old log!",
                        Snackbar.LENGTH_LONG).setAction("Action", null).show();
            }

        } else {
            Snackbar.make(tableLayout, "Resumed daily log", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }

        // Add all the tows that exist
        refreshTowTable();

        // Button to add new tow activity
        floatingactionbutton = (FloatingActionButton) findViewById(R.id.fab);
        floatingactionbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DayOverviewActivity.this, NewTowActivity.class);
                startActivityForResult(intent, 1);
            }
        });

        // Add a connectivity listener, waiting for when we get internet connection back
        final Context context = this;
        ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        cm.addDefaultNetworkActiveListener(new ConnectivityManager.OnNetworkActiveListener() {
            @Override
            public void onNetworkActive() {
                Log.e("DAYOVERVIEW", "Network connection active, checking for pending uploads");
                LogUploader uploader = new LogUploader(context, settings);
                uploader.uploadPending();
            }
        });

        // Alert dialog for fiken contact loading
        loadfikencontactsdialog = getLoadFikenContactsAlertDialog();

    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e("DAYOVERVIEW", "onResume() called");
    }

    // Add the menu
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        getMenuInflater().inflate(R.menu.dayoverview_menu, menu);

        floatingactionbutton.setVisibility(daylog.logIsLocked ? View.INVISIBLE : View.VISIBLE);
        menu.findItem(R.id.menu_reenablelog).setVisible(daylog.logIsLocked);
        menu.findItem(R.id.menu_editlog).setVisible(!daylog.logIsLocked);
        menu.findItem(R.id.menu_deletedaylog).setVisible(!daylog.logIsLocked);

        return true;
    }

    // Handle clicks on the menu
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Email the day log
            case R.id.menu_senddaylog:
                if (daylog.logIsLocked) {
                    new AlertDialog.Builder(this)
                            .setMessage("It looks like this log has already been sent. " +
                                    "Are you sure you want to resend it?")
                            .setCancelable(false)
                            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    sendLog();
                                }
                            })
                            .setNegativeButton("No", null)
                            .show();
                } else {
                    sendLog();
                }
                return true;

            // Clear the pilot list
            case R.id.menu_clearpilotlist:
                new AlertDialog.Builder(this)
                        .setMessage("Are you sure you want to clear the list of pilots? " +
                                "This only affects autocomplete hints. The change is visible on  " +
                                "the next application launch.")
                        .setCancelable(false)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                ContactListManager pilotlist = new ContactListManager(getApplicationContext());
                                pilotlist.clearList();
                            }
                        })
                        .setNegativeButton("No", null)
                        .show();
                return true;

            // Clear the registration list
            case R.id.menu_clearregistrationlist:
                new AlertDialog.Builder(this)
                        .setMessage("Are you sure you want to clear the list of registrations? " +
                                "This only affects autocomplete hints. The change is visible on  " +
                                "the next application launch.")
                        .setCancelable(false)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                RegistrationList reglist = new RegistrationList(getApplicationContext());
                                reglist.clearList();
                            }
                        })
                        .setNegativeButton("No", null)
                        .show();
                return true;

            // Edit the day log manually (move up down, delete)
            case R.id.menu_editlog:
                editMode = !editMode;
                MenuItem edit = menu.findItem(R.id.menu_editlog);
                if (editMode) {
                    edit.setTitle("Finish log editing");
                } else {
                    edit.setTitle("Manually edit log");
                }
                refreshTowTable();
                return true;

            // Delete this daylog
            case R.id.menu_deletedaylog:
                new AlertDialog.Builder(this)
                        .setMessage("Are you sure you want to delete this day? " +
                                "This change can not be undone. ")
                        .setCancelable(false)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                deleteDayLog();
                                finish();
                            }
                        })
                        .setNegativeButton("No", null)
                        .show();
                return true;

            // Load lists of contacts from Fiken accounting program
            case R.id.menu_loadfikencontacts:
                FikenContactRequestTask fikenRequest = new FikenContactRequestTask();
                fikenRequest.setContext(this);
                fikenRequest.setDialog(loadfikencontactsdialog);
                fikenRequest.setContactListManager(new ContactListManager(this));
                fikenRequest.execute();
                return true;

            // Upload log directly to Fiken for invoicing (TODO: does not work)
            /*
            case R.id.menu_uploadtofiken:
                FikenInvoicePushTask fikenInvoice = new FikenInvoicePushTask();
                fikenInvoice.setContext(this);
                fikenInvoice.setDialog(loadfikencontactsdialog);
                fikenInvoice.setDayLog(daylog);
                fikenInvoice.execute();
                return true;
            */

            // If the menu was locked before, unlock it now
            case R.id.menu_reenablelog:
                daylog.logIsLocked = false;
                saveDayLog();
                if (menu != null) {
                    menu.findItem(R.id.menu_reenablelog).setVisible(false);
                    menu.findItem(R.id.menu_editlog).setVisible(true);
                    menu.findItem(R.id.menu_deletedaylog).setVisible(true);
                }
                floatingactionbutton.setVisibility(View.VISIBLE);
                return true;

            case R.id.menu_settings:
                Intent intent = new Intent(DayOverviewActivity.this, SettingsActivity.class);
                startActivity(intent);
                return true;

            // End the activity and go back to the overview calendar
            case R.id.menu_backtocalendar:
                Intent response = new Intent();
                Bundle bundle = new Bundle();

                bundle.putSerializable("action", "backtocalendarmenu");
                response.putExtras(bundle);
                setResult(Activity.RESULT_OK, response);
                finish();
                return true;

            // Nothing to do
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // Root function for sending log
    // First uploads the log, then emails it
    public void sendLog() {
        // Pack up and send the logs
        if (settings.getBoolean("upload_log_enabled", false)) {
            LogUploader uploader = new LogUploader(this, settings);

            uploader.addToUploadQueue(this.daylog);
            uploader.uploadPending();
        }
        sendLogViaEmail();
    }

    // Send logfile using email (create intent and send off to email application)
    public void sendLogViaEmail() {


        SimpleDateFormat outdf = new SimpleDateFormat("cccc d/M/yyyy");
        String dstr = outdf.format(daylog.date);

        SimpleDateFormat df_file = new SimpleDateFormat("yyyy_M_d");
        String d_file = df_file.format(daylog.date);

        String towlogfilename = "towlog_"+d_file+".html";
        String receiver_email = settings.getString("send_log_email","");

        try {
            // Write the HTML version of the log to a file
            File dir = this.getExternalCacheDir();
            File towlogfile = new File(dir, towlogfilename);
            towlogfile.createNewFile();
            FileOutputStream fios = new FileOutputStream(towlogfile);
            //FileOutputStream fios = openFileOutput(towlogfilename,
            //        Context.MODE_WORLD_READABLE);
            fios.write(daylog.csv2Html(daylog.getCsvOutput()).getBytes());
            fios.close();

            // Then add that HTML file as an attachment to the email
            File file = towlogfile; //getFileStreamPath(towlogfilename);

            // Also add the CSV version to the email directly
            Intent emailintent = new Intent(Intent.ACTION_SEND);
            emailintent.setType("text/plain");
            emailintent.putExtra(Intent.EXTRA_EMAIL, new String[]{receiver_email});
            emailintent.putExtra(Intent.EXTRA_SUBJECT, "Tow Log for " + dstr);
            emailintent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
            emailintent.putExtra(Intent.EXTRA_TEXT, daylog.getCsvOutput());

            // If this setting is enabled, add all the emails to pilots to the BCC
            // list of the email
            if (settings.getBoolean("send_log_to_customers", false)) {
                ArrayList<String> customerlist = new ArrayList<>();
                if (daylog.towpilot != null && daylog.towpilot.email != null) {
                    customerlist.add(daylog.towpilot.email);
                }
                for (TowEntry t : daylog.tows) {
                    if (t.pilot != null && t.pilot.email != null) {
                        customerlist.add(t.pilot.email);
                    }
                    if (t.copilot != null && t.copilot.email != null) {
                        customerlist.add(t.copilot.email);
                    }
                }
                String[] cl = customerlist.toArray(new String[customerlist.size()]);
                if (settings.getBoolean("send_log_to_customers_using_bcc", true)) {
                    emailintent.putExtra(Intent.EXTRA_BCC, cl);
                } else {
                    emailintent.putExtra(Intent.EXTRA_CC, cl);
                }

            }

            startActivityForResult(Intent.createChooser(emailintent, "Send email using"), 2);



            // Mark the log as sent, disable various menu options
            daylog.setLogHasBeenSent();
            floatingactionbutton.setVisibility(View.INVISIBLE);
            if (menu != null) {
                menu.findItem(R.id.menu_reenablelog).setVisible(true);
                menu.findItem(R.id.menu_editlog).setVisible(false);
                menu.findItem(R.id.menu_deletedaylog).setVisible(false);
            }
            saveDayLog();

            Log.e("EMAIL", "Sending email ");
        }  catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Create an alertdialog for loading contacts from fiken
    private AlertDialog getLoadFikenContactsAlertDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Load Contacts from Fiken");
        builder.setMessage("Connecting to Fiken...");
        return builder.create();
    }

    // Return towentry from the started activity (only for tow activity results, type 0)
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.e("DAYOVERVIEW", "onActivityResult() called");

        // Grab the tow entry and add it to the daylog
        if (resultCode==RESULT_OK && requestCode == 1) {
            Bundle bundle = data.getExtras();
            TowEntry towentry = (TowEntry) bundle.getSerializable("value");
            daylog.tows.add(towentry);

            Log.e("DAYOVERVIEW", "onActivityResult() result OK");

            // Extract and write out the GPX content to file
            if (towentry.gpx_body != null) {
                Log.e("GPXGEN_RETURN", "GPX body not null:  "+towentry.gpx_body);
                String filename = GPXGenerator.storeGPX(this, daylog, towentry, towentry.gpx_body);
                if (filename != null) {
                    towentry.gpx_filename = filename;
                    towentry.gpx_body = null;
                    Log.e("GPXGEN_RETURN", "Saved a GPX file: "+filename);
                }
            }

            // Refresh the table view
            refreshTowTable();
            Snackbar.make(tableLayout, "Tow saved", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            saveDayLog();
        }
    }


    // Helper function to add a tow row
    private TableRow addTowToTable(TowEntry tow, int townumber) {
        TableRow tr = new TableRow(this);

        // Scale *somewhat* correctly for different screen densities...
        final float scale = this.getResources().getDisplayMetrics().density;
        float textsize_dp;
        if (scale <= 1.0) {
            textsize_dp = 24;
        } else {
            textsize_dp = 5;
        }
        float textsize = (textsize_dp * scale + 0.5f);

        TextView towno = new TextView(this);
        towno.setText(String.valueOf(townumber));
        towno.setTextSize(textsize);
        towno.setPadding(0, 0, 5, 0);
        tr.addView(towno);

        TextView ttreg = new TextView(this);
        ttreg.setText(tow.registration);
        ttreg.setTypeface(null, Typeface.BOLD);
        ttreg.setTextSize(textsize);
        ttreg.setPadding(0, 0, 5, 0);

        TextView pilot = new TextView(this);
        pilot.setText(tow.pilot.name);
        pilot.setTextSize(textsize);
        pilot.setPadding(0, 0, 5, 0);

        TextView copilot = new TextView(this);
        if (tow.copilot != null) {
            copilot.setText(tow.copilot.name);
            copilot.setTextSize(textsize);
            copilot.setPadding(0, 0, 5, 0);
        }

        TextView notes = new TextView(this);
        if (tow.notes != "") {
            notes.setText(tow.notes);
            notes.setTextSize(textsize);
            notes.setPadding(0, 0, 5, 0);
            notes.setTypeface(null, Typeface.ITALIC);
        }

        RelativeLayout regandbuttons = new RelativeLayout(this);
        RelativeLayout.LayoutParams myparam = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.FILL_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        regandbuttons.setLayoutParams(myparam);
        regandbuttons.addView(ttreg);

        // If edit mode is enabled, add buttons for moving entries up and down the list, plus delete
        if (editMode) {
            final int towindex = townumber - 1;

            RelativeLayout.LayoutParams right = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            right.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            LinearLayout buttons = new LinearLayout(this);
            buttons.setLayoutParams(right);

            ImageView down = new ImageView(this);
            down.setImageResource(R.mipmap.down_circle);
            down.setPadding(50, 0, 25, 0);
            down.setMaxHeight((int) (textsize / 1.5));
            down.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    daylog.moveLogLine(false, towindex);
                    refreshTowTable();
                    saveDayLog();
                }
            });

            ImageView up = new ImageView(this);
            up.setImageResource(R.mipmap.up_circle);
            up.setPadding(25, 0, 25, 0);
            up.setMaxHeight((int) (textsize / 1.5));
            up.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    daylog.moveLogLine(true, towindex);
                    refreshTowTable();
                    saveDayLog();
                }
            });

            ImageView delete = new ImageView(this);
            delete.setImageResource(R.mipmap.x_circle);
            delete.setPadding(50, 0, 25, 0);
            delete.setMaxHeight((int) (textsize / 1.5));
            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(DayOverviewActivity.this)
                            .setMessage("Are you sure you want to delete this line?")
                            .setCancelable(false)
                            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    daylog.deleteLogLine(towindex);
                                    refreshTowTable();
                                    saveDayLog();
                                }
                            })
                            .setNegativeButton("No", null)
                            .show();

                }
            });

            buttons.addView(down);
            buttons.addView(up);
            buttons.addView(delete);

            regandbuttons.addView(buttons);
        }

        LinearLayout regAndNames = new LinearLayout(this);
        regAndNames.setOrientation(LinearLayout.VERTICAL);

        regAndNames.addView(regandbuttons);
        regAndNames.addView(pilot);
        if (tow.copilot != null) {
            regAndNames.addView(copilot);
            //Log.e("MM","I have a copilot");
        } else {
            //Log.e("MM","Empty copilot");
        }
        if (tow.notes != "") {
            regAndNames.addView(notes);
            //Log.e("MM","I have notes:"+tow.notes);
        } else {
            //Log.e("MM", "No note");
        }


        tr.addView(regAndNames);

        LinearLayout heightAndTime = new LinearLayout(this);
        heightAndTime.setOrientation(LinearLayout.VERTICAL);

        TextView height = new TextView(this);
        height.setText(String.valueOf(tow.height) + "m");
        height.setTextSize(textsize);
        height.setPadding(0, 0, 5, 0);
        heightAndTime.addView(height);

        TextView time = new TextView(this);
        SimpleDateFormat outdf = new SimpleDateFormat("HH:mm");
        String dstr = outdf.format(tow.towStarted);
        time.setText(dstr);
        time.setTextSize(textsize);
        time.setPadding(0, 0, 5, 0);
        heightAndTime.addView(time);

        tr.addView(heightAndTime);

        return tr;
    }

    private View rememberMe;

    private void refreshTowTable() {
        // Clear everything except the header row
        final int header_rows = 2;
        tableLayout.removeViews(header_rows, tableLayout.getChildCount() - header_rows);

        // Add the already existing tows to the table
        int townumber = 0;
        for (TowEntry tow : daylog.tows) {
            townumber++;

            // Add the text row
            final TableRow tbv = addTowToTable(tow, townumber);
            tbv.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    editMode = !editMode;

                    rememberMe = tbv;
                    //Snackbar.make(tableLayout, "", Snackbar.LENGTH_LONG)
                    //        .setAction("Action", null).show();
                    refreshTowTable();
                    return false;
                }
            });

            // Add the row separator
            View separator = new View(this);
            separator.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            separator.setBackgroundColor(Color.BLACK);

            tableLayout.addView(tbv, new TableLayout.LayoutParams(TableLayout.LayoutParams.WRAP_CONTENT, TableLayout.LayoutParams.WRAP_CONTENT));
            tableLayout.addView(separator);
        }
    }



    // Load the day log from a file
    private boolean loadDayLog(Date date) {
        boolean loadedSuccessfully = false;
        SimpleDateFormat outdf = new SimpleDateFormat("yyyy_MM_dd");
        String daylogsuffix = outdf.format(date);
        String fullfilename = dayLogFileName+daylogsuffix;
        try {
            FileInputStream fis = this.openFileInput(fullfilename);
            ObjectInputStream is = new ObjectInputStream(fis);
            daylog = (DayLog) is.readObject();
            is.close();
            fis.close();
            loadedSuccessfully = true;
            //Log.e("LOAD", "File found: "+fullfilename);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e("DAYOVERVIEW", "File not found: " + fullfilename);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return loadedSuccessfully;
    }

    // Save the day log to a file
    private void saveDayLog() {
        // Save the daylog
        FileOutputStream fos = null;
        String fullfilename = daylog.getFilename();
        try {
            fos = this.openFileOutput(fullfilename, MODE_PRIVATE);
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(daylog);
            os.close();
            fos.close();
            //Log.e("SAVE", "Saved to file "+fullfilename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e("DAYOVERVIEW", "Save, File not found" + fullfilename);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("DAYOVERVIEW", "Save, IO Exception" + fullfilename);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable("action", "resume");
        outState.putSerializable("date", daylog.date);

        Log.e("DAYOVERVIEW", "onSaveInstanceState() called");
    }


    @Override
    protected void onPause() {
        super.onPause();
        Log.e("DAYOVERVIEW", "onPause() called");
    }

    // Ignore on back pressed, do not do anything with it
    @Override
    public void onBackPressed() {
        Log.e("DATOVERVIEW", "onBackPressed() called");

        if (true)
            return;

        // Old code:
        // Override on back pressed, close the whole app immediately instead of going to the main activity

        // Build response Intent
        Intent response = new Intent();
        Bundle bundle = new Bundle();

        bundle.putSerializable("action", "backbutton");
        response.putExtras(bundle);
        setResult(Activity.RESULT_OK, response);
        finish();
    }

    // Delete the day log
    private boolean deleteDayLog() {
        boolean deleted = this.deleteFile(this.daylog.getFilename());
        return deleted;
    }

}
