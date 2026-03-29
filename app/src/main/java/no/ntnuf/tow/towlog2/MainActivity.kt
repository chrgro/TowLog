package no.ntnuf.tow.towlog2

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import no.ntnuf.tow.towlog2.ui.screens.MainScreen
import no.ntnuf.tow.towlog2.ui.theme.TowLogTheme
import no.ntnuf.tow.towlog2.viewmodel.TowingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val dayLogFileNamePrefix = "daylog_"
    private val fikenErrorBodyMaxChars = 2000
    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

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
        setContent {
            TowLogTheme {
                val viewModel: TowingViewModel = viewModel()
                MainScreen(
                    viewModel = viewModel,
                    onStartNewDay = { bundle ->
                        val intent = Intent(this@MainActivity, DayOverviewActivity::class.java)
                        intent.putExtras(bundle)
                        startActivity(intent)
                    },
                    onResumeDay = { bundle ->
                        val intent = Intent(this@MainActivity, DayOverviewActivity::class.java)
                        intent.putExtras(bundle)
                        startActivity(intent)
                    },
                    onShowLogs = {
                        showPreviousLogsDialog(viewModel)
                    },
                    onLoadFikenContacts = {
                        Toast.makeText(this@MainActivity, "Connecting to Fiken...", Toast.LENGTH_SHORT).show()
                        lifecycleScope.launch {
                            val result = viewModel.loadFikenContacts()
                            if (result.success) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "${result.message} (${result.count})",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else if (result.httpResponseCode != null) {
                                showFikenFailureDialog(
                                    responseCode = result.httpResponseCode,
                                    responseBody = result.httpResponseBody.orEmpty()
                                )
                            } else {
                                Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onSettings = {
                        val intent = Intent(this@MainActivity, SettingsActivity::class.java)
                        startActivity(intent)
                    }
                )
            }
        }
        ensureLocationPermissionOnLaunch()
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

    private fun showPreviousLogsDialog(viewModel: TowingViewModel) {
        val availableLogs = availableDayLogs()
        if (availableLogs.isEmpty()) {
            Toast.makeText(this, "No previous logs found", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Previous logs")
            .setItems(availableLogs.map { it.displayName }.toTypedArray()) { _, which ->
                val selectedLog = availableLogs.getOrNull(which) ?: return@setItems
                val selectedDate = parseDayLogSuffix(selectedLog.fileNameSuffix)
                if (selectedDate == null) {
                    Toast.makeText(this, "Unable to parse selected log date", Toast.LENGTH_SHORT).show()
                    return@setItems
                }

                viewModel.updateSelectedDate(selectedDate)
                val bundle = viewModel.resumeDay()
                if (bundle.isEmpty) {
                    Toast.makeText(this, "Could not load selected log", Toast.LENGTH_SHORT).show()
                    return@setItems
                }

                val intent = Intent(this, DayOverviewActivity::class.java)
                intent.putExtras(bundle)
                startActivity(intent)
            }
            .show()
    }

    private fun availableDayLogs(): List<AvailableDayLog> {
        val files = filesDir.list()?.toList().orEmpty().sorted()
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
