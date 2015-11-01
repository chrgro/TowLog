package no.ntnuf.towlog.towlog2.newtow;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.Toast;

import no.ntnuf.towlog.towlog2.common.Contact;
import no.ntnuf.towlog.towlog2.duringtowing.DuringTowingActivity;
import no.ntnuf.tow.towlog2.R;
import no.ntnuf.towlog.towlog2.common.RegistrationList;
import no.ntnuf.towlog.towlog2.common.TowEntry;
import no.ntnuf.towlog.towlog2.common.ContactListManager;

public class NewTowActivity extends AppCompatActivity {

    private AutoCompleteTextView pilotIn;
    private AutoCompleteTextView copilotIn;
    private AutoCompleteTextView registrationIn;
    private ImageView pilotCheckmark;
    private ImageView copilotCheckmark;

    private Contact selectedPilot;
    private Contact selectedCoPilot;

    private ContactListManager contactlistmanager;
    private RegistrationList registrationlist;

    private Toolbar toolbar;

    private SharedPreferences settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_tow);

        toolbar = (Toolbar) findViewById(R.id.toolbarnewtow);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        contactlistmanager = new ContactListManager(this);
        registrationlist = new RegistrationList(this);

        settings = PreferenceManager.getDefaultSharedPreferences(this);

        // Set up main pilot name input
        pilotIn = (AutoCompleteTextView) findViewById(R.id.pilotNameIn);
        pilotCheckmark = (ImageView) findViewById(R.id.pilotNameCheckmark);
        pilotIn.setAdapter(contactlistmanager.getContactNameListAdapter());
        pilotIn.setAdapter(contactlistmanager.getContactNameListAdapter());
        pilotIn.addTextChangedListener(new TextWatcher() {
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Contact selected = contactlistmanager.findContactFromName(String.valueOf(s));
                selectedPilot = selected;
                if (selected == null) {
                    pilotCheckmark.setBackgroundResource(0);
                } else {
                    if (selected.hasAccount) {
                        pilotCheckmark.setBackgroundResource(R.mipmap.green_checkmark);
                    } else {
                        pilotCheckmark.setBackgroundResource(R.mipmap.new_icon_blue);
                    }
                }
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void afterTextChanged(Editable s) {
            }
        });

        // Set up copilot name input
        copilotIn = (AutoCompleteTextView) findViewById(R.id.coPilotNameIn);
        copilotCheckmark = (ImageView) findViewById(R.id.copilotNameCheckmark);
        copilotIn.setAdapter(contactlistmanager.getContactNameListAdapter());
        copilotIn.addTextChangedListener(new TextWatcher() {
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Contact selected = contactlistmanager.findContactFromName(String.valueOf(s));
                selectedCoPilot = selected;
                if (selected == null) {
                    copilotCheckmark.setBackgroundResource(0);
                } else {
                    if (selected.hasAccount) {
                        copilotCheckmark.setBackgroundResource(R.mipmap.green_checkmark);
                    } else {
                        copilotCheckmark.setBackgroundResource(R.mipmap.new_icon_blue);
                    }
                }
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void afterTextChanged(Editable s) {}
        });

        // Set up aircraft registration name input
        registrationIn = (AutoCompleteTextView) findViewById(R.id.gliderRegistrationIn);
        registrationIn.setAdapter(registrationlist.getRegistrationListAdapter());
        registrationIn.setSelection(registrationIn.getText().length());
        registrationIn.setText(settings.getString("glider_default_reg",""));

        // Set up button for starting tow
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.startTowButton);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Save any interesting data
                String reg = registrationIn.getText().toString().trim();
                String pilotname = pilotIn.getText().toString().trim();
                String copilotname = copilotIn.getText().toString().trim();

                if (pilotname.equals("") || reg.equals("")) {
                    Toast.makeText(NewTowActivity.this, "You need a name and a registration", Toast.LENGTH_LONG).show();
                    return;
                }

                registrationlist.addRegistration(reg);

                if (selectedPilot == null) {
                    Contact pilot = contactlistmanager.saveContact(pilotname);
                    selectedPilot = pilot;
                }
                if (selectedCoPilot == null) {
                    // Only if there is a copilot
                    if (!copilotname.equals("")) {
                        Contact copilot = contactlistmanager.saveContact(copilotname);
                    }
                }

                // Make up the towentry
                TowEntry towentry = new TowEntry();
                towentry.registration = reg;
                towentry.pilot = selectedPilot;
                towentry.copilot = selectedCoPilot;

                // Bundle the data and start the next activity, DuringTowing
                Intent intent = new Intent(NewTowActivity.this, DuringTowingActivity.class);
                Bundle bundle = new Bundle();
                bundle.putSerializable("value", towentry);
                intent.putExtras(bundle);
                startActivityForResult(intent, 1);
            }
        });

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case android.R.id.home:
            finish();
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.e("NEWTOW", "Got activity result" + data);
        super.onActivityResult(requestCode, resultCode, data);

        // If we succeeded towing, then just throw it back to the overview screen
        // Otherwise stay here
        if (resultCode==RESULT_OK) {
            setResult(RESULT_OK, data);
            finish();
        }
    }

}
