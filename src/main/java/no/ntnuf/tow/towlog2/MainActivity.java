package no.ntnuf.tow.towlog2;

import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.location.LocationManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ViewFlipper;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OptionalDataException;
import java.io.StreamCorruptedException;
import java.util.ArrayList;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class MainActivity extends AppCompatActivity {

    private final String dayLogFileName = "daylogfilename";


    private Button startDayButton;
    private Button resumeDayButton;

    private ArrayList<String> countries = new ArrayList<String>();

    PilotList pilotlist;
    AutoCompleteTextView towPilotNameIn;

    boolean found_daylog = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        pilotlist = new PilotList(this);

        startDayButton = (Button) findViewById(R.id.startDayButton);
        startDayButton.getBackground().setColorFilter(getResources().getColor(R.color.colorPrimary), PorterDuff.Mode.MULTIPLY);
        startDayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Add the pilot so we can autocomplete it later
                pilotlist.addPilot(towPilotNameIn.getText().toString());

                String action = "new";
                Intent intent = new Intent(MainActivity.this, DayOverviewActivity.class);
                Bundle bundle = new Bundle();
                bundle.putSerializable("value", action);
                intent.putExtras(bundle);
                startActivity(intent);
            }
        });

        // Resume day button
        resumeDayButton = (Button) findViewById(R.id.resumeDayButton);
        resumeDayButton.getBackground().setColorFilter(getResources().getColor(R.color.colorAccent), PorterDuff.Mode.MULTIPLY);
        resumeDayButton.setVisibility(View.INVISIBLE);
        resumeDayButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String action = "resume";
                Intent intent = new Intent(MainActivity.this, DayOverviewActivity.class);
                Bundle bundle = new Bundle();
                bundle.putSerializable("value", action);
                intent.putExtras(bundle);
                startActivity(intent);
            }
        });



        towPilotNameIn = (AutoCompleteTextView) findViewById(R.id.towPilotNameIn);
        towPilotNameIn.setAdapter(pilotlist.getPilotListAdapter());


        if (loadDayLog()) {
            found_daylog = true;
            resumeDayButton.setVisibility(View.VISIBLE);
        }

    }

    // Load the day log from a file
    private boolean loadDayLog() {
        boolean res = false;
        try {
            FileInputStream fis = this.openFileInput(dayLogFileName);
            ObjectInputStream is = new ObjectInputStream(fis);
            DayLog daylog = (DayLog) is.readObject();
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

}
