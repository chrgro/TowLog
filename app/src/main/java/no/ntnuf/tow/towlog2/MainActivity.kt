package no.ntnuf.tow.towlog2

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch
import no.ntnuf.tow.towlog2.model.ContactListManager
import no.ntnuf.tow.towlog2.viewmodel.TowingViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

class MainActivity : AppCompatActivity() {
    private val dayLogFileNamePrefix = "daylog_"
    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    private val viewModel: TowingViewModel by viewModels()

    private lateinit var toolbar: Toolbar
    private lateinit var selectDateButton: Button
    private lateinit var towPilotInput: AutoCompleteTextView
    private lateinit var towPilotCheckmark: ImageView
    private lateinit var towPlaneInput: EditText
    private lateinit var mainButtonContainer: View
    private lateinit var startNewDayButton: Button
    private lateinit var resumeDayButton: Button
    private lateinit var contactAdapter: ArrayAdapter<String>

    private var isUpdatingTowPlaneText = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.any { it.value }
        if (!granted) {
            val permanentlyDenied = locationPermissions.all { permission ->
                !ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
            }
            if (permanentlyDenied) {
                showLocationPermissionPermanentlyDeniedDialog()
            } else {
                showLocationPermissionDeniedDialog()
            }
        }
    }

    private data class AvailableDayLog(
        val fileNameSuffix: String,
        val displayName: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbarmain)
        toolbar.title = getString(R.string.main_prepare_towing_title)
        setSupportActionBar(toolbar)

        bindViews()
        bindViewModel()
        bindInputHandlers()

        // Trigger initial day-log existence check for the default selected date.
        viewModel.updateSelectedDate(viewModel.selectedDate.value)

        ensureLocationPermissionOnLaunch()
    }

    override fun onResume() {
        super.onResume()
        val defaultTowPlane = (PreferenceManager.getDefaultSharedPreferences(this)
            .getString("towplane_default_reg", "") ?: "")
            .uppercase(Locale.ROOT)
        towPlaneInput.setText(defaultTowPlane)
        towPlaneInput.setSelection(defaultTowPlane.length)
        viewModel.updateTowPlane(defaultTowPlane)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_previous_logs -> {
                showPreviousLogsDialog()
                return true
            }

            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun bindViews() {
        selectDateButton = findViewById(R.id.selectDateButton)
        towPilotInput = findViewById(R.id.towPilotNameIn)
        towPilotCheckmark = findViewById(R.id.towPilotCheckmark)
        towPlaneInput = findViewById(R.id.towPlaneIn)
        mainButtonContainer = findViewById(R.id.mainButtonContainer)
        startNewDayButton = findViewById(R.id.startDayButton)
        resumeDayButton = findViewById(R.id.resumeDayButton)
        applyImeAwareMarginsToBottomEnd(mainButtonContainer)

        contactAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf())
        towPilotInput.setAdapter(contactAdapter)
    }

    private fun applyImeAwareMarginsToBottomEnd(view: View) {
        val marginLayoutParams = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val initialBottom = marginLayoutParams.bottomMargin
        val initialEnd = marginLayoutParams.marginEnd
        val initialRight = marginLayoutParams.rightMargin

        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val updated = target.layoutParams as? ViewGroup.MarginLayoutParams
                ?: return@setOnApplyWindowInsetsListener insets

            updated.bottomMargin = initialBottom + max(systemBars.bottom, ime.bottom)
            updated.marginEnd = initialEnd + systemBars.right
            updated.rightMargin = initialRight + systemBars.right
            target.layoutParams = updated
            insets
        }

        ViewCompat.requestApplyInsets(view)
    }

    private fun bindInputHandlers() {
        selectDateButton.setOnClickListener { showDatePicker() }

        towPilotInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.updateTowPilotName(ContactListManager.normalizeSuggestionLabel(s?.toString().orEmpty()))
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        towPilotInput.setOnItemClickListener { _, _, _, _ ->
            val normalized = ContactListManager.normalizeSuggestionLabel(towPilotInput.text?.toString().orEmpty())
            towPilotInput.setText(normalized, false)
            towPilotInput.setSelection(normalized.length)
            viewModel.updateTowPilotName(normalized)
        }

        towPlaneInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUpdatingTowPlaneText) return

                val normalized = s?.toString().orEmpty().uppercase(Locale.ROOT)
                if (normalized != s?.toString().orEmpty()) {
                    isUpdatingTowPlaneText = true
                    towPlaneInput.setText(normalized)
                    towPlaneInput.setSelection(normalized.length)
                    isUpdatingTowPlaneText = false
                }
                viewModel.updateTowPlane(normalized)
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        startNewDayButton.setOnClickListener {
            val bundle = viewModel.startNewDay()
            openDayOverview(bundle)
        }

        resumeDayButton.setOnClickListener {
            val bundle = viewModel.resumeDay()
            if (bundle.isEmpty) {
                Toast.makeText(this, getString(R.string.main_could_not_load_selected_log), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            openDayOverview(bundle)
        }
    }

    private fun bindViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.selectedDate.collect { selectedDate ->
                        val formattedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(selectedDate)
                        selectDateButton.text = getString(R.string.main_selected_date_value, formattedDate)
                    }
                }

                launch {
                    viewModel.towPilotName.collect { pilotName ->
                        if (towPilotInput.text?.toString() != pilotName) {
                            towPilotInput.setText(pilotName)
                            towPilotInput.setSelection(pilotName.length)
                        }
                    }
                }

                launch {
                    viewModel.towPlane.collect { towPlane ->
                        if (towPlaneInput.text?.toString() != towPlane) {
                            towPlaneInput.setText(towPlane)
                            towPlaneInput.setSelection(towPlane.length)
                        }
                    }
                }

                launch {
                    viewModel.contacts.collect { contacts ->
                        contactAdapter.clear()
                        contactAdapter.addAll(contacts.map { ContactListManager.toSuggestionLabel(it.name, it.hasAccount) })
                        contactAdapter.notifyDataSetChanged()
                    }
                }

                launch {
                    viewModel.selectedTowPilot.collect { pilot ->
                        if (pilot == null) {
                            towPilotCheckmark.visibility = View.GONE
                        } else {
                            towPilotCheckmark.visibility = View.VISIBLE
                            towPilotCheckmark.setImageResource(
                                if (pilot.hasAccount) R.mipmap.green_checkmark else R.mipmap.new_icon_blue
                            )
                        }
                    }
                }

                launch {
                    viewModel.foundDayLog.collect { foundDayLog ->
                        resumeDayButton.visibility = if (foundDayLog) View.VISIBLE else View.GONE
                        startNewDayButton.visibility = if (foundDayLog) View.GONE else View.VISIBLE
                    }
                }
            }
        }
    }

    private fun showDatePicker() {
        val selectedDate = viewModel.selectedDate.value
        val calendar = Calendar.getInstance().apply { time = selectedDate }
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val newDate = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }.time
                viewModel.updateSelectedDate(newDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun openDayOverview(bundle: Bundle) {
        val intent = Intent(this, DayOverviewActivity::class.java)
        intent.putExtras(bundle)
        startActivity(intent)
    }

    private fun ensureLocationPermissionOnLaunch() {
        if (hasLocationPermission()) {
            return
        }

        val shouldShowRationale = locationPermissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        }

        if (shouldShowRationale) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.location_permission_title))
                .setMessage(getString(R.string.location_permission_rationale_message))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.location_permission_allow)) { _, _ ->
                    locationPermissionLauncher.launch(locationPermissions)
                }
                .setNegativeButton(getString(R.string.location_permission_not_now), null)
                .show()
        } else {
            locationPermissionLauncher.launch(locationPermissions)
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    private fun showLocationPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.location_permission_title))
            .setMessage(getString(R.string.location_permission_denied_message))
            .setPositiveButton(getString(R.string.location_permission_ok), null)
            .show()
    }

    private fun showLocationPermissionPermanentlyDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.location_permission_title))
            .setMessage(getString(R.string.location_permission_permanently_denied_message))
            .setPositiveButton(getString(R.string.location_permission_ok), null)
            .show()
    }

    private fun showPreviousLogsDialog() {
        val availableLogs = availableDayLogs()
        if (availableLogs.isEmpty()) {
            Toast.makeText(this, getString(R.string.main_no_previous_logs), Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.main_previous_logs_title))
            .setItems(availableLogs.map { it.displayName }.toTypedArray()) { _, which ->
                val selectedLog = availableLogs.getOrNull(which) ?: return@setItems
                val selectedDate = parseDayLogSuffix(selectedLog.fileNameSuffix)
                if (selectedDate == null) {
                    Toast.makeText(this, getString(R.string.main_unable_to_parse_log_date), Toast.LENGTH_SHORT).show()
                    return@setItems
                }

                viewModel.updateSelectedDate(selectedDate)
                val bundle = viewModel.resumeDay()
                if (bundle.isEmpty) {
                    Toast.makeText(this, getString(R.string.main_could_not_load_selected_log), Toast.LENGTH_SHORT).show()
                    return@setItems
                }

                val intent = Intent(this, DayOverviewActivity::class.java)
                intent.putExtras(bundle)
                startActivity(intent)
            }
            .show()
    }

    private fun availableDayLogs(): List<AvailableDayLog> {
        // With fixed-width yyyy_MM_dd suffixes, descending lexical order yields newest-first logs.
        val files = filesDir.list()?.toList().orEmpty().sortedDescending()
        return files
            .filter { it.startsWith(dayLogFileNamePrefix) }
            .map { fileName ->
                val suffix = fileName.removePrefix(dayLogFileNamePrefix)
                AvailableDayLog(
                    fileNameSuffix = suffix,
                    displayName = formatDayLogNameForDisplay(fileName, suffix)
                )
            }
    }

    private fun formatDayLogNameForDisplay(fileName: String, suffix: String): String {
        val parser = SimpleDateFormat("yyyy_MM_dd", Locale.ENGLISH)
        val isoDateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val weekdayFormatter = SimpleDateFormat("EEEE", Locale.getDefault())
        return try {
            val parsed = parser.parse(suffix)
            if (parsed == null) {
                "Parse error, name: $fileName"
            } else {
                "${isoDateFormatter.format(parsed)} (${weekdayFormatter.format(parsed)})"
            }
        } catch (_: Exception) {
            "Parse error, name: $fileName"
        }
    }

    private fun parseDayLogSuffix(fileNameSuffix: String): Date? {
        return try {
            val parser = SimpleDateFormat("yyyy_MM_dd", Locale.ENGLISH)
            parser.parse(fileNameSuffix)
        } catch (_: Exception) {
            null
        }
    }
}
