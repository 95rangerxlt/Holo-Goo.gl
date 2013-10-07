package com.pandanomic.hologoogl;
/*
 * Copyright 2013 Chris Banes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Html;
import android.text.format.Time;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.andreabaccega.formedittextvalidator.WebUrlValidator;
import com.andreabaccega.widget.FormEditText;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;

import uk.co.senab.actionbarpulltorefresh.library.DefaultHeaderTransformer;
import uk.co.senab.actionbarpulltorefresh.library.PullToRefreshAttacher;

import com.google.ads.*;

public class URLListActivity extends ListActivity
        implements PullToRefreshAttacher.OnRefreshListener {

    private static final int AUTHORIZATION_CODE = 1993;
    private static final int ACCOUNT_CODE = 1601;
    private AuthPreferences mAuthPreferences;
    private AccountManager mAccountManager;
    private final String SCOPE = "https://www.googleapis.com/auth/urlshortener";
    private final String LOGTAG = "Main Activity";
    private boolean mGoogleLoggedIn = false;
    private final int APIVERSION = Build.VERSION.SDK_INT;
    private Menu mOptionsMenu;
    private SimpleAdapter mAdapter;
    private static ArrayList<HashMap<String, Object>> mURLListItems = new ArrayList<HashMap<String, Object>>();
    private PullToRefreshAttacher mPullToRefreshAttacher;
    private boolean mRefreshing;
    private AdView mAdView;

    static {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("title", "http://goo.gl/SYFV4");
        map.put("clicks", "498");
        map.put("longurl", "www.somelongurl.com/dsfgkljheiubs");
        mURLListItems.add(map);

        HashMap<String, Object> map1 = new HashMap<String, Object>();
        map1.put("title", "http://goo.gl/4DR2e");
        map1.put("clicks", "360");
        map1.put("longurl", "Pull down to refresh!");
        mURLListItems.add(map1);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_url_list);
        /**
         * Get ListView and give it an adapter to display the sample items
         */
        ListView listView = getListView();
        String[] from = new String[] {"title", "longurl", "clicks"};
        int[] to = new int[] { R.id.title, R.id.longurl, R.id.clicks};
        mAdapter = new SimpleAdapter(this, mURLListItems, R.layout.url_list_item,
                from, to);
        setListAdapter(mAdapter);

        /**
         * Here we create a PullToRefreshAttacher manually without an Options instance.
         * PullToRefreshAttacher will manually create one using default values.
         */
        mPullToRefreshAttacher = PullToRefreshAttacher.get(this);

        // Set the Refreshable View to be the ListView and the refresh listener to be this.
        mPullToRefreshAttacher.addRefreshableView(listView, this);

        DefaultHeaderTransformer ht = (DefaultHeaderTransformer) mPullToRefreshAttacher.getHeaderTransformer();
        ht.setPullText("Swipe down to refresh");
        ht.setRefreshingText("Refreshing");

        mRefreshing = false;

        mAccountManager = AccountManager.get(this);
        mAuthPreferences = new AuthPreferences(this);
        mGoogleLoggedIn = mAuthPreferences.loggedIn();
        if (mAuthPreferences.getUser() != null && mAuthPreferences.getToken() != null) {
            // Account exists, reauthorize stuff and make button say log out
            reauthorizeGoogle();
            mGoogleLoggedIn = true;
        } else {
            // No account, refresh only anonymous ones and leave button alone
        }

        mAdView = (AdView) findViewById(R.id.ad);

        AdRequest adRequest = new AdRequest();
        adRequest.addKeyword("technology");
        mAdView.loadAd(adRequest);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        // Do something when a list item is clicked
        if (!mRefreshing) {
            HashMap<String, Object> map = mURLListItems.get(position);
            String shortURL = (String) map.get("title");
            Log.i("itemClicked", Boolean.toString(mRefreshing));
            refreshMetrics task = new refreshMetrics();
            task.execute(shortURL);
        }
    }

    @Override
    public void onRefreshStarted(View view) {
        if (!checkNetwork() || mRefreshing) {
            stopRefreshAnimation();
            return;
        }

        if (!mGoogleLoggedIn) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_LONG).show();
            stopRefreshAnimation();
            return;
        }

        startRefreshAnimation("Refreshing...");
        String authToken = mAuthPreferences.getToken();
