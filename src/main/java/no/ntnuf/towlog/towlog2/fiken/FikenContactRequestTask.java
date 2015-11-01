package no.ntnuf.towlog.towlog2.fiken;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.springframework.http.HttpAuthentication;
import org.springframework.http.HttpBasicAuthentication;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import no.ntnuf.towlog.towlog2.common.ContactListManager;

/**
 * Created by Christian on 03.01.2016.
 */
public class FikenContactRequestTask extends AsyncTask<Void, Void, FikenContactList> {

    Context context;
    AlertDialog alertDialog;
    ContactListManager contactListManager;

    public SharedPreferences settings;

    public void setContext(Context context) {
        this.context = context;
    }

    public void setDialog(AlertDialog alertDialog) {
        this.alertDialog = alertDialog;
    }

    public void setContactListManager(ContactListManager contactListManager) {
        this.contactListManager = contactListManager;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (alertDialog != null) {
            alertDialog.show();
        }
        settings = PreferenceManager.getDefaultSharedPreferences(context);
    }

    protected FikenContactList doInBackground(Void... params) {
        try {
            //final String url = "https://fiken.no/api/v1/whoAmI";
            String url = settings.getString("fiken_api_url","");
            String username = settings.getString("fiken_api_username","");
            String password = settings.getString("fiken_api_password","");


            // Set the username and password for creating a Basic Auth request
            HttpAuthentication authHeader = new HttpBasicAuthentication(username, password);
            HttpHeaders requestHeaders = new HttpHeaders();
            requestHeaders.setAuthorization(authHeader);
            HttpEntity<?> requestEntity = new HttpEntity<Object>(requestHeaders);

            RestTemplate restTemplate = new RestTemplate();
            StringHttpMessageConverter mj = new StringHttpMessageConverter();
            restTemplate.getMessageConverters().add(mj);

            // Execute the GET request
            ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);

            // Return a new contact list
            return new FikenContactList( res.getBody() );

        } catch (Exception e) {
            Log.e("FikenContactRequestTask", e.getMessage(), e);
        }

        return null;
    }

    @Override
    protected void onPostExecute(FikenContactList fikenContactList) {
        if (fikenContactList == null) {
            // We failed to load the list for some reasong (connection, authentication or similar)
            Toast.makeText(context, "Failed to load contacts", Toast.LENGTH_LONG).show();
        } else {
            // Loaded it succesfully
            Log.e("FikenContactRequestTask", "Loaded num contacts: " + String.valueOf(fikenContactList.contacts.size()));
            if (contactListManager != null) {
                contactListManager.set_fiken_contacts(fikenContactList);
            }

            Toast.makeText(context, "Loaded contacts", Toast.LENGTH_LONG).show();
        }
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
        }
    }

}

