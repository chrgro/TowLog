package no.ntnuf.tow.towlog2

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.ntnuf.tow.towlog2.fiken.FikenApiClient.FikenApiException
import org.json.JSONException
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import no.ntnuf.tow.towlog2.fiken.FikenApiClient
import no.ntnuf.tow.towlog2.model.Contact
import no.ntnuf.tow.towlog2.model.ContactListManager
import no.ntnuf.tow.towlog2.model.DayLog
import no.ntnuf.tow.towlog2.model.RegistrationList
import no.ntnuf.tow.towlog2.model.TowEntry

class DayOverviewActivity : AppCompatActivity() {

    private var daylog: DayLog? = null

    private lateinit var tableLayout: TableLayout
    private lateinit var toolbar: Toolbar
    private lateinit var loadfikencontactsdialog: AlertDialog
    private lateinit var floatingactionbutton: FloatingActionButton

    private val dayLogFileName = "daylog_"
    private val fikenErrorBodyMaxChars = 2000
    private val fikenContactLoadingEnabledKey = "fiken_contact_loading_enabled"

    private var menu: Menu? = null

    private var editMode = false

    private lateinit var settings: SharedPreferences

    private fun applySystemBarMarginsToBottomEnd(view: View) {
        val marginLayoutParams = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val initialBottom = marginLayoutParams.bottomMargin
        val initialEnd = marginLayoutParams.marginEnd
        val initialRight = marginLayoutParams.rightMargin
        val extraBottomPx = (8 * resources.displayMetrics.density + 0.5f).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val updated = target.layoutParams as? ViewGroup.MarginLayoutParams
                    ?: return@setOnApplyWindowInsetsListener insets

            updated.bottomMargin = initialBottom + systemBars.bottom + extraBottomPx
            updated.marginEnd = initialEnd + systemBars.right
            updated.rightMargin = initialRight + systemBars.right
            target.layoutParams = updated
            insets
        }

