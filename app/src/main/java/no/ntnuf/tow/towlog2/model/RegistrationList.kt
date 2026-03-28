package no.ntnuf.tow.towlog2.model

import android.content.Context
import android.widget.ArrayAdapter
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class RegistrationList(private val context: Context) {
    private var registrationList: ArrayList<String> = ArrayList()
    private val filename = "registrationlist.serialized"

    init {
        loadList()
    }

    fun findRegistration(reg: String): String? {
        return registrationList.find { it == reg }
    }

    fun addRegistration(reg: String) {
        if (!registrationList.contains(reg)) {
            registrationList.add(reg)
            save()
        }
    }

    fun getList(): List<String> {
        return registrationList
    }

    fun getRegistrationListAdapter(context: Context): ArrayAdapter<String> {
        return ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, registrationList)
    }

    fun clearList() {
        registrationList.clear()
        save()
    }

    private fun save() {
        try {
            val fos = context.openFileOutput(filename, Context.MODE_PRIVATE)
            val oos = ObjectOutputStream(fos)
            oos.writeObject(registrationList)
            oos.close()
            fos.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun loadList() {
        try {
            val fis = context.openFileInput(filename)
            val ois = ObjectInputStream(fis)
            registrationList = ois.readObject() as ArrayList<String>
            ois.close()
            fis.close()
        } catch (e: Exception) {
            registrationList = ArrayList()
        }
    }
}
