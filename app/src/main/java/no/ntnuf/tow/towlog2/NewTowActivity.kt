package no.ntnuf.tow.towlog2

import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import no.ntnuf.tow.towlog2.model.Contact
import no.ntnuf.tow.towlog2.model.ContactListManager
import no.ntnuf.tow.towlog2.model.RegistrationList
import no.ntnuf.tow.towlog2.model.TowEntry
import java.util.Date

@Suppress("DEPRECATION")
class NewTowActivity : AppCompatActivity(), LocationListener {

    private lateinit var pilotIn: AutoCompleteTextView
    private lateinit var copilotIn: AutoCompleteTextView
    private lateinit var registrationIn: AutoCompleteTextView
    private lateinit var notesIn: AutoCompleteTextView
    private lateinit var pilotCheckmark: ImageView
    private lateinit var copilotCheckmark: ImageView

    private var selectedPilot: Contact? = null
    private var selectedCoPilot: Contact? = null

    private lateinit var contactlistmanager: ContactListManager
    private lateinit var registrationlist: RegistrationList

    private lateinit var toolbar: Toolbar

    private lateinit var settings: SharedPreferences

    private lateinit var locationManager: LocationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set fullscreen
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_tow)

        toolbar = findViewById(R.id.toolbarnewtow)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        contactlistmanager = ContactListManager(this)
        registrationlist = RegistrationList(this)

        settings = PreferenceManager.getDefaultSharedPreferences(this)

        // Set up main pilot name input
        pilotIn = findViewById(R.id.pilotNameIn)
        pilotCheckmark = findViewById(R.id.pilotNameCheckmark)
        pilotIn.setAdapter(contactlistmanager.getContactNameListAdapter(this))
        pilotIn.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val selected = contactlistmanager.findContactFromName(s.toString())
                selectedPilot = selected
                if (selected == null) {
                    pilotCheckmark.setBackgroundResource(0)
                } else {
                    pilotCheckmark.setBackgroundResource(
                        if (selected.hasAccount) R.mipmap.green_checkmark else R.mipmap.new_icon_blue
                    )
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
        })

        // Set up copilot name input
        copilotIn = findViewById(R.id.coPilotNameIn)
        copilotCheckmark = findViewById(R.id.copilotNameCheckmark)
        copilotIn.setAdapter(contactlistmanager.getContactNameListAdapter(this))
        copilotIn.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val selected = contactlistmanager.findContactFromName(s.toString())
                selectedCoPilot = selected
                if (selected == null) {
                    copilotCheckmark.setBackgroundResource(0)
                } else {
                    copilotCheckmark.setBackgroundResource(
                        if (selected.hasAccount) R.mipmap.green_checkmark else R.mipmap.new_icon_blue
                    )
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
        })

        // Set up aircraft registration name input
        registrationIn = findViewById(R.id.gliderRegistrationIn)
        registrationIn.setAdapter(registrationlist.getRegistrationListAdapter(this))
        registrationIn.setSelection(registrationIn.text.length)
        registrationIn.setText(settings.getString("glider_default_reg", ""))

        // Set up notes input
        notesIn = findViewById(R.id.notesIn)
        notesIn.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                notesIn.showDropDown()
            }
        }

        // Set up button for starting tow
        val fab: FloatingActionButton = findViewById(R.id.startTowButton)
        fab.setOnClickListener {
            // Save any interesting data
            val reg = registrationIn.text.toString().trim()
            val pilotname = pilotIn.text.toString().trim()
            val copilotname = copilotIn.text.toString().trim()

            if (pilotname.isEmpty() || reg.isEmpty()) {
                Toast.makeText(this@NewTowActivity, "You need a name and a registration", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            registrationlist.addRegistration(reg)

            if (selectedPilot == null) {
                selectedPilot = contactlistmanager.saveContact(pilotname)
            }
            if (selectedCoPilot == null && copilotname.isNotEmpty()) {
                selectedCoPilot = contactlistmanager.saveContact(copilotname)
            }

            // Make up the towentry
            val towentry = TowEntry(
                height = 0,
                pilot = selectedPilot!!,
                copilot = selectedCoPilot,
                towStarted = Date(),
                registration = reg,
                notes = notesIn.text.toString().trim()
            )

            // Bundle the data and start the next activity, DuringTowing
            val intent = Intent(this@NewTowActivity, DuringTowingActivity::class.java)
            val bundle = Bundle()
            bundle.putSerializable("value", towentry)
            intent.putExtras(bundle)
            startActivityForResult(intent, 1)
        }
    }

    override fun onResume() {
        super.onResume()

        // Set up a GPS location listener on this screen, to aquire GPS signals
        // faster when we actually need them in the next activity
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 0f, this)
        } catch (_: SecurityException) {
            Log.e("NEWTOW", "Location permission not granted")
        }
    }

    override fun onPause() {
        super.onPause()
        locationManager.removeUpdates(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.e("NEWTOW", "Got activity result$data")
        super.onActivityResult(requestCode, resultCode, data)

        // If we succeeded towing, then just throw it back to the overview screen
        // Otherwise stay here
        if (resultCode == RESULT_OK) {
            setResult(RESULT_OK, data)
            finish()
        }
    }

    override fun onLocationChanged(location: Location) {
        // Nothing much to do here. Perhaps in the future show some icon to
        // indicate if the GPS is connected or not
    }

    @Deprecated("Deprecated in LocationListener")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}
}
