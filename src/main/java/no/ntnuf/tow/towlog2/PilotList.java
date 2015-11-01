package no.ntnuf.tow.towlog2;

import android.content.Context;
import android.util.Log;
import android.widget.ArrayAdapter;

import java.util.ArrayList;

/**
 * Maintain a list of pilot names, up to 100
 *
 */
public class PilotList {

    private final int MAX_PILOTS = 100;

    private ArrayList<String> pilots;
    private ArrayAdapter<String> adapter;
    private Context context;

    // TODO: Load some previous data here
    public PilotList(Context context) {
        this.context = context;

        pilots = new ArrayList<String>();

        adapter = new ArrayAdapter<String>(context,
                android.R.layout.simple_dropdown_item_1line, pilots);

        // TODO: Remove initial list
        // TODO: Load initial list from storage instead
        pilots.add("Christian Grøvdal");
        pilots.add("Vegard Flatjord");
        pilots.add("Vegard Bokalrud");
        pilots.add("Vegard Johansen");
        pilots.add("Vegard Nielsen");
        pilots.add("Vegard Fredriksen");
        pilots.add("Jan Erik Arendal");
    }

    public void addPilot(String pilot) {
        int found = pilots.indexOf(pilot);
        if (found >= 0) {
            pilots.remove(found);
        }
        pilots.add(0, pilot);
        if (pilots.size() >= MAX_PILOTS) {
            pilots.remove(pilots.size()-1);
        }
    }



    public ArrayAdapter<String> getPilotListAdapter() {
        return adapter;
    }
}
