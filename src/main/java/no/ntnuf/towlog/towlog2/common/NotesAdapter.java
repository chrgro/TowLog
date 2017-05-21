package no.ntnuf.towlog.towlog2.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ArrayAdapter;

import java.util.ArrayList;

// An autocomplete input field adapter for the Notes on a new tow
// Grab default set of notes from the settings.

public class NotesAdapter {

    private ArrayList<String> notes;
    private ArrayAdapter<String> adapter;
    private Context context;

    public NotesAdapter(Context context, SharedPreferences settings) {
        ArrayList<String> notes = new ArrayList<>();

        String defaultnotes = settings.getString("default_notes","");
        String[] notesarray = defaultnotes.split(",");
        for (String s : notesarray) {
            notes.add(s);
        }
        adapter = new ArrayAdapter<String>(context,
                android.R.layout.simple_dropdown_item_1line, notesarray);
    }

    public ArrayAdapter<String> getNotesAdapter() {
        return adapter;
    }
}