//        new RefreshListTask().execute(authToken);
        new RefreshHistoryTask().execute(authToken);
    }

    public void startRefreshAnimation(String message) {
        assert !mRefreshing;
        mRefreshing = true;
        ((DefaultHeaderTransformer) mPullToRefreshAttacher.getHeaderTransformer()).setRefreshingText(message);
        mPullToRefreshAttacher.setRefreshing(true);
    }

    public void stopRefreshAnimation() {
        mPullToRefreshAttacher.setRefreshComplete();
        mRefreshing = false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.shorten_new_URL:
                newURLDialog();
                return true;
//            case R.id.refresh_url_list:
//                onRefreshStarted(getListView());
//                return true;
            case R.id.login:
                accountSetup();
                return true;
            case R.id.logout:
                logout();
                Toast.makeText(this, "You are now logged out", Toast.LENGTH_LONG).show();
                invalidateOptionsMenu();
                return true;
            case R.id.action_settings:
                return true;
            case R.id.about:
                aboutDialog();
                return true;
            case R.id.send_feedback:
                sendFeedback();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void aboutDialog() {
        Dialog dialog = null;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        Context context = getApplicationContext();
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.dialog_about, null);
        builder.setView(layout);
        builder.setPositiveButton("OK", null);
        dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.v(LOGTAG, "Resumed");
//        reauthorizeGoogle();
//        if (mGoogleLoggedIn) {
//            mPullToRefreshAttacher.setRefreshing(true);
//            onRefreshStarted(getListView());
//        }
        // TODO: This for whatever reason gets an invalid credentials error from google
    }

    @Override
    public void onDestroy() {
        if (mAdView != null) {
            mAdView.removeAllViews();
            mAdView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.mOptionsMenu = menu;
        getMenuInflater().inflate(R.menu.urllist_menu, menu);

        if (mGoogleLoggedIn) {
            menu.findItem(R.id.login).setVisible(false);
            menu.findItem(R.id.logout).setVisible(true);
        } else {
            menu.findItem(R.id.login).setVisible(true);
            menu.findItem(R.id.logout).setVisible(false);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mGoogleLoggedIn) {
            menu.findItem(R.id.login).setVisible(false);
            menu.findItem(R.id.logout).setVisible(true);
        } else {
            menu.findItem(R.id.login).setVisible(true);
            menu.findItem(R.id.logout).setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    public void accountSetup() {
        Intent intent = AccountManager.newChooseAccountIntent(null, null, new String[] {"com.google"}, false, null, null, null, null);
        startActivityForResult(intent, ACCOUNT_CODE);
    }

    private void requestToken(boolean passive) {
        Account userAccount = null;
        String user = mAuthPreferences.getUser();
        for (Account account : mAccountManager.getAccountsByType("com.google")) {
            if (account.name.equals(user)) {
                userAccount = account;
                break;
            }
        }

        mAccountManager.getAuthToken(userAccount, "oauth2:" + SCOPE, null, this,
                new OnTokenAcquired(passive), null);
    }

    /**
     * call this method if your token expired, or you want to request a new
     * token for whatever reason. call requestToken() again afterwards in order
     * to get a new token.
     */
    private void invalidateToken() {
        AccountManager accountManager = AccountManager.get(this);
        accountManager.invalidateAuthToken("com.google",
                mAuthPreferences.getToken());

        mAuthPreferences.setToken(null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == AUTHORIZATION_CODE) {
                requestToken(false);
            } else if (requestCode == ACCOUNT_CODE) {
                String accountName = data
                        .getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                mAuthPreferences.setUser(accountName);

                // invalidate old tokens which might be cached. we want a fresh
                // one, which is guaranteed to work
                invalidateToken();

                requestToken(false);
            }
        }
    }

    private class OnTokenAcquired implements AccountManagerCallback<Bundle> {

        private boolean passive = false;

        public OnTokenAcquired(boolean passive) {
            this.passive = passive;
        }

        @Override
        public void run(AccountManagerFuture<Bundle> result) {
            try {
                Bundle bundle = result.getResult();

                Intent launch = (Intent) bundle.get(AccountManager.KEY_INTENT);
                if (launch != null) {
                    startActivityForResult(launch, AUTHORIZATION_CODE);
                } else {
                    String token = bundle
                            .getString(AccountManager.KEY_AUTHTOKEN);

                    mAuthPreferences.setToken(token);
                    if (!passive) {
                        Intent intent = new Intent(URLListActivity.this, URLListActivity.class);
                        startActivity(intent);
                        finish();
                    }
                }
            } catch (Exception e) {
                Log.e("OnTokenAcquired run", "Failed to acquire token");
            }
        }
    }

    private void logout() {
        invalidateToken();
        mAuthPreferences.logout();
        mGoogleLoggedIn = false;
        mURLListItems.clear();
        mAdapter.notifyDataSetChanged();
        Intent intent = new Intent(URLListActivity.this, URLListActivity.class);
        startActivity(intent);
        finish();
    }

    public void refreshCallback(JSONObject result) {
        Log.v(LOGTAG, "Finished refresh, parsing result");
        if (result == null) {
            Toast.makeText(this, "Error retrieving data", Toast.LENGTH_LONG).show();
            return;
        }

//        Log.v("JSON RESULT", result.toString());

        try {
            int totalItems = result.getInt("totalItems");

            JSONArray array = result.getJSONArray("items");
//            Log.d("array", totalItems + " " + array.toString());

            JSONObject tmpobj = array.getJSONObject(0);
//            Log.d("object", tmpobj.getString("id"));
            mURLListItems.clear();
            for (int i = 0; i < 30; ++i) {
                URLMetrics metrics = new URLMetrics(array.getJSONObject(i));
                HashMap<String, Object> map = new HashMap<String, Object>();
                map.put("title", metrics.getShortURL());
                map.put("longurl", metrics.getLongURL());
                map.put("clicks", "" + ((int) (Math.random() * ((999) + 1))));
                mURLListItems.add(map);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        mAdapter.notifyDataSetChanged();
        stopRefreshAnimation();
    }

    @TargetApi(17)
    private void newURLDialog() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Shorten New URL");
        alert.setCancelable(true);

        LayoutInflater layoutInflater = LayoutInflater.from(this);
        View dialog_layout = layoutInflater.inflate(R.layout.edittext_url, null);

        final FormEditText input = (FormEditText) dialog_layout.findViewById(R.id.et_url);
        input.addValidator(new WebUrlValidator("Not a valid URL"));

        if (input == null) {
            Log.e(LOGTAG, "input is null");
            return;
        }

        alert.setView(dialog_layout);
        alert.setPositiveButton("Shorten", null);
        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                hideKeyboard(input);
            }
        });

        // hide keyboard if the dialog is dismissed
        if (APIVERSION >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    hideKeyboard(input);
                }
            });
        }

        final AlertDialog dialog = alert.create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogI) {

                Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                assert positive != null;    // To get AS to stop bugging me about nullpointer checks
                positive.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (input.testValidity()) {
                            String urlToShare = input.getText().toString();

                            // Make sure it's not empty
                            if (urlToShare == null || urlToShare.matches("")) {
                                Toast.makeText(getBaseContext(), "No URL Entered", Toast.LENGTH_LONG).show();
                            } else if (!Patterns.WEB_URL.matcher(urlToShare).matches()) {
                                // Validate URL pattern
                                Toast.makeText(getBaseContext(), "Invalid URL", Toast.LENGTH_LONG).show();
                            } else {
                                hideKeyboard(input);
                                // Let's go get that URL!
                                // Trim any trailing spaces (sometimes keyboards will autocorrect .com with a space at the end)
                                generateShortenedURL(urlToShare.trim());
                                dialog.dismiss();
                            }
                        }
                    }
                });

            }
        });

        dialog.show();

        // Show keyboard
        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                input.post(new Runnable() {
                    @Override
                    public void run() {
                        InputMethodManager imm = (InputMethodManager) getBaseContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                    }
                });
            }
        });
        input.requestFocus();
    }

    private void hideKeyboard(EditText input) {
        InputMethodManager imm = (InputMethodManager)getSystemService(
                Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    private void generateShortenedURL(String input) {
        if (!checkNetwork()) {
            return;
        }
        ShortenURLTask shortenURLTask;

        if (mGoogleLoggedIn) {
            shortenURLTask = new ShortenURLTask(mAuthPreferences.getToken());
        } else {
            shortenURLTask = new ShortenURLTask();
        }

        shortenURLTask.execute(input);
    }

    @TargetApi(17)
    private void displayShortenedURL(final String resultURL) {
        Log.d("hologoogl", "done generating");

        Log.d("hologoogl", "Generated " + resultURL);

        if (resultURL.equals("none")) {
            Toast.makeText(this, "Parse error", Toast.LENGTH_LONG).show();
            return;
        }

        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(resultURL)
                .setCancelable(true)
                .setPositiveButton("Share", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        shareURL(resultURL);
                    }
                });

        alert.setNegativeButton("Copy", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                copyURL(resultURL);
            }
        });

        if (APIVERSION >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (!mGoogleLoggedIn) {
                        HashMap<String, Object> map = new HashMap<String, Object>();
                        map.put("title", resultURL);
                        Time now = new Time();
                        now.setToNow();
                        map.put("createddate", now.toString());
                        map.put("longurl", "N/A");
                        mURLListItems.add(map);
                        mAdapter.notifyDataSetChanged();
                    }
                }
            });
        }
        alert.show();
    }

    private void copyURL(String input) {
        ClipboardManager clipboard = (ClipboardManager)
                getBaseContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Shortened URL", input);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getBaseContext(), "Copied to clipboard!", Toast.LENGTH_SHORT).show();
    }

    private void shareURL(String input) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, input);
        intent.putExtra(Intent.EXTRA_SUBJECT, "Shared from Holo Goo.gl");
        startActivity(Intent.createChooser(intent, "Share"));
    }

    private void sendFeedback() {
        Intent gmail = new Intent(Intent.ACTION_VIEW);
        gmail.setClassName("com.google.android.gm", "com.google.android.gm.ComposeActivityGmail");
        gmail.putExtra(Intent.EXTRA_EMAIL, new String[] { "pandanomic@gmail.com" });
        gmail.setData(Uri.parse("pandanomic@gmail.com"));
        gmail.putExtra(Intent.EXTRA_SUBJECT, "Holo Goo.gl Feedback");
        gmail.setType("plain/text");
        startActivity(gmail);
    }

    private boolean checkNetwork() {
        ConnectivityManager connectivityManager = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        boolean airplaneMode = checkAirplaneMode();
        if (!(networkInfo != null && networkInfo.isConnected())) {
            if (airplaneMode) {
                Toast.makeText(this, "Please disable airplane mode or turn on WiFi first!", Toast.LENGTH_LONG).show();
                return false;
            }
            Toast.makeText(this, "Could not connect, please check your internet connection.", Toast.LENGTH_LONG).show();
            return false;
        }

        return true;
    }

    private boolean checkAirplaneMode() {
        if (APIVERSION >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return Settings.System.getInt(getBaseContext().getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        } else {
            return Settings.System.getInt(getBaseContext().getContentResolver(),
                    Settings.System.AIRPLANE_MODE_ON, 0) != 0;
        }
    }

    private void reauthorizeGoogle() {
        // if response is a 401-unauthorized
        Log.i("URLList", "reauthorizing");
        invalidateToken();
        requestToken(true);
    }

    public void updateHistory(ArrayList<URLMetrics> metrics) {
        mURLListItems.clear();
        for (URLMetrics metric : metrics) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("title", metric.getShortURL());
            map.put("longurl", metric.getLongURL());
            map.put("clicks", "" + metric.getClicks());
            mURLListItems.add(map);
        }

        mAdapter.notifyDataSetChanged();
        stopRefreshAnimation();
    }

    private class RefreshListTask extends AsyncTask<String, Void, JSONObject> {
        private String GETURL = "https://www.googleapis.com/urlshortener/v1/url/history";
        private final String LOGTAG = "RefreshHistory";

        @Override
        protected void onPreExecute() {
//            this.progressDialog.show();
        }

        @Override
        protected JSONObject doInBackground(String... params) {
            String authToken;

            if (!checkNetwork()) {
                Toast.makeText(getParent(), "Not connected to the internet", Toast.LENGTH_LONG).show();
                Log.e(LOGTAG, "No connection");
                return null;
            }

            authToken = params[0];

            JSONObject results;

            Log.d(LOGTAG, "Fetching data");
            try {
                HttpParams httpParams = new BasicHttpParams();
                HttpConnectionParams.setConnectionTimeout(httpParams, 5000);
                HttpConnectionParams.setSoTimeout(httpParams, 5000);

                DefaultHttpClient client = new DefaultHttpClient(httpParams);
                HttpGet get = new HttpGet(GETURL);

                get.setHeader("Authorization", "Bearer " + authToken);

                Log.d(LOGTAG, "Requesting");
                HttpResponse response = client.execute(get);

                if (response.getStatusLine().getStatusCode() == 404) {
                    Log.e(LOGTAG, "404 Not found");
                    return null;
                }

                Log.d(LOGTAG, "Parsing response");
                BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
                StringBuilder builder = new StringBuilder();
                for (String line; (line = reader.readLine()) != null;) {
                    builder.append(line).append("\n");
                }

                results = new JSONObject(new JSONTokener(builder.toString()));
                Log.d(LOGTAG, "Finished parsing");
            } catch (Exception e) {
                e.printStackTrace();
                String errorMessage = "Error: ";
                if (e instanceof UnsupportedEncodingException) {
                    errorMessage += "Encoding exception";
                } else if (e instanceof ClientProtocolException) {
                    errorMessage += "POST exception";
                } else if (e instanceof IOException) {
                    errorMessage += "IO Exception in parsing response";
                } else {
                    errorMessage += "JSON parsing exception";
                }

                Log.e("history", errorMessage);
                return null;
            }
            return results;
        }

        /**
         * Post-execution stuff
         * @param result JSONObject result received from Goo.gl
         */
        protected void onPostExecute(JSONObject result) {
            refreshCallback(result);
        }
    }

    private void metricsDialog(URLMetrics metrics) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Analytics")
                .setCancelable(true)
                .setNeutralButton("Done", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .setMessage(Html.fromHtml("<b><u> Short URL </u></b><br>" + metrics.getShortURL()
                                + "<br><br><b><u>Long URL </u></b><br>" + metrics.getLongURL()
                                + "<br><br><b><u>Clicks </u></b><br>" + metrics.getClicks()))
                .setNegativeButton("Details", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(getBaseContext(), "Coming soon", Toast.LENGTH_LONG).show();
                    }
                })
                .setPositiveButton("Share", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });

        alert.show();
        stopRefreshAnimation();
