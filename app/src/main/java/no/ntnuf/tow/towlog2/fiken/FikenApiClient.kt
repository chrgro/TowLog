package no.ntnuf.tow.towlog2.fiken

import android.content.SharedPreferences
import no.ntnuf.tow.towlog2.model.Contact
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class FikenApiClient(private val settings: SharedPreferences) {

    class FikenApiException(
        val statusCode: Int,
        val responseBody: String
    ) : IOException("Fiken API request failed (HTTP $statusCode)")

    companion object {
        private const val KEY_CONTACT_LOADING_ENABLED = "fiken_contact_loading_enabled"
        private const val KEY_API_URL = "fiken_api_url"
        private const val KEY_API_BEARER = "fiken_api_bearer_key"
        private const val PAGE_SIZE = 100
    }

    fun loadContacts(): Result<List<Contact>> {
        return runCatching {
            val contactLoadingEnabled = settings.getBoolean(KEY_CONTACT_LOADING_ENABLED, true)
            val baseUrl = settings.getString(KEY_API_URL, "")?.trim().orEmpty()
            val bearerToken = settings.getString(KEY_API_BEARER, "")?.trim().orEmpty()

            if (!contactLoadingEnabled) {
                throw IllegalStateException("Fiken contact loading is disabled")
            }

            if (baseUrl.isBlank()) {
                throw IllegalArgumentException("Fiken API URL is empty")
            }
            if (bearerToken.isBlank()) {
                throw IllegalArgumentException("Fiken API bearer key is empty")
            }

            val contacts = ArrayList<Contact>()
            var page = 0

            while (true) {
                val pagedUrl = buildContactsUrl(baseUrl, page)
                val jsonResponse = fetchContactsJson(pagedUrl, bearerToken)
                val pageContacts = parseContacts(jsonResponse)

                if (pageContacts.isEmpty()) {
                    break
                }

                contacts.addAll(pageContacts)
                page += 1
            }

            contacts
        }
    }

    private fun fetchContactsJson(url: String, bearerToken: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $bearerToken")

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (responseCode !in 200..299) {
                throw FikenApiException(responseCode, responseBody)
            }

            return responseBody
        } finally {
            connection.disconnect()
        }
    }

    private fun buildContactsUrl(baseUrl: String, page: Int): String {
        val split = baseUrl.split("?", limit = 2)
        val endpoint = split[0]
        val existingQuery = split.getOrNull(1).orEmpty()

        val retainedParams = existingQuery
            .split("&")
            .filter { it.isNotBlank() }
            .filterNot {
                it.startsWith("customer=") ||
                        it.startsWith("page=") ||
                        it.startsWith("pageSize=")
            }

        val params = ArrayList<String>()
        params.addAll(retainedParams)
        params.add("customer=true")
        params.add("pageSize=$PAGE_SIZE")
        params.add("page=$page")

        return "$endpoint?${params.joinToString("&")}"
    }

    private fun parseContacts(json: String): List<Contact> {
        val jsonContacts = JSONArray(json)

        val contacts = ArrayList<Contact>()
        for (i in 0 until jsonContacts.length()) {
            val entry = jsonContacts.getJSONObject(i)
            if (!entry.optBoolean("customer", false)) {
                continue
            }

            val name = entry.optString("name", "").trim()
            if (name.isBlank()) {
                continue
            }

            val self = entry
                .optJSONObject("_links")
                ?.optJSONObject("self")
                ?.optString("href")
                ?.takeIf { it.isNotBlank() }
                ?: entry.opt("contactId")?.toString()

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

