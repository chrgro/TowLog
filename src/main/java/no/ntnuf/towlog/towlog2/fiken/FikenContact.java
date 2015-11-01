package no.ntnuf.towlog.towlog2.fiken;

import org.json.JSONException;
import org.json.JSONObject;

import no.ntnuf.towlog.towlog2.common.Contact;

/**
 * Created by Christian on 02.01.2016.
 */
public class FikenContact extends Contact {

    // Parse a contact into the required fields
    public static FikenContact createContact(JSONObject root) {
        try {
            FikenContact contact = new FikenContact();

            contact.self = root.getJSONObject("_links").getJSONObject("self").getString("href");
            contact.name = root.getString("name");
            contact.hasAccount = true;

            if (root.has("customerNumber")) {
                contact.customerNumber = root.getInt("customerNumber");
            }
            if (root.has("supplierNumber")) {
                contact.supplierNumber = root.getInt("supplierNumber");
            }

            if (root.has("email")) {
                contact.email = root.getString("email");
            }

            return contact;

        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

}
