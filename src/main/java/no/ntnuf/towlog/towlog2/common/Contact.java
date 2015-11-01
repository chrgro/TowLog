package no.ntnuf.towlog.towlog2.common;

import java.io.Serializable;

/**
 * Created by Christian on 03.01.2016.
 */
public class Contact implements Serializable {


    private static final long serialVersionUID = 2L;

    public String name;

    public String self;

    public boolean hasAccount;

    public int customerNumber;
    public int supplierNumber;

    public String email;

    public String toString() {
        return name;
    }

}
