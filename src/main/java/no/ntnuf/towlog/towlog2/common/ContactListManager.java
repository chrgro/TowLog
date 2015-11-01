package no.ntnuf.towlog.towlog2.common;

import android.content.Context;
import android.util.Log;
import android.widget.ArrayAdapter;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.StreamCorruptedException;

import no.ntnuf.towlog.towlog2.fiken.FikenContact;
import no.ntnuf.towlog.towlog2.fiken.FikenContactList;

/**
 * Maintain a list of contacts, and loads/stores them
 *
 */
public class ContactListManager {

    private ContactList contactlist;
    private ArrayAdapter<Contact> adapter;

    private Context context;

    String filename_contactlist = "contactlist.serialized";

    public ContactListManager(Context context) {
        this.context = context;

        if (!load_local_contacts()) {
            //Log.e("ContactListManager", "Creating empty contact list");
            contactlist = new ContactList();
        }

        //Log.e("ContactListManager", "New contactlist, containing "+String.valueOf(contactlist.contacts.size()));

        adapter = new ArrayAdapter<Contact>(context,
                android.R.layout.simple_dropdown_item_1line, contactlist.contacts);
    }

    public boolean clearList() {
        contactlist = new ContactList();
        return save();
    }

    public Contact findContactFromName(String name) {
        if (contactlist!=null) {
            return contactlist.findContactFromName(name);
        }
        return null;
    }

    // Save a new contact with name, return that contact
    public Contact saveContact(String contactname) {
        Contact found = findContactFromName(contactname);
        Contact newcontact = null;
        if (found != null) {

        } else {
            newcontact = new Contact();
            newcontact.name = contactname;
            contactlist.addContact(newcontact);
        }
        save();
        return newcontact;
    }

    // Save a new contact, just in contact form
    public void saveContact(Contact contact) {
        contactlist.addContact(contact);
        save();
    }

    // Save to file
    private boolean save() {
        FileOutputStream fos = null;

        try {
            fos = context.openFileOutput(filename_contactlist, context.MODE_PRIVATE);
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(contactlist);
            os.close();
            fos.close();
            //Log.e("ContactListManager", "Saved contactlist to file "+ filename_contactlist);
            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e("ContactListManager", "While saving, file not found" + filename_contactlist);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("ContactListManager", "IO Exception during saving" + filename_contactlist);
        }
        return false;
    }

    // Load from file
    private boolean load_local_contacts() {
        boolean res = false;
        try {
            FileInputStream fis = context.openFileInput(filename_contactlist);
            ObjectInputStream is = new ObjectInputStream(fis);
            contactlist = (ContactList) is.readObject();
            is.close();
            fis.close();
            res = true;
            //Log.e("ContactListManager", "Succesfully loaded contact list!");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            Log.e("ContactListManager", "Class not found");
        } catch (OptionalDataException e) {
            e.printStackTrace();
            Log.e("ContactListManager", "Optional Data Exception");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e("ContactListManager", "File not found");
        } catch (StreamCorruptedException e) {
            e.printStackTrace();
            Log.e("ContactListManager", "Stream Corrupted");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("ContactListManager", "IO Exception: "+e.getStackTrace().toString());
        }
        return res;
    }

    public void set_fiken_contacts(FikenContactList fikenContactList) {
        // Clear the old list
        contactlist.clear();

        // Add all fiken contacts
        for (FikenContact fikenContact : fikenContactList.contacts) {
            contactlist.addContact(fikenContact);
        }

        // Save it to file
        save();
    }





    // Return the adapter form of the pilot list
    public ArrayAdapter<Contact> getContactNameListAdapter() {
        return adapter;
    }
}
