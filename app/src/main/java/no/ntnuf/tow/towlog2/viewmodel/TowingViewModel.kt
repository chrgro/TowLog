package no.ntnuf.tow.towlog2.viewmodel

import android.app.Application
import android.content.SharedPreferences
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import no.ntnuf.tow.towlog2.fiken.FikenApiClient
import no.ntnuf.tow.towlog2.fiken.FikenApiClient.FikenApiException
import no.ntnuf.tow.towlog2.model.Contact
import no.ntnuf.tow.towlog2.model.ContactListManager
import no.ntnuf.tow.towlog2.model.DayLog
import java.io.ObjectInputStream
import java.text.SimpleDateFormat
import java.util.*

class TowingViewModel(application: Application) : AndroidViewModel(application) {

    data class FikenLoadResult(
        val success: Boolean,
        val message: String,
        val count: Int = 0,
        val httpResponseCode: Int? = null,
        val httpResponseBody: String? = null
    )

    private val _towPilotName = MutableStateFlow("")
    val towPilotName: StateFlow<String> = _towPilotName

    private val _towPlane = MutableStateFlow("")
    val towPlane: StateFlow<String> = _towPlane

    private val _selectedDate = MutableStateFlow(Date())
    val selectedDate: StateFlow<Date> = _selectedDate

    private val _foundDayLog = MutableStateFlow(false)
    val foundDayLog: StateFlow<Boolean> = _foundDayLog

    private val _selectedTowPilot = MutableStateFlow<Contact?>(null)
    val selectedTowPilot: StateFlow<Contact?> = _selectedTowPilot

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    private val settings: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    private val contactListManager = ContactListManager(application)
    private val fikenApiClient = FikenApiClient(settings)

    init {
        loadContacts()
        loadDefaultTowPlane()
    }

    fun updateTowPilotName(name: String) {
        _towPilotName.value = name
        val contact = contactListManager.findContactFromName(name)
        _selectedTowPilot.value = contact
    }

    fun updateTowPlane(plane: String) {
        _towPlane.value = plane.uppercase(Locale.ROOT)
    }

    fun updateSelectedDate(date: Date) {
        _selectedDate.value = date
        checkForExistingDayLog()
    }

    private fun checkForExistingDayLog() {
        val dayLogFileNameSuffix = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(_selectedDate.value)
        _foundDayLog.value = loadDayLog("daylog_$dayLogFileNameSuffix") != null
    }

    private fun loadDayLog(filename: String): DayLog? {
        return try {
            val fis = getApplication<Application>().openFileInput(filename)
            val ois = ObjectInputStream(fis)
            val dayLog = ois.readObject() as DayLog
            ois.close()
            fis.close()
            dayLog
        } catch (_: Exception) {
            null
        }
    }

    fun saveContact(name: String) {
        contactListManager.saveContact(name)
        loadContacts()
    }

    private fun loadContacts() {
        _contacts.value = contactListManager.getContacts()
    }

    private fun loadDefaultTowPlane() {
        _towPlane.value = (settings.getString("towplane_default_reg", "") ?: "")
            .uppercase(Locale.ROOT)
    }

    suspend fun loadFikenContacts(): FikenLoadResult {
        val loadedContacts = withContext(Dispatchers.IO) {
            fikenApiClient.loadContacts()
        }

        return loadedContacts.fold(
            onSuccess = { contacts ->
                withContext(Dispatchers.IO) {
                    contactListManager.setContacts(contacts)
                }
                _contacts.value = contactListManager.getContacts()
                FikenLoadResult(
                    success = true,
                    message = "Loaded contacts",
                    count = contacts.size
                )
            },
            onFailure = { throwable ->
                val apiException = throwable as? FikenApiException
                FikenLoadResult(
                    success = false,
                    message = "Failed to load contacts",
                    httpResponseCode = apiException?.statusCode,
                    httpResponseBody = apiException?.responseBody
                )
            }
        )
    }

    fun startNewDay(): Bundle {
        saveContact(_towPilotName.value.trim())
        val towPilot = _selectedTowPilot.value ?: Contact(name = _towPilotName.value.trim())
        val towPlane = _towPlane.value.trim()
        val date = _selectedDate.value

        return Bundle().apply {
            putSerializable("towpilot", towPilot)
            putSerializable("towplane", towPlane)
            putSerializable("date", date)
            putSerializable("action", "new")
        }
    }

    fun resumeDay(): Bundle {
        val dayLogFileNameSuffix = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(_selectedDate.value)
        val dayLog = loadDayLog("daylog_$dayLogFileNameSuffix") ?: return Bundle()

        return Bundle().apply {
            putSerializable("towpilot", dayLog.towpilot)
            putSerializable("towplane", dayLog.towplane)
            putSerializable("date", dayLog.date)
            putSerializable("action", "resume")
            putSerializable("reason", "Resuming day")
        }
    }
}
