package no.ntnuf.tow.towlog2.model

import java.io.Serializable

data class Contact(
    val name: String = "",
    val self: String? = null,
    val hasAccount: Boolean = false,
    val customerNumber: Int = 0,
    val supplierNumber: Int = 0,
    val email: String? = null
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }
}
