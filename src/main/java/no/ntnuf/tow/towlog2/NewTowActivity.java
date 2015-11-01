package no.ntnuf.tow.towlog2;

import android.app.ActionBar;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;

public class NewTowActivity extends AppCompatActivity {

    private AutoCompleteTextView pilotIn;
    private AutoCompleteTextView copilotIn;
    private AutoCompleteTextView registrationIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_tow);

        pilotIn = (AutoCompleteTextView) findViewById(R.id.pilotNameIn);
        copilotIn = (AutoCompleteTextView) findViewById(R.id.coPilotNameIn);
        registrationIn = (AutoCompleteTextView) findViewById(R.id.gliderRegistrationIn);


        // Add new tow activity
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.startTowButton);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TowEntry towentry = new TowEntry();
                towentry.registration = registrationIn.getText().toString();
                towentry.pilotname = pilotIn.getText().toString();
                towentry.copilotname = copilotIn.getText().toString();

                Intent intent = new Intent(NewTowActivity.this, DuringTowingActivity.class);
                Bundle bundle = new Bundle();
                bundle.putSerializable("value", towentry);
                intent.putExtras(bundle);
                startActivityForResult(intent, 0);
            }
        });

        //ActionBar actionBar = getActionBar();
        //actionBar.setDisplayHomeAsUpEnabled(true);
    }


    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // If we succeeded towing, then just throw it back to the overview screen
        // Otherwise stay here
        if (resultCode==RESULT_OK) {
            setResult(RESULT_OK, data);
            finish();
        }
    }

}
