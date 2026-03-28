package no.ntnuf.tow.towlog2.fiken

import android.content.SharedPreferences
import android.util.Base64
import no.ntnuf.tow.towlog2.model.Contact
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class FikenApiClient(private val settings: SharedPreferences) {

    companion object {
        private const val CONTACTS_REL = "https://fiken.no/api/v1/rel/contacts"
        private const val KEY_API_URL = "fiken_api_url"
        private const val KEY_API_USERNAME = "fiken_api_username"
        private const val KEY_API_PASSWORD = "fiken_api_password"
    }

    fun loadContacts(): Result<List<Contact>> {
        return runCatching {
            val url = settings.getString(KEY_API_URL, "")?.trim().orEmpty()
            val username = settings.getString(KEY_API_USERNAME, "")?.trim().orEmpty()
            val password = settings.getString(KEY_API_PASSWORD, "")?.trim().orEmpty()

            if (url.isBlank()) {
                throw IllegalArgumentException("Fiken API URL is empty")
            }
            if (username.isBlank() || password.isBlank()) {
                throw IllegalArgumentException("Fiken API username or password is empty")
            }

            val jsonResponse = fetchContactsJson(url, username, password)
            parseContacts(jsonResponse)
        }
    }

    private fun fetchContactsJson(url: String, username: String, password: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "application/json")

            val authValue = "$username:$password"
            val encodedAuth = Base64.encodeToString(authValue.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            connection.setRequestProperty("Authorization", "Basic $encodedAuth")

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (responseCode !in 200..299) {
                throw IOException("Fiken API request failed (HTTP $responseCode)")
            }

            return responseBody
        } finally {
            connection.disconnect()
        }
    }

    private fun parseContacts(json: String): List<Contact> {
        val root = JSONObject(json)
        val embedded = root.getJSONObject("_embedded")
        val jsonContacts = embedded.getJSONArray(CONTACTS_REL)

        val contacts = ArrayList<Contact>()
        for (i in 0 until jsonContacts.length()) {
            val entry = jsonContacts.getJSONObject(i)
            val name = entry.optString("name", "").trim()
            if (name.isBlank()) {
                continue
            }

            val self = entry
                .optJSONObject("_links")
                ?.optJSONObject("self")
                ?.optString("href")
                ?.takeIf { it.isNotBlank() }

            val email = entry.optString("email", "").trim().ifBlank { null }

            contacts.add(
                Contact(
                    name = name,
                    self = self,
                    hasAccount = true,
                    customerNumber = entry.optInt("customerNumber", 0),
                    supplierNumber = entry.optInt("supplierNumber", 0),
                    email = email
                )
            )
        }

        return contacts
    }
}