        ViewCompat.requestApplyInsets(view)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set fullscreen
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_day_overview)

        Log.e("DAYOVERVIEW", "onCreate() called")

        val bundle = savedInstanceState ?: intent.extras ?: Bundle()

        settings = PreferenceManager.getDefaultSharedPreferences(this)

        // Date and action are mandatory
        val action = bundle.getSerializable("action") as String
        val date = bundle.getSerializable("date") as Date

        // Tow pilot and plane are optional (only needed for new logs)
        val towpilot = bundle.getSerializable("towpilot") as Contact?
        val towplane = bundle.getSerializable("towplane") as String?
        val outdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val strdate = outdf.format(date)

        toolbar = findViewById(R.id.toolbardayoverview)
        val toolbartitle = "Tow Log  -  $strdate"
        toolbar.title = toolbartitle
        setSupportActionBar(toolbar)

        tableLayout = findViewById(R.id.dayOverViewTableLayout)

        // Load/create daylog
        if (action == "new" || !loadDayLog(date)) {
            if (action == "new") {
                Log.e("DAYOVERVIEW", "Creating new day log for date " + date.toString())
                Snackbar.make(tableLayout, "New daily log", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show()

                daylog = DayLog(towpilot!!, towplane!!, date)

                saveDayLog()

            } else {
                Log.e("DAYOVERVIEW", "Unable to load previous log, so creating a new one")
                Snackbar.make(tableLayout, "Problem loading old log!",
                        Snackbar.LENGTH_LONG).setAction("Action", null).show()
            }

        } else {
            Snackbar.make(tableLayout, "Resumed daily log", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

        // Add all the tows that exist
        refreshTowTable()

        // Button to add new tow activity
        floatingactionbutton = findViewById(R.id.fab)
        // Keep the FAB clear of transient system navigation/back controls.
        applySystemBarMarginsToBottomEnd(floatingactionbutton)
        floatingactionbutton.setOnClickListener {
            val intent = Intent(this@DayOverviewActivity, NewTowActivity::class.java)
            startActivityForResult(intent, 1)
        }

        // Add a connectivity listener, waiting for when we get internet connection back
        // TODO: Implement network connectivity monitoring
        // val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // cm.addDefaultNetworkActiveListener {
        //     Log.e("DAYOVERVIEW", "Network connection active, checking for pending uploads")
        //     val uploader = LogUploader(context, settings)
        //     uploader.uploadPending()
        // }

        // Alert dialog for fiken contact loading
        loadfikencontactsdialog = getLoadFikenContactsAlertDialog()

        // Keep legacy behavior: ignore physical/gesture back in this screen.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.e("DATOVERVIEW", "onBackPressed() called")
            }
        })

    }

    override fun onResume() {
        super.onResume()
        Log.e("DAYOVERVIEW", "onResume() called")
    }

    // Add the menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        this.menu = menu
        menuInflater.inflate(R.menu.dayoverview_menu, menu)

        floatingactionbutton.visibility = if (daylog?.logIsLocked == true) View.INVISIBLE else View.VISIBLE
        menu.findItem(R.id.menu_reenablelog).isVisible = daylog?.logIsLocked == true
        menu.findItem(R.id.menu_editlog).isVisible = daylog?.logIsLocked != true
        menu.findItem(R.id.menu_deletedaylog).isVisible = daylog?.logIsLocked != true
        menu.findItem(R.id.menu_loadfikencontacts).isVisible = isFikenContactLoadingEnabled()

        return true
    }

    // Handle clicks on the menu
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // Email the day log
            R.id.menu_senddaylog -> {
                if (daylog?.logIsLocked == true) {
                    AlertDialog.Builder(this)
                            .setMessage("It looks like this log has already been sent. " +
                                    "Are you sure you want to resend it?")
                            .setCancelable(false)
                            .setPositiveButton("Yes") { _, _ ->
                                sendLog()
                            }
                            .setNegativeButton("No", null)
                            .show()
                } else {
                    sendLog()
                }
                return true
            }

            // Clear the pilot list
            R.id.menu_clearpilotlist -> {
                AlertDialog.Builder(this)
                        .setMessage("Are you sure you want to clear the list of pilots? " +
                                "This only affects autocomplete hints. The change is visible on  " +
                                "the next application launch.")
                        .setCancelable(false)
                        .setPositiveButton("Yes") { _, _ ->
                            val pilotlist = ContactListManager(applicationContext)
                            pilotlist.clearList()
                        }
                        .setNegativeButton("No", null)
                        .show()
                return true
            }

            // Clear the registration list
            R.id.menu_clearregistrationlist -> {
                AlertDialog.Builder(this)
                        .setMessage("Are you sure you want to clear the list of registrations? " +
                                "This only affects autocomplete hints. The change is visible on  " +
                                "the next application launch.")
                        .setCancelable(false)
                        .setPositiveButton("Yes") { _, _ ->
                            val reglist = RegistrationList(applicationContext)
                            reglist.clearList()
                        }
                        .setNegativeButton("No", null)
                        .show()
                return true
            }

            // Edit the day log manually (move up down, delete)
            R.id.menu_editlog -> {
                editMode = !editMode
                val edit = menu?.findItem(R.id.menu_editlog)
                if (editMode) {
                    edit?.title = "Finish log editing"
                } else {
                    edit?.title = "Manually edit log"
                }
                refreshTowTable()
                return true
            }

            // Delete this daylog
            R.id.menu_deletedaylog -> {
                AlertDialog.Builder(this)
                        .setMessage("Are you sure you want to delete this day? " +
                                "This change can not be undone. ")
                        .setCancelable(false)
                        .setPositiveButton("Yes") { _, _ ->
                            deleteDayLog()
                            finish()
                        }
                        .setNegativeButton("No", null)
                        .show()
                return true
            }

            // Load lists of contacts from Fiken accounting program
            R.id.menu_loadfikencontacts -> {
                loadFikenContactsFromMenu()
                return true
            }

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
            R.id.menu_reenablelog -> {
                daylog = daylog?.copy(logIsLocked = false)
                saveDayLog()
                menu?.findItem(R.id.menu_reenablelog)?.isVisible = false
                menu?.findItem(R.id.menu_editlog)?.isVisible = true
                menu?.findItem(R.id.menu_deletedaylog)?.isVisible = true
                floatingactionbutton.visibility = View.VISIBLE
                return true
            }

            R.id.menu_settings -> {
                val intent = Intent(this@DayOverviewActivity, SettingsActivity::class.java)
                startActivity(intent)
                return true
            }

            // End the activity and go back to the overview calendar
            R.id.menu_backtocalendar -> {
                val response = Intent()
                val bundle = Bundle()

                bundle.putSerializable("action", "backtocalendarmenu")
                response.putExtras(bundle)
                setResult(RESULT_OK, response)
                finish()
                return true
            }

            // Nothing to do
            else -> return super.onOptionsItemSelected(item)
        }
    }

    // Root function for sending log
    // First uploads the log, then emails it
    fun sendLog() {
        // Pack up and send the logs
        if (settings.getBoolean("upload_log_enabled", false)) {
            Log.e("DAYOVERVIEW", "Log upload is enabled, but uploader is not implemented yet")
            // TODO: Implement LogUploader
            // val uploader = LogUploader(this, settings)
            // uploader.addToUploadQueue(daylog!!)
            // uploader.uploadPending()
        }
        sendLogViaEmail()
    }

    // Send logfile using email (create intent and send off to email application)
    fun sendLogViaEmail() {
        val currentDaylog = daylog ?: run {
            Log.e("DAYOVERVIEW", "No day log available for email")
            return
        }

        val outdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val dstr = outdf.format(currentDaylog.date)

        val dfFile = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val dFile = dfFile.format(currentDaylog.date)

        val towlogfilename = "towlog_" + dFile + ".html"
        val receiver_email = settings.getString("send_log_email", "")

        try {
            // Write the HTML version of the log to a file
            val dir = getExternalCacheDir()
            val towlogfile = File(dir, towlogfilename)
            towlogfile.createNewFile()
            val fios = FileOutputStream(towlogfile)

            val htmlContent = currentDaylog.csv2Html(currentDaylog.getCsvOutput())
            fios.write(htmlContent.toByteArray())
            fios.close()

            // Then add that HTML file as an attachment to the email
            val file = towlogfile //getFileStreamPath(towlogfilename);

            // Also add the CSV version to the email directly
            val emailintent = Intent(Intent.ACTION_SEND)
            emailintent.type = "text/plain"
            emailintent.putExtra(Intent.EXTRA_EMAIL, arrayOf(receiver_email))
            emailintent.putExtra(Intent.EXTRA_SUBJECT, "Tow Log for $dstr")
            emailintent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
            emailintent.putExtra(Intent.EXTRA_TEXT, currentDaylog.getCsvOutput())

            // If this setting is enabled, add all the emails to pilots to the BCC
            // list of the email
            if (settings.getBoolean("send_log_to_customers", false)) {
                val customerlist = ArrayList<String>()
                if (currentDaylog.towpilot.email != null) {
                    customerlist.add(currentDaylog.towpilot.email)
                }
                for (t in currentDaylog.tows) {
                    val pilotEmail = t.pilot.email
                    if (pilotEmail != null) {
                        customerlist.add(pilotEmail)
                    }
                    val copilotEmail = t.copilot?.email
                    if (copilotEmail != null) {
                        customerlist.add(copilotEmail)
                    }
                }
                val cl = customerlist.toTypedArray()
                if (settings.getBoolean("send_log_to_customers_using_bcc", true)) {
                    emailintent.putExtra(Intent.EXTRA_BCC, cl)
                } else {
                    emailintent.putExtra(Intent.EXTRA_CC, cl)
                }

            }

            startActivityForResult(Intent.createChooser(emailintent, "Send email using"), 2)

            // Mark the log as sent, disable various menu options
            daylog = daylog?.setLogHasBeenSent()
            floatingactionbutton.visibility = View.INVISIBLE
            menu?.findItem(R.id.menu_reenablelog)?.isVisible = true
            menu?.findItem(R.id.menu_editlog)?.isVisible = false
            menu?.findItem(R.id.menu_deletedaylog)?.isVisible = false
            saveDayLog()

            Log.e("EMAIL", "Sending email ")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // Create an alertdialog for loading contacts from fiken
    private fun getLoadFikenContactsAlertDialog(): AlertDialog {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Load Contacts from Fiken")
        builder.setMessage("Connecting to Fiken...")
        return builder.create()
    }

    private fun showFikenFailureDialog(responseCode: Int, responseBody: String) {
        val normalizedBody = responseBody.ifBlank { "<empty>" }
        val body = if (normalizedBody.length > fikenErrorBodyMaxChars) {
            normalizedBody.take(fikenErrorBodyMaxChars) + "\n\n... (truncated)"
        } else {
            normalizedBody
        }
        AlertDialog.Builder(this)
            .setTitle("Fiken Request Failed")
            .setMessage("HTTP $responseCode\n\n$body")
            .setPositiveButton("Dismiss", null)
            .show()
    }

    private fun showFikenFailureDialog(details: String) {
        val body = if (details.length > fikenErrorBodyMaxChars) {
            details.take(fikenErrorBodyMaxChars) + "\n\n... (truncated)"
        } else {
            details
        }
        AlertDialog.Builder(this)
            .setTitle("Fiken Request Failed")
            .setMessage(body)
            .setPositiveButton("Dismiss", null)
            .show()
    }

    private fun showFikenFailureDialog(throwable: Throwable) {
        when (throwable) {
            is FikenApiException -> {
                showFikenFailureDialog(
                    responseCode = throwable.statusCode,
                    responseBody = throwable.responseBody
                )
            }
            is IllegalStateException -> {
                showFikenFailureDialog(
                    throwable.message?.ifBlank { "<empty>" } ?: getString(R.string.fiken_contact_loading_disabled_message)
                )
            }
            is IllegalArgumentException -> {
                showFikenFailureDialog(
                    "Configuration error:\n${throwable.message?.ifBlank { "<empty>" } ?: "<none>"}"
                )
            }
            is JSONException -> {
                showFikenFailureDialog(
                    "Failed to parse response from Fiken:\n${throwable.message?.ifBlank { "<empty>" } ?: "<none>"}"
                )
            }
            is IOException -> {
                showFikenFailureDialog(
                    "Network/API I/O error:\n${throwable.message?.ifBlank { "<empty>" } ?: "<none>"}"
                )
            }
            else -> {
                showFikenFailureDialog(
                    "Unexpected error (${throwable::class.java.simpleName}):\n${throwable.message?.ifBlank { "<empty>" } ?: "<none>"}"
                )
            }
        }
    }

    private fun loadFikenContactsFromMenu() {
        if (!isFikenContactLoadingEnabled()) {
            showFikenFailureDialog(getString(R.string.fiken_contact_loading_disabled_message))
            return
        }

        loadfikencontactsdialog.show()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val apiClient = FikenApiClient(settings)
                apiClient.loadContacts().fold(
                    onSuccess = { contacts ->
                        ContactListManager(this@DayOverviewActivity).setContacts(contacts)
                        Result.success(contacts.size)
                    },
                    onFailure = { Result.failure(it) }
                )
            }

            if (loadfikencontactsdialog.isShowing) {
                loadfikencontactsdialog.dismiss()
            }

            result.fold(
                onSuccess = { count ->
                    Snackbar.make(tableLayout, "Loaded contacts ($count)", Snackbar.LENGTH_LONG).show()
                },
                onFailure = { throwable ->
                    showFikenFailureDialog(throwable)
                }
            )
        }
    }

    private fun isFikenContactLoadingEnabled(): Boolean {
        return settings.getBoolean(fikenContactLoadingEnabledKey, true)
    }

    // Return towentry from the started activity (only for tow activity results, type 0)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        Log.e("DAYOVERVIEW", "onActivityResult() called")

        // Grab the tow entry and add it to the daylog
        if (resultCode == RESULT_OK && requestCode == 1) {
            val bundle = data?.extras
            val towentry = bundle?.getSerializable("value") as TowEntry
            
            val currentDaylog = daylog
            if (currentDaylog != null) {
                val updatedTows = ArrayList(currentDaylog.tows).apply { add(towentry) }
                daylog = currentDaylog.copy(tows = updatedTows)
            }

            Log.e("DAYOVERVIEW", "onActivityResult() result OK")

            // Extract and write out the GPX content to file
            if (towentry.gpx_body != null) {
                Log.e("GPXGEN_RETURN", "GPX body not null:  " + towentry.gpx_body)
                // TODO: Implement GPXGenerator.storeGPX
                // val filename = GPXGenerator.storeGPX(this, daylog!!, towentry, towentry.gpx_body!!)
                // if (filename != null) {
                //     val updatedTow = towentry.copy(gpx_filename = filename, gpx_body = null)
                //     val index = daylog?.tows?.indexOf(towentry) ?: -1
                //     if (index >= 0) {
                //         val newTows = ArrayList(daylog.tows)
                //         newTows[index] = updatedTow
                //         daylog = daylog?.copy(tows = newTows)
                //     }
                //     Log.e("GPXGEN_RETURN", "Saved a GPX file: $filename")
                // }
            }

            // Refresh the table view
            refreshTowTable()
            Snackbar.make(tableLayout, "Tow saved", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
            saveDayLog()
        }
    }

    // Helper function to add a tow row
    private fun addTowToTable(tow: TowEntry, townumber: Int): TableRow {
        val tr = TableRow(this)
        val bodyTextColor = Color.BLACK

        // Scale *somewhat* correctly for different screen densities...
        val scale = resources.displayMetrics.density
        val textsize_dp = if (scale <= 1.0) 24 else 5
        val textsize = (textsize_dp * scale + 0.5f)

        val towno = TextView(this)
        towno.text = townumber.toString()
        towno.setTextSize(textsize)
        towno.setTextColor(bodyTextColor)
        towno.setPadding(0, 0, 5, 0)
        tr.addView(towno)

        val ttreg = TextView(this)
        ttreg.text = tow.registration
        ttreg.setTypeface(null, Typeface.BOLD)
        ttreg.setTextSize(textsize)
        ttreg.setTextColor(bodyTextColor)
        ttreg.setPadding(0, 0, 5, 0)

        val pilot = TextView(this)
        pilot.text = tow.pilot.name
        pilot.setTextSize(textsize)
        pilot.setTextColor(bodyTextColor)
        pilot.setPadding(0, 0, 5, 0)

        val copilot = TextView(this)
        if (tow.copilot != null) {
            copilot.text = tow.copilot.name
            copilot.setTextSize(textsize)
            copilot.setTextColor(bodyTextColor)
            copilot.setPadding(0, 0, 5, 0)
        }

        val notes = TextView(this)
        if (tow.notes != "") {
            notes.text = tow.notes
            notes.setTextSize(textsize)
            notes.setTextColor(bodyTextColor)
            notes.setPadding(0, 0, 5, 0)
            notes.setTypeface(null, Typeface.ITALIC)
        }

        val regandbuttons = RelativeLayout(this)
        val myparam = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT)
        regandbuttons.layoutParams = myparam
        regandbuttons.addView(ttreg)

        // If edit mode is enabled, add buttons for moving entries up and down the list, plus delete
        if (editMode) {
            val towindex = townumber - 1

            val right = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT)
            right.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            val buttons = LinearLayout(this)
            buttons.layoutParams = right

            val down = ImageView(this)
            down.setImageResource(R.mipmap.down_circle)
            down.setPadding(50, 0, 25, 0)
            down.maxHeight = (textsize / 1.5).toInt()
            down.setOnClickListener {
                daylog = daylog?.moveLogLine(false, towindex)
                refreshTowTable()
                saveDayLog()
            }

            val up = ImageView(this)
            up.setImageResource(R.mipmap.up_circle)
            up.setPadding(25, 0, 25, 0)
            up.maxHeight = (textsize / 1.5).toInt()
            up.setOnClickListener {
                daylog = daylog?.moveLogLine(true, towindex)
                refreshTowTable()
                saveDayLog()
            }

            val delete = ImageView(this)
            delete.setImageResource(R.mipmap.x_circle)
            delete.setPadding(50, 0, 25, 0)
            down.maxHeight = (textsize / 1.5).toInt()
            delete.setOnClickListener {
                AlertDialog.Builder(this@DayOverviewActivity)
                        .setMessage("Are you sure you want to delete this line?")
                        .setCancelable(false)
                        .setPositiveButton("Yes") { _, _ ->
                            daylog = daylog?.deleteLogLine(towindex)
                            refreshTowTable()
                            saveDayLog()
                        }
                        .setNegativeButton("No", null)
                        .show()

            }

            buttons.addView(down)
            buttons.addView(up)
            buttons.addView(delete)

            regandbuttons.addView(buttons)
        }

        val regAndNames = LinearLayout(this)
        regAndNames.orientation = LinearLayout.VERTICAL

        regAndNames.addView(regandbuttons)
        regAndNames.addView(pilot)
        if (tow.copilot != null) {
            regAndNames.addView(copilot)
        }
        if (tow.notes != "") {
            regAndNames.addView(notes)
        }

        tr.addView(regAndNames)

        val heightAndTime = LinearLayout(this)
        heightAndTime.orientation = LinearLayout.VERTICAL

        val height = TextView(this)
        height.text = "${tow.height}m"
        height.setTextSize(textsize)
        height.setTextColor(bodyTextColor)
        height.setPadding(0, 0, 5, 0)
        heightAndTime.addView(height)

        val time = TextView(this)
        val outdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dstr = outdf.format(tow.towStarted)
        time.text = dstr
        time.setTextSize(textsize)
        time.setTextColor(bodyTextColor)
        time.setPadding(0, 0, 5, 0)
        heightAndTime.addView(time)

        tr.addView(heightAndTime)

        return tr
    }


    private fun refreshTowTable() {
        // Clear everything except the header row
        val header_rows = 2
        tableLayout.removeViews(header_rows, tableLayout.childCount - header_rows)

        // Add the already existing tows to the table
        var townumber = 0
        for (tow in daylog?.tows ?: emptyList()) {
            townumber++

            // Add the text row
            val tbv = addTowToTable(tow, townumber)

            // Add the row separator
            val separator = View(this)
            separator.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            separator.setBackgroundColor(Color.BLACK)

            tableLayout.addView(tbv, TableLayout.LayoutParams(TableLayout.LayoutParams.WRAP_CONTENT, TableLayout.LayoutParams.WRAP_CONTENT))
            tableLayout.addView(separator)
        }
    }

    // Load the day log from a file
    private fun loadDayLog(date: Date): Boolean {
        var loadedSuccessfully = false
        val outdf = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault())
        val daylogsuffix = outdf.format(date)
        val fullfilename = dayLogFileName + daylogsuffix
        try {
            val fis = this.openFileInput(fullfilename)
            val `is` = ObjectInputStream(fis)
            daylog = `is`.readObject() as DayLog
            `is`.close()
            fis.close()
            loadedSuccessfully = true
            //Log.e("LOAD", "File found: "+fullfilename);
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            Log.e("DAYOVERVIEW", "File not found: $fullfilename")
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return loadedSuccessfully
    }

    // Save the day log to a file
    private fun saveDayLog() {
        // Save the daylog
        val fullfilename = daylog?.getFilename()
        if (fullfilename == null) {
            Log.e("DAYOVERVIEW", "Save, No filename available")
            return
        }
        try {
            val fos = this.openFileOutput(fullfilename, MODE_PRIVATE)
            val os = ObjectOutputStream(fos)
            os.writeObject(daylog)
            os.close()
            fos.close()
            //Log.e("SAVE", "Saved to file "+fullfilename);
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            Log.e("DAYOVERVIEW", "Save, File not found $fullfilename")
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("DAYOVERVIEW", "Save, IO Exception $fullfilename")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putSerializable("action", "resume")
        outState.putSerializable("date", daylog?.date)

        Log.e("DAYOVERVIEW", "onSaveInstanceState() called")
    }

    override fun onPause() {
        super.onPause()
        Log.e("DAYOVERVIEW", "onPause() called")
    }


    // Delete the day log
    private fun deleteDayLog(): Boolean {
        val deleted = deleteFile(daylog?.getFilename())
        return deleted
    }

}
