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
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import no.ntnuf.towlog.towlog2.common.DayLog;

/**
 * Created by Christian on 03.01.2016.
 */
public class FikenInvoicePushTask extends AsyncTask<Void, Void, DayLog> {

    Context context;
    AlertDialog alertDialog;
    DayLog daylog;

    private SharedPreferences settings;

    public void setContext(Context context) {
        this.context = context;
    }

    public void setDialog(AlertDialog alertDialog) {
        this.alertDialog = alertDialog;
    }

    public void setDayLog(DayLog daylog) {
        this.daylog = daylog;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (alertDialog != null) {
            alertDialog.show();
        }

        settings = PreferenceManager.getDefaultSharedPreferences(context);
    }

    protected DayLog doInBackground(Void... params) {
        try {
            //final String url = "https://fiken.no/api/v1/whoAmI";
            String url = settings.getString("fiken_api_url","");
            String username = settings.getString("fiken_api_username","");
            String password = settings.getString("fiken_api_password","");

            MultiValueMap<String, String> body = new LinkedMultiValueMap<String, String>();


            // Set the username and password for creating a Basic Auth request
            HttpAuthentication authHeader = new HttpBasicAuthentication(username, password);
            HttpHeaders requestHeaders = new HttpHeaders();


            requestHeaders.setAuthorization(authHeader);
            HttpEntity<?> requestEntity = new HttpEntity<Object>(requestHeaders);

            RestTemplate restTemplate = new RestTemplate();
            restTemplate.getMessageConverters().add(new StringHttpMessageConverter());

            // Execute the GET request
            //ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);

            // Return a new contact list
            return daylog;

        } catch (Exception e) {
            Log.e("FikenContactRequestTask", e.getMessage(), e);
        }

        return null;
    }

    @Override
    protected void onPostExecute(DayLog daylog) {

        // Dismiss the alert dialog and show success message
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
        }
        Toast.makeText(context, "This feature is not implemented yet. Sorry!", Toast.LENGTH_LONG).show();
    }

}