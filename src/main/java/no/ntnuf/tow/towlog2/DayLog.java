package no.ntnuf.tow.towlog2;

import android.content.Context;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by Christian on 21.10.2015.
 */
public class DayLog implements Serializable{

    private static final long serialVersionUID = 1L;

    String towpilot;
    Date date;

    ArrayList<TowEntry> tows;

    public DayLog(Context context) {

        tows = new ArrayList<TowEntry>();
    }

}
