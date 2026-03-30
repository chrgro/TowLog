package no.ntnuf.tow.towlog2.model

import java.io.Serializable
import java.util.Date

data class TowEntry(
    val height: Int = 0,
    val pilot: Contact,
    val copilot: Contact? = null,
    val towStarted: Date,
    val registration: String = "",
    val notes: String = "",
    val gpx_filename: String? = null,
    val gpx_body: String? = null
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }
}
