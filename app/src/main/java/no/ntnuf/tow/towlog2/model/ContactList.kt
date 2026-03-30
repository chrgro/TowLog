package no.ntnuf.tow.towlog2.model

import java.io.Serializable

data class ContactList(
    val contacts: ArrayList<Contact> = ArrayList()
) : Serializable {

    companion object {
        private const val serialVersionUID = 3L
    }

    fun clear() {
        contacts.clear()
    }

    fun findContactFromName(name: String): Contact? {
        return contacts.find { it.name == name }
    }

    fun addContact(c: Contact) {
        val existing = findContactFromName(c.name)
        if (existing == null) {
            contacts.add(c)
        }
    }

    fun remove(index: Int) {
        contacts.removeAt(index)
    }
}
