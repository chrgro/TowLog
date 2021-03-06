package no.ntnuf.towlog.towlog2.common;

import java.io.Serializable;
import java.util.Date;

/**
 * Created by Christian on 21.10.2015.
 */
public class TowEntry implements Serializable {

    private static final long serialVersionUID = 4L;

    public int height = 0;

    public Contact pilot;
    public Contact copilot;

    public Date towStarted;

    public String registration = "";
    public String notes = "";

    public String gpx_filename = null;
    public String gpx_body = null;

}
