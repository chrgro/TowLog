package no.ntnuf.tow.towlog2.model

import android.content.Context
import android.widget.ArrayAdapter
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class ContactListManager(private val context: Context) {
    private var contactList: ContactList = ContactList()
    private val filename = "contactlist.serialized"

    companion object {
        private const val ACCOUNT_CHECKMARK_SUFFIX = " \u2713"

        fun toSuggestionLabel(contactName: String, hasAccount: Boolean): String {
            return if (hasAccount) {
                "$contactName$ACCOUNT_CHECKMARK_SUFFIX"
            } else {
                contactName
            }
        }

        fun normalizeSuggestionLabel(value: String): String {
            val trimmed = value.trim()
            return if (trimmed.endsWith(ACCOUNT_CHECKMARK_SUFFIX)) {
                trimmed.removeSuffix(ACCOUNT_CHECKMARK_SUFFIX).trimEnd()
            } else {
                trimmed
            }
        }
    }

    init {
        loadContacts()
    }

    fun findContactFromName(name: String): Contact? {
        return contactList.findContactFromName(name)
    }

    fun saveContact(name: String): Contact {
        val found = findContactFromName(name)
        if (found != null) {
            return found
        } else {
            val newContact = Contact(name = name)
            contactList.addContact(newContact)
            save()
            return newContact
        }
    }

    fun getContacts(): List<Contact> {
        return contactList.contacts
    }

    fun getContactNameListAdapter(context: Context): ArrayAdapter<String> {
        val names = contactList.contacts.map { it.name }
        return ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, names)
    }

    fun getContactSuggestionListAdapter(context: Context): ArrayAdapter<String> {
        val labels = contactList.contacts.map { toSuggestionLabel(it.name, it.hasAccount) }
        return ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, labels)
    }

    fun clearList() {
        contactList.clear()
        save()
    }

    fun setContacts(contacts: List<Contact>) {
        contactList.clear()
        contacts.forEach { contactList.addContact(it) }
        save()
    }

    private fun save() {
        try {
            val fos = context.openFileOutput(filename, Context.MODE_PRIVATE)
            val oos = ObjectOutputStream(fos)
            oos.writeObject(contactList)
            oos.close()
            fos.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun loadContacts() {
        try {
            val fis = context.openFileInput(filename)
            val ois = ObjectInputStream(fis)
            contactList = ois.readObject() as ContactList
            ois.close()
            fis.close()
        } catch (_: Exception) {
            contactList = ContactList()
        }
    }
}
