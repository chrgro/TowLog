package no.ntnuf.towlog.towlog2.common;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created by Christian on 02.01.2016.
 */
public class ContactList implements Serializable {

    private static final long serialVersionUID = 2L;

    public ArrayList<Contact> contacts;

    // Initialize (or clear) the contact list
    public ContactList() {
        contacts = new ArrayList<>();
    }

    public void clear() {
        contacts.clear();
    }

    public Contact findContactFromName(String name) {
        boolean found = false;
        for (int i = 0; i < contacts.size(); i++) {
            if (contacts.get(i).name.equals(name)) {
                return contacts.get(i);
            }
        }
        return null;
    }

    // Add a contact
    public void addContact(Contact c) {
        Contact existing = findContactFromName(c.name);
        if (existing != null) {
            // Oh shit, we have two contacts with the same name
        } else {
            contacts.add(c);
        }
    }

    // Remove contact
    public void remove(int index) {
        contacts.remove(index);
    }

}
