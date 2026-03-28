package no.ntnuf.tow.towlog2

import android.content.Intent
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
                        val intent = Intent(this@MainActivity, DayOverviewActivity::class.java)
                        bundle?.let { intent.putExtras(it) }
                        startActivity(intent)
                    },
                    onResumeDay = { bundle ->
                        val intent = Intent(this@MainActivity, DayOverviewActivity::class.java)
                        bundle?.let { intent.putExtras(it) }
                        startActivity(intent)
                    },
                    onShowLogs = {
                        Toast.makeText(this@MainActivity, "Show Logs", Toast.LENGTH_SHORT).show()
                        // Show logs dialog
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
}
