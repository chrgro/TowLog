package no.ntnuf.towlog.towlog2.fiken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created by Christian on 02.01.2016.
 */
public class FikenContactList implements Serializable {

    public String self;

    public ArrayList<FikenContact> contacts;

    // Parse a list of contacts
    public FikenContactList(String in) {
        try {
            JSONObject root = new JSONObject(in);

            this.self = root.getJSONObject("_links").getJSONObject("self").getString("href");
            this.contacts = new ArrayList<>();

            JSONObject emb = root.getJSONObject("_embedded");
            JSONArray jsoncontacts = emb.getJSONArray("https://fiken.no/api/v1/rel/contacts");

            for (int i = 0; i < jsoncontacts.length(); i++) {
                FikenContact contact = FikenContact.createContact(jsoncontacts.getJSONObject(i));
                if (contact != null) {
                    this.contacts.add(contact);
                }
            }


        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

}
