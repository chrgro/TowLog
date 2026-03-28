package no.ntnuf.tow.towlog2

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import no.ntnuf.tow.towlog2.ui.screens.MainScreen
import no.ntnuf.tow.towlog2.ui.theme.TowLogTheme
import no.ntnuf.tow.towlog2.viewmodel.TowingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val dayLogFileNamePrefix = "daylog_"

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
                        Toast.makeText(this@MainActivity, "Load Fiken Contacts", Toast.LENGTH_SHORT).show()
                        // Load contacts
                    },
                    onSettings = {
                        val intent = Intent(this@MainActivity, SettingsActivity::class.java)
                        startActivity(intent)
                    }
                )
            }
        }
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
        val formatter = SimpleDateFormat("EEEE d/M/yyyy", Locale.getDefault())
        return try {
            val parsed = parser.parse(suffix)
            if (parsed == null) {
                "Parse error, name: $fileName"
            } else {
                formatter.format(parsed)
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
