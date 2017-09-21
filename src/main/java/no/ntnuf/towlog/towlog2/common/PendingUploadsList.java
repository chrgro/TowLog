package no.ntnuf.towlog.towlog2.common;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created by Christian on 21.09.2017.
 */

public class PendingUploadsList implements Serializable {

    private static final long serialVersionUID = 1L;

    public ArrayList<File> pending_files;

    public PendingUploadsList() {
        pending_files = new ArrayList<>();
    }
}
