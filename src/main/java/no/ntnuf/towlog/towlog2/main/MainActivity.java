package no.ntnuf.towlog.towlog2.main;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OptionalDataException;
import java.io.StreamCorruptedException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import no.ntnuf.towlog.towlog2.common.ColoringUtil;
import no.ntnuf.towlog.towlog2.common.Contact;
import no.ntnuf.towlog.towlog2.dayoverview.DayOverviewActivity;
import no.ntnuf.tow.towlog2.R;
import no.ntnuf.towlog.towlog2.common.ContactListManager;
import no.ntnuf.towlog.towlog2.common.DayLog;
import no.ntnuf.towlog.towlog2.fiken.FikenContactRequestTask;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class MainActivity extends AppCompatActivity {

    private final String dayLogFileNamePrefix = "daylog_";
    private String dayLogFileNameSuffix = "";


    private Button startDayButton;
    private Button resumeDayButton;
    private DatePicker datepicker;

    private Toolbar toolbar;
    private AlertDialog daylogdialog;
    private AlertDialog loadfikencontactsdialog;

    private Contact selectedTowPilot;
    private Date selecteddate = new Date();

    private ArrayList<String> countries = new ArrayList<String>();

    private ContactListManager contactlistmanager;
    private AutoCompleteTextView towPilotNameIn;
    private ImageView towPilotCheckmark;
    private EditText towPlaneIn;

    private boolean found_daylog = false;
    private DayLog daylog;

    private SharedPreferences settings;

    private boolean autoLoadLog = true;

    private Calendar today;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = (Toolbar) findViewById(R.id.toolbarmain);
        toolbar.setTitle("Prepare Towing");
        setSupportActionBar(toolbar);

        settings = PreferenceManager.getDefaultSharedPreferences(this);

        // Set up the tow pilot name input, including autocomplete and hasAccount check
        contactlistmanager = new ContactListManager(this);

        towPilotCheckmark = (ImageView) findViewById(R.id.towPilotCheckmark);

        towPilotNameIn = (AutoCompleteTextView) findViewById(R.id.towPilotNameIn);
        towPilotNameIn.setAdapter(contactlistmanager.getContactNameListAdapter());
        towPilotNameIn.addTextChangedListener(new TextWatcher() {
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Contact selected = contactlistmanager.findContactFromName(String.valueOf(s));
                selectedTowPilot = selected;
                if (selected == null) {
                    towPilotCheckmark.setBackgroundResource(0);
                } else {
                    if (selected.hasAccount) {
                        towPilotCheckmark.setBackgroundResource(R.mipmap.green_checkmark);
                    } else {
                        towPilotCheckmark.setBackgroundResource(R.mipmap.new_icon_blue);
                    }
                }
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void afterTextChanged(Editable s) {
            }
        });


        // Set up towplane input
        towPlaneIn = (EditText) findViewById(R.id.towPlaneIn);
        towPlaneIn.setText(settings.getString("lasttowplane", settings.getString("towplane_default_reg","")));


        // Set up Datepicker
        datepicker = (DatePicker) findViewById(R.id.datePicker);
        today = Calendar.getInstance();
        datepicker.init(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH), new DatePicker.OnDateChangedListener() {
            @Override
            public void onDateChanged(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                Calendar c = Calendar.getInstance();
                c.set(year, monthOfYear, dayOfMonth);
                selecteddate = c.getTime();

                dayLogFileNameSuffix = String.valueOf(year) + "_" + String.valueOf(monthOfYear + 1) +
                        "_" + String.valueOf(dayOfMonth);

                Log.e("DATEPICKER", dayLogFileNameSuffix);

                if (loadDayLog()) {
                    found_daylog = true;
                    resumeDayButton.setVisibility(View.VISIBLE);
                } else {
                    found_daylog = false;
                    resumeDayButton.setVisibility(View.INVISIBLE);
                }
            }
        });

        Log.e("DATEPICKER", "Inited object, " + datepicker);

        // Start new day button
        startDayButton = (Button) findViewById(R.id.startDayButton);
        ColoringUtil.colorMe(startDayButton, getResources().getColor(R.color.colorPrimary));
        startDayButton.setTextColor(getResources().getColor(R.color.white));
        startDayButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (found_daylog) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setMessage("There is already a log for this date. " +
                                    "Are you sure you want to overwrite it?")
                            .setCancelable(false)
                            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    startNewDay();
                                }
                            })
                            .setNegativeButton("No", null)
                            .show();

                } else {
                    startNewDay();
                }
            }
        });


        // Resume day button
        resumeDayButton = (Button) findViewById(R.id.resumeDayButton);
        ColoringUtil.colorMe(resumeDayButton, getResources().getColor(R.color.resumeday_button));
        resumeDayButton.setTextColor(getResources().getColor(R.color.white));
        resumeDayButton.setVisibility(View.INVISIBLE);
        resumeDayButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String action = "resume";
                Intent intent = new Intent(MainActivity.this, DayOverviewActivity.class);
                Bundle bundle = bundleDayInfo();
                bundle.putSerializable("action", action);
                bundle.putSerializable("reason", "Resuming day");
                intent.putExtras(bundle);
                startActivityForResult(intent, 0);
            }
        });



        // Various dialogs for the menus
        daylogdialog = getPrevLogsAlertDialog();
        loadfikencontactsdialog = getLoadFikenContactsAlertDialog();

    }

    // Helper function to start a new day
    private void startNewDay() {
        // Add the pilot so we can autocomplete it later
        contactlistmanager.saveContact(towPilotNameIn.getText().toString().trim());

        // Add tow info to the bundle
        String action = "new";
        Intent intent = new Intent(MainActivity.this, DayOverviewActivity.class);
        Bundle bundle = bundleDayInfo();
        bundle.putSerializable("action", action);

        // Start next activity, DayOverview
        intent.putExtras(bundle);
        startActivity(intent);
    }

    // Helper function to oncreate, get an alertdialog for showing available logs
    private AlertDialog getPrevLogsAlertDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Previous logs");
        final CharSequence[] daysavailable = availableDayLogs();
        builder.setItems(daysavailable, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dayLogFileNameSuffix = getFilenameFromListpos(which);

                // Update the date fields passed on to dayoverviewactivity
                String[] daylogfilenamesplit = dayLogFileNameSuffix.split("_");
                int year = Integer.valueOf(daylogfilenamesplit[0]);
                int monthOfYear = Integer.valueOf(daylogfilenamesplit[1]) - 1;
                int dayOfMonth = Integer.valueOf(daylogfilenamesplit[2]);

                Calendar c = Calendar.getInstance();
                c.set(year, monthOfYear, dayOfMonth);
                selecteddate = c.getTime();

                // Call the resume day button which handles the dayoverview activity starting
                resumeDayButton.callOnClick();
            }
        });
        return builder.create();
    }

    // Helper function to oncreate, get an alertdialog for loading contacts from fiken
    private AlertDialog getLoadFikenContactsAlertDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Load Contacts from Fiken");
        builder.setMessage("Connecting to Fiken...");
        return builder.create();
    }

    // Helper function
    // Get the filename suffix from an index, corresponding to the position
    // of the file list
    private String getFilenameFromListpos(int which) {
        String[] listOfFiles = this.getFilesDir().list();
        List<String> arrayOfFiles = new ArrayList<String>(Arrays.asList(listOfFiles));
        Collections.sort(arrayOfFiles);

        int c = 0;
        for (int i = 0; i < arrayOfFiles.size(); i++) {
            String name = arrayOfFiles.get(i);
            // Count only the files that contain daylogs
            if (name.startsWith(dayLogFileNamePrefix)) {
                if (c == which) {
                    return name.replaceFirst(dayLogFileNamePrefix, "");
                }
                c++;
            }
        }
        return "";
    }

    // Search files for available logs
    private CharSequence[] availableDayLogs() {
        String[] listOfFiles = this.getFilesDir().list();
        ArrayList<String> arrayOfFiles = new ArrayList<String>(Arrays.asList(listOfFiles));
        Collections.sort(arrayOfFiles);

        ArrayList<String> s = new ArrayList<String>();

        SimpleDateFormat df = new SimpleDateFormat("yyyy M d", Locale.ENGLISH);
        SimpleDateFormat outdf = new SimpleDateFormat("cccc d/M/yyyy");

        for (int i = 0; i < arrayOfFiles.size(); i++) {
            String name = arrayOfFiles.get(i);
            // Add only the files that contain daylogs
            if (name.startsWith(dayLogFileNamePrefix)) {
                // Prettify it
                String[] namesplit = name.split("_");
                if (namesplit.length == 4) {

                    Date result = null;
                    try {
                        String parsestring = namesplit[1] + " " + namesplit[2] + " " + namesplit[3];
                        result = df.parse(parsestring);
                        String print = outdf.format(result);
                        s.add(print);
                    } catch (ParseException e) {
                        s.add("Parseerror, name: "+name);
                    }

                } else {
                    // Messed up formatting of the filename
                    s.add("Parse error, split: "+name);
                }
            }
        }

        final CharSequence[] cs = s.toArray(new String[s.size()]);
        return cs;
    }

    // Helper function to pack up the tow pilot info and the date
    private Bundle bundleDayInfo() {
        Bundle bundle = new Bundle();
        Contact towpilot;
        if (selectedTowPilot == null) {
            towpilot = new Contact();
            towpilot.name = towPilotNameIn.getText().toString().trim();
        } else {
            towpilot = selectedTowPilot;
        }
        String towplane = towPlaneIn.getText().toString().trim();
        Date d = selecteddate;

        bundle.putSerializable("towpilot", towpilot);
        bundle.putSerializable("towplane", towplane);
        bundle.putSerializable("date", d);
        return bundle;
    }

    // Helper function to load the day log from a file
    private boolean loadDayLog() {
        boolean res = false;
        String fullfilename = dayLogFileNamePrefix + dayLogFileNameSuffix;
        try {
            FileInputStream fis = this.openFileInput(fullfilename);
            ObjectInputStream is = new ObjectInputStream(fis);
            daylog = (DayLog) is.readObject();
            is.close();
            fis.close();
            res = true;
            //Log.e("Main Load", "Existing log for this date found: " + fullfilename);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            this.deleteFile(dayLogFileNamePrefix + dayLogFileNameSuffix);
            Toast.makeText(MainActivity.this, "Broken file selected, deleting it...",
                    Toast.LENGTH_LONG).show();
        } catch (OptionalDataException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e("MAIN", "Logload, No log for this date found " + fullfilename);
        } catch (StreamCorruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res;
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    // Menu selector. Starts async tasks if needed.
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Show the list of days
            case R.id.menu_listtowlogs:
                daylogdialog.show();
                return true;

            case R.id.menu_loadfikencontacts:
                FikenContactRequestTask fikenRequest = new FikenContactRequestTask();
                fikenRequest.setContext(this);
                fikenRequest.setDialog(loadfikencontactsdialog);
                fikenRequest.setContactListManager(contactlistmanager);
                fikenRequest.execute();
                return true;

            case R.id.menu_settings:
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
                return true;


            // Nothing to do
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // The main activity behaves differently depending on how we return from it from other activities
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.e("MAIN", "onActivityResult() called");

        if (data != null) {
            Bundle bundle = data.getExtras();
            String action = (String) bundle.getSerializable("action");
            if (action.equals("backbutton")) {
                // Close app immediately if we return due to a back button press
                finish();
                return;
            } else if (action.equals("backtocalendarmenu")) {
                // If we return back from daylog, stay on this activity
                autoLoadLog = false;
                return;
            }
        }

        // If you got here otherwise, you came from the "back to calendar" menu option in daylog
        // If we return back from daylog, stay on this activity
        //autoLoadLog = false;
    }

    // Refresh the view when going back
    protected void onResume() {
        super.onResume();
        Log.e("MAIN", "onResume() called");

        today = Calendar.getInstance();

        // Day log loader, check if we already have a log for the current date
        dayLogFileNameSuffix = String.valueOf(datepicker.getYear()) + "_" +
                String.valueOf(datepicker.getMonth()+1) + "_" +
                String.valueOf(datepicker.getDayOfMonth());
        if (loadDayLog()) {
            found_daylog = true;
            resumeDayButton.setVisibility(View.VISIBLE);

            // Jump straight to daylog if we are currently on the shown date from a clean startup
            int day = datepicker.getDayOfMonth();
            int month = datepicker.getMonth();
            int year = datepicker.getYear();
            Calendar date_picked = Calendar.getInstance();
            date_picked.set(year, month, day);

            if (autoLoadLog && date_picked.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    date_picked.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
                resumeDayButton.callOnClick();
            }
        } else {
            found_daylog = false;
            resumeDayButton.setVisibility(View.INVISIBLE);
        }

        // Subsequent resumes will autoload logs again
        autoLoadLog = true;

        // Reload the contact list
        contactlistmanager = new ContactListManager(this);
        towPilotNameIn.setAdapter(contactlistmanager.getContactNameListAdapter());

        // Set the daylog dialog
        daylogdialog = getPrevLogsAlertDialog();

        // To avoid focus going to the text fields
        datepicker.requestFocus();

    }

}
