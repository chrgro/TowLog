package no.ntnuf.tow.towlog2

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import no.ntnuf.tow.towlog2.ui.screens.MainScreen
import no.ntnuf.tow.towlog2.ui.theme.TowLogTheme
import no.ntnuf.tow.towlog2.viewmodel.TowingViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TowLogTheme {
                val viewModel: TowingViewModel = viewModel()
                MainScreen(
                    viewModel = viewModel,
                    onStartNewDay = { bundle ->
                        Toast.makeText(this, "Start New Day", Toast.LENGTH_SHORT).show()
                        // Navigate to DayOverviewActivity with bundle
                    },
                    onResumeDay = { bundle ->
                        Toast.makeText(this, "Resume Day", Toast.LENGTH_SHORT).show()
                        // Navigate to DayOverviewActivity with bundle
                    },
                    onShowLogs = {
                        Toast.makeText(this, "Show Logs", Toast.LENGTH_SHORT).show()
                        // Show logs dialog
                    },
                    onLoadFikenContacts = {
                        Toast.makeText(this, "Load Fiken Contacts", Toast.LENGTH_SHORT).show()
                        // Load contacts
                    },
                    onSettings = {
                        Toast.makeText(this, "Settings", Toast.LENGTH_SHORT).show()
                        // Navigate to SettingsActivity
                    }
                )
            }
        }
    }
}
