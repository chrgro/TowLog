package no.ntnuf.tow.towlog2.ui.screens

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.DatePicker
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import no.ntnuf.tow.towlog2.R
import no.ntnuf.tow.towlog2.viewmodel.TowingViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun MainScreen(
    viewModel: TowingViewModel = viewModel(),
    onStartNewDay: (Bundle) -> Unit,
    onResumeDay: (Bundle) -> Unit,
    onShowLogs: () -> Unit,
    onLoadFikenContacts: () -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current

    val towPilotName by viewModel.towPilotName.collectAsState()
    val towPlane by viewModel.towPlane.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val foundDayLog by viewModel.foundDayLog.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val selectedTowPilot by viewModel.selectedTowPilot.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }

    val calendar = Calendar.getInstance().apply { time = selectedDate }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prepare Towing") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorResource(id = R.color.colorPrimary),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = onShowLogs) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Show Logs")
                    }
                    IconButton(onClick = onLoadFikenContacts) {
                        Icon(Icons.Default.Refresh, contentDescription = "Load Fiken Contacts")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Date Picker
            OutlinedButton(onClick = { showDatePicker = true }) {
                Text("Selected Date: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate)}")
            }

            // Tow Pilot Name
            var expanded by remember { mutableStateOf(false) }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Tow Pilot Name", style = MaterialTheme.typography.bodyMedium)
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = towPilotName,
                        onValueChange = { viewModel.updateTowPilotName(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        textStyle = TextStyle(fontSize = 30.sp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Words
                        ),
                        trailingIcon = {
                            selectedTowPilot?.let { pilot ->
                                Image(
                                    painter = painterResource(
                                        if (pilot.hasAccount) R.mipmap.green_checkmark
                                        else R.mipmap.new_icon_blue
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        contacts.forEach { contact ->
                            DropdownMenuItem(
                                text = { Text(contact.name) },
                                onClick = {
                                    viewModel.updateTowPilotName(contact.name)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Tow Plane
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Tow Plane Registration", style = MaterialTheme.typography.bodyMedium)
                TextField(
                    value = towPlane,
                    onValueChange = { viewModel.updateTowPlane(it.uppercase(Locale.ROOT)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 30.sp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Characters
                    ),
                )
            }

            // Buttons
            Button(
                onClick = {
                    val bundle = viewModel.startNewDay()
                    onStartNewDay(bundle)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start New Day")
            }

            if (foundDayLog) {
                Button(
                    onClick = {
                        val bundle = viewModel.resumeDay()
                        onResumeDay(bundle)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("Resume Day")
                }
            }
        }
    }

    LaunchedEffect(showDatePicker) {
        if (showDatePicker) {
            DatePickerDialog(
                context,
                { _: DatePicker, year: Int, month: Int, day: Int ->
                    val newDate = Calendar.getInstance().apply {
                        set(year, month, day)
                    }.time
                    viewModel.updateSelectedDate(newDate)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply {
                setOnDismissListener { showDatePicker = false }
                show()
            }
        }
    }
}
