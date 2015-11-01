package no.ntnuf.tow.towlog2;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.StreamCorruptedException;

public class DayOverviewActivity extends AppCompatActivity {

    private DayLog daylog;

    TableLayout tableLayout;

    private final String dayLogFileName = "daylogfilename";

    //final Handler handler = new Handler();

    // Helper function to add a tow row
    private TableRow addTowToTable(TowEntry tow, int townumber) {
        TableRow tr = new TableRow(this);

        TextView towno = new TextView(this);
        towno.setText(String.valueOf(townumber));
        towno.setPadding(0,0,5,0);
        tr.addView(towno);

        TextView ttreg = new TextView(this);
        ttreg.setText(tow.registration);
        ttreg.setPadding(0, 0, 5, 0);
        tr.addView(ttreg);

        TextView pilot = new TextView(this);
        pilot.setText(tow.pilotname);
        pilot.setPadding(0, 0, 5, 0);
        tr.addView(pilot);

        TextView copilot = new TextView(this);
        copilot.setText(tow.copilotname);
        copilot.setPadding(0, 0, 5, 0);
        tr.addView(copilot);

        TextView height = new TextView(this);
        height.setText(String.valueOf(tow.height));
        height.setPadding(0, 0, 5, 0);
        tr.addView(height);

        return tr;
    }

    private void refreshTowTable() {
        // Clear everything except the header row
        final int header_rows = 2;
        tableLayout.removeViews(header_rows, tableLayout.getChildCount() - header_rows);

        // Add the already existing tows to the table
        int townumber = 0;
        for (TowEntry tow : daylog.tows) {
            townumber++;

            TableRow tbv = addTowToTable(tow, townumber);

            tableLayout.addView(tbv, new TableLayout.LayoutParams(TableLayout.LayoutParams.WRAP_CONTENT, TableLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_day_overview);

        tableLayout = (TableLayout) findViewById(R.id.dayOverViewTableLayout);

        Bundle bundle = getIntent().getExtras();
        String action = (String) bundle.getSerializable("value");

        // Load/create daylog
        Log.e("LOG", "action: "+action);
        if (action.equals("new") || !loadDayLog()) {
            if (action == "new") {
                Log.e("LOG", "Creating new day log");
            } else {
                Log.e("LOG", "Unable to load previous log, so creating a new one");
            }
            daylog = new DayLog(this);

            Snackbar.make(tableLayout, "New daily log", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();

        } else {
            Snackbar.make(tableLayout, "Loaded daily log", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }

        // TODO: Add some test data
        for (int i = 0; i < 0; i ++) {
            TowEntry t1 = new TowEntry();
            t1.pilotname = "Jens Johansen Jesefsen Juborg Jaglang Jesus";
            t1.registration = "LN-GCN";
            t1.height=((i+3)%15)*100;
            daylog.tows.add(t1);
            TowEntry t2 = new TowEntry();
            t2.pilotname = "Ola Normann";
            t2.copilotname = "Stine Johansen";
            t2.registration = "LN-GCG";
            t2.height=(i%15)*100;
            daylog.tows.add(t2);
        }

        refreshTowTable();

        // Add new tow activity
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DayOverviewActivity.this, NewTowActivity.class);
                startActivityForResult(intent, 0);
            }
        });

    }

    @Override
    // Return towentry from the started activity
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Grab the tow entry and add it to the daylog
        if (resultCode==RESULT_OK) {
            Bundle bundle = data.getExtras();
            TowEntry towentry = (TowEntry) bundle.getSerializable("value");
            daylog.tows.add(towentry);


            // Refresh the table view
            refreshTowTable();
            Snackbar.make(tableLayout, "Tow saved", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            saveDayLog();
        }
    }



    // Load the day log from a file
    private boolean loadDayLog() {
        boolean res = false;
        try {
            FileInputStream fis = this.openFileInput(dayLogFileName);
            ObjectInputStream is = new ObjectInputStream(fis);
            daylog = (DayLog) is.readObject();
            is.close();
            fis.close();
            res = true;
            Log.e("LOAD", "File found!");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (OptionalDataException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e("LOAD", "File not found");
        } catch (StreamCorruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res;
    }

    // Save the day log to a file
    private void saveDayLog() {
        // Save the daylog
        FileOutputStream fos = null;
        try {
            fos = this.openFileOutput(dayLogFileName, this.MODE_PRIVATE);
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(daylog);
            os.close();
            fos.close();
            Log.e("SAVE", "Saved to file "+dayLogFileName);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e("SAVE", "File not found" + dayLogFileName);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("SAVE", "IO Exception" + dayLogFileName);
        }
    }


    @Override
    protected void onPause() {
        super.onPause();

        // Save the day log on pause
        //saveDayLog();
    }
}
