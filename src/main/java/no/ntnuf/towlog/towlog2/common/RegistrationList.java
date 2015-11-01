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
import java.util.ArrayList;

/**
 * Maintains a persistent list of aircraft callsigns, to be able to quickly select previously
 * used registrations.
 */
public class RegistrationList {
    private final int MAX_REGISTRATIONS = 100;

    private ArrayList<String> registrations;
    private ArrayAdapter<String> adapter;
    private Context context;

    String filename_registrationlist = "registrationlist.serialized";

    public RegistrationList(Context context) {
        this.context = context;

        if (!load()) {
            Log.e("RegistrationList", "Creating empty registration list");
            registrations = new ArrayList<String>();

        }

        adapter = new ArrayAdapter<String>(context,
                android.R.layout.simple_dropdown_item_1line, registrations);
    }

    public boolean clearList() {
        registrations = new ArrayList<String>();
        return save();
    }

    public void addRegistration(String registration) {
        registration = registration.toUpperCase();

        int found = registrations.indexOf(registration);
        if (found >= 0) {
            registrations.remove(found);
        }
        registrations.add(0, registration);
        if (registrations.size() >= MAX_REGISTRATIONS) {
            registrations.remove(registrations.size()-1);
        }
        save();
    }

    // Save to file
    private boolean save() {
        FileOutputStream fos;

        try {
            fos = context.openFileOutput(filename_registrationlist, Context.MODE_PRIVATE);
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(registrations);
            os.close();
            fos.close();
            Log.e("RegistrationList", "Saved registrationlist to file "+ filename_registrationlist);
            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e("RegistrationList", "While saving, file not found" + filename_registrationlist);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("RegistrationList", "IO Exception during saving" + filename_registrationlist);
        }
        return false;
    }

    // Load from file
    private boolean load() {
        boolean res = false;
        try {
            FileInputStream fis = context.openFileInput(filename_registrationlist);
            ObjectInputStream is = new ObjectInputStream(fis);
            registrations = (ArrayList<String>) is.readObject();
            is.close();
            fis.close();
            res = true;
            Log.e("RegistrationList", "Succesfully loaded registration list!");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (OptionalDataException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e("RegistrationList", "File not found");
        } catch (StreamCorruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res;
    }

    // Return the adapter form of the registration list
    public ArrayAdapter<String> getRegistrationListAdapter() {
        return adapter;
    }
}