//        MetricsDialogFragment dialog = new MetricsDialogFragment();
//        dialog.show(getFragmentManager().beginTransaction(), "changelogdemo");
    }

    private class refreshMetrics extends AsyncTask<String, Void, URLMetrics> {
        private final String LOGTAG = "RefreshHistory";
        private String GETURL = "https://www.googleapis.com/urlshortener/v1/url";
        @Override
        protected void onPreExecute() {
            startRefreshAnimation("Retrieving analytics");
        }

        @Override
        protected URLMetrics doInBackground(String... params) {
            String shortenedURL = params[0];
            GETURL += "?shortUrl=" + shortenedURL;
            GETURL += "&projection=FULL";

            JSONObject results;

            Log.d("googl", "Fetching data");
            try {
                HttpParams httpParams = new BasicHttpParams();
                HttpConnectionParams.setConnectionTimeout(httpParams, 5000);
                HttpConnectionParams.setSoTimeout(httpParams, 5000);

                DefaultHttpClient client = new DefaultHttpClient(httpParams);
                HttpGet get = new HttpGet(GETURL);

                HttpResponse response = client.execute(get);

                BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
                StringBuilder builder = new StringBuilder();
                for (String line; (line = reader.readLine()) != null;) {
                    builder.append(line).append("\n");
                }

                results = new JSONObject(new JSONTokener(builder.toString()));
                Log.d("hologoogl", results.toString());
            } catch (Exception e) {
                e.printStackTrace();
                String errorMessage = "Error: ";
                if (e instanceof UnsupportedEncodingException) {
                    errorMessage += "Encoding exception";
                } else if (e instanceof ClientProtocolException) {
                    errorMessage += "POST exception";
                } else if (e instanceof IOException) {
                    errorMessage += "IO Exception in parsing response";
                } else {
                    errorMessage += "JSON parsing exception";
                }

                Log.e("googl:retrieveURLTask", errorMessage);
                return null;
            }

            return new URLMetrics(results);
        }

        /**
         * Post-execution stuff
         * @param result JSONObject result received from Goo.gl
         */
        protected void onPostExecute(URLMetrics result) {
            metricsDialog(result);
        }
    }

    private class ShortenURLTask extends AsyncTask<String, Void, JSONObject> {

        private String token;

        public ShortenURLTask() {

        }

        public ShortenURLTask(String authToken) {
            this.token = authToken;
        }

        @Override
        protected void onPreExecute() {
            startRefreshAnimation("Shortening URL...");
        }

        @Override
        protected JSONObject doInBackground(String... params) {
            String sharedURL = params[0];

            JSONObject results;

            Log.d("googl", "Fetching data");
            try {
                HttpParams httpParams = new BasicHttpParams();
                HttpConnectionParams.setConnectionTimeout(httpParams, 5000);
                HttpConnectionParams.setSoTimeout(httpParams, 5000);

                DefaultHttpClient client = new DefaultHttpClient(httpParams);
                HttpPost post = new HttpPost("https://www.googleapis.com/urlshortener/v1/url");
                post.setEntity(new StringEntity("{\"longUrl\": \"" + sharedURL + "\"}"));
                post.setHeader("Content-Type", "application/json");
                if (token != null) {
                    post.setHeader("Authorization", "Bearer " + token);
                }

                HttpResponse response = client.execute(post);

                BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
                StringBuilder builder = new StringBuilder();
                for (String line; (line = reader.readLine()) != null;) {
                    builder.append(line).append("\n");
                }

                results = new JSONObject(new JSONTokener(builder.toString()));
            } catch (Exception e) {
                e.printStackTrace();
                String errorMessage = "Error: ";
                if (e instanceof UnsupportedEncodingException) {
                    errorMessage += "Encoding exception";
                } else if (e instanceof ClientProtocolException) {
                    errorMessage += "POST exception";
                } else if (e instanceof IOException) {
                    errorMessage += "IO Exception in parsing response";
                } else {
                    errorMessage += "JSON parsing exception";
                }

                Log.e("ShortenURLTask", errorMessage);
                return null;
            }
            return results;
        }

        /**
         * Post-execution stuff
         * @param result JSONObject result received from Goo.gl
         */
        protected void onPostExecute(JSONObject result) {
            String shortenedURL = "none";

            try {
                shortenedURL = result.getString("id");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            stopRefreshAnimation();
            displayShortenedURL(shortenedURL);
        }
    }

    private class RefreshHistoryTask extends AsyncTask<String, Void, Boolean> {
        private final String LOGTAG = "RefreshHistory";
        private String HISTORYURL = "https://www.googleapis.com/urlshortener/v1/url/history";
        private String METRICSURL = "https://www.googleapis.com/urlshortener/v1/url";
        private ArrayList<URLMetrics> metricsList = new ArrayList<URLMetrics>();

        @Override
        protected void onPreExecute() {
            startRefreshAnimation("Retrieving history");
        }

        @Override
        protected Boolean doInBackground(String... params) {

            // Get history
            if (!checkNetwork()) {
                Toast.makeText(getParent(), "Not connected to the internet", Toast.LENGTH_LONG).show();
                Log.e(LOGTAG, "No connection");
                return null;
            }

            String authToken = params[0];

            JSONObject history_results = historyRequest(authToken);

            if (history_results == null) {
                return false;
            }

            // Got history, now create list of URLMetrics
            Log.d(LOGTAG, "Parsing history result");
            Log.d(LOGTAG, history_results.toString());


            int totalItems = 30;
            JSONArray itemsArray = null;
            try {
                totalItems = history_results.getInt("totalItems");
                itemsArray = history_results.getJSONArray("items");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            mURLListItems.clear();
            for (int i = 0; i < 30; ++i) {
                URLMetrics metric = null;
                try {
                    String id = itemsArray.getJSONObject(i).getString("id");
                    Log.i(LOGTAG, "id is " + id);
                    metric = new URLMetrics(id);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                refreshAnalytics(metric);
                metricsList.add(metric);
            }

            return true;
        }

        /**
         * Post-execution stuff
         * @param result JSONObject result received from Goo.gl
         */
        protected void onPostExecute(Boolean success) {
            if (success) {
                updateHistory(metricsList);
            }
        }

        private JSONObject historyRequest(String authToken) {
            JSONObject history_results = null;

            Log.d(LOGTAG, "Fetching data");
            try {
                HttpParams httpParams = new BasicHttpParams();
                HttpConnectionParams.setConnectionTimeout(httpParams, 5000);
                HttpConnectionParams.setSoTimeout(httpParams, 5000);

                DefaultHttpClient client = new DefaultHttpClient(httpParams);
                HttpGet get = new HttpGet(HISTORYURL);

                get.setHeader("Authorization", "Bearer " + authToken);

                Log.d(LOGTAG, "Requesting");
                HttpResponse response = client.execute(get);

                if (response.getStatusLine().getStatusCode() == 404) {
                    Log.e(LOGTAG, "404 Not found");
                    return null;
                }

                Log.d(LOGTAG, "Parsing response");
                BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
                StringBuilder builder = new StringBuilder();
                for (String line; (line = reader.readLine()) != null;) {
                    builder.append(line).append("\n");
                }

                history_results = new JSONObject(new JSONTokener(builder.toString()));
                Log.d(LOGTAG, "Finished parsing");
            } catch (Exception e) {
                e.printStackTrace();
                String errorMessage = "Error: ";
                if (e instanceof UnsupportedEncodingException) {
                    errorMessage += "Encoding exception";
                } else if (e instanceof ClientProtocolException) {
                    errorMessage += "POST exception";
                } else if (e instanceof IOException) {
                    errorMessage += "IO Exception in parsing response";
                } else {
                    errorMessage += "JSON parsing exception";
                }

                Log.e("history", errorMessage);
            }

            return history_results;
        }

        private void refreshAnalytics(URLMetrics metric) {
            String shortenedURL = metric.getShortURL();
            String tmpScope = METRICSURL + "?shortUrl=" + shortenedURL + "&projection=FULL";
//            METRICSURL += "?shortUrl=" + shortenedURL;
//            METRICSURL += "&projection=FULL";

            Log.d(LOGTAG, "Fetching analytics");
            JSONObject results = null;
            try {
                HttpParams httpParams = new BasicHttpParams();
                HttpConnectionParams.setConnectionTimeout(httpParams, 5000);
                HttpConnectionParams.setSoTimeout(httpParams, 5000);

                DefaultHttpClient client = new DefaultHttpClient(httpParams);
                HttpGet get = new HttpGet(tmpScope);

                HttpResponse response = client.execute(get);

                BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
                StringBuilder builder = new StringBuilder();
                for (String line; (line = reader.readLine()) != null;) {
                    builder.append(line).append("\n");
                }

                results = new JSONObject(new JSONTokener(builder.toString()));
//                Log.d("hologoogl", results.toString());
            } catch (Exception e) {
                e.printStackTrace();
                String errorMessage = "Error: ";
                if (e instanceof UnsupportedEncodingException) {
                    errorMessage += "Encoding exception";
                } else if (e instanceof ClientProtocolException) {
                    errorMessage += "POST exception";
                } else if (e instanceof IOException) {
                    errorMessage += "IO Exception in parsing response";
                } else {
                    errorMessage += "JSON parsing exception";
                }

                Log.e("googl:retrieveURLTask", errorMessage);
            }

            if (results != null) {
                metric.setAnalytics(results);
            }
        }
    }
}