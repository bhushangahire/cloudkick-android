package com.cloudkick;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

public class LoginActivity extends Activity {
	RelativeLayout loginView = null;
	private String user = null;
	private String pass = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.login);
		setTitle("Cloudkick for Android");

		findViewById(R.id.button_login).setOnClickListener(new LoginClickListener());
		findViewById(R.id.button_signup).setOnClickListener(new SignupClickListener());
	}

	private class LoginClickListener implements View.OnClickListener {
		public void onClick(View v) {
			new AccountLister().execute();
		}
	}

	private class SignupClickListener implements View.OnClickListener {
		public void onClick(View v) {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://cloudkick.com/pricing/")));
		}
	}
	private class AccountLister extends AsyncTask<Void, Void, ArrayList<String>>{
		private ProgressDialog progress = null;
		private Integer statusCode = null;

		@Override
		protected void onPreExecute() {
			user = ((EditText) findViewById(R.id.input_email)).getText().toString();
			pass = ((EditText) findViewById(R.id.input_password)).getText().toString();
			progress = ProgressDialog.show(LoginActivity.this, "", "Logging In...", true);
		}

		@Override
		protected ArrayList<String> doInBackground(Void...voids) {
			ArrayList<String> accounts = new ArrayList<String>();
			try {
				HttpClient client = new DefaultHttpClient();
				HttpPost post = new HttpPost("https://cloudkick.com/oauth/list_accounts/");
				ArrayList<NameValuePair> values = new ArrayList<NameValuePair>(2);
				values.add(new BasicNameValuePair("user", user));
				values.add(new BasicNameValuePair("password", pass));
				post.setEntity(new UrlEncodedFormEntity(values));
				HttpResponse response = client.execute(post);
				statusCode = response.getStatusLine().getStatusCode();
			    InputStream is = response.getEntity().getContent();
				BufferedReader rd = new BufferedReader(new InputStreamReader(is));
				String line;
				while ((line = rd.readLine()) != null) {
				    accounts.add(line);
				    Log.i("LoginActivity", line);
				}
			}
			catch (Exception e) {
				statusCode = 0;
			}
			return accounts;
		}

		@Override
		protected void onPostExecute(ArrayList<String> accounts) {
			progress.dismiss();
			switch (statusCode) {
				case 200:
					if (accounts.size() == 1) {
						new KeyRetriever().execute(accounts.get(0));
					}
					else {
						String[] tmpAccountArray = new String[accounts.size()];
						final String[] accountArray = accounts.toArray(tmpAccountArray);
						AlertDialog.Builder builder = new AlertDialog.Builder(LoginActivity.this);
						builder.setTitle("Select an Account");
						builder.setItems(accountArray, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int item) {
								new KeyRetriever().execute(accountArray[item]);
							}
						});
						AlertDialog selectAccount = builder.create();
						selectAccount.show();
					}
					break;
				case 400:
					Toast.makeText(LoginActivity.this, "Invalid Username or Password", Toast.LENGTH_LONG).show();
					break;
				default:
					Toast.makeText(LoginActivity.this, "An Error Occurred", Toast.LENGTH_LONG).show();
			};
		}
	}

	private class KeyRetriever extends AsyncTask<String, Void, String[]>{
		private ProgressDialog progress = null;
		private Integer statusCode = null;

		@Override
		protected void onPreExecute() {
			progress = ProgressDialog.show(LoginActivity.this, "", "Retrieving Key...", true);
		}

		@Override
		protected String[] doInBackground(String...accts) {
			Log.i("LoginActivity", "Selected Account: " + accts[0]);
			String[] creds = new String[2];
			try {
				HttpClient client = new DefaultHttpClient();
				HttpPost post = new HttpPost("https://cloudkick.com/oauth/create_consumer/");
				ArrayList<NameValuePair> values = new ArrayList<NameValuePair>(2);
				values.add(new BasicNameValuePair("user", user));
				values.add(new BasicNameValuePair("password", pass));
				values.add(new BasicNameValuePair("account", accts[0]));
				values.add(new BasicNameValuePair("system", "Cloudkick for Android"));
				values.add(new BasicNameValuePair("perm_read", "True"));
				values.add(new BasicNameValuePair("perm_write", "False"));
				values.add(new BasicNameValuePair("perm_execute", "False"));
				post.setEntity(new UrlEncodedFormEntity(values));
				HttpResponse response = client.execute(post);
				statusCode = response.getStatusLine().getStatusCode();
				Log.i("LoginActivity", "Return Code: " + statusCode);
				if (statusCode != 200) return null;
			    InputStream is = response.getEntity().getContent();
				BufferedReader rd = new BufferedReader(new InputStreamReader(is));
				String line;
				for (int i = 0; i < 2; i++) {
					line = rd.readLine();
					if (line == null) {
						statusCode = 0;
						return null;
					}
					creds[i] = line;
				}
			}
			catch (Exception e) {
				statusCode = 0;
			}
			return creds;
		}

		@Override
		protected void onPostExecute(String[] creds) {
			progress.dismiss();
			if (statusCode != 200) {
				Toast.makeText(LoginActivity.this, "An Error Occurred", Toast.LENGTH_LONG).show();
				return;
			}
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(LoginActivity.this);
			SharedPreferences.Editor editor = prefs.edit();
			editor.putString("editKey", creds[0]);
			editor.putString("editSecret", creds[1]);
			editor.commit();
			LoginActivity.this.finish();
		}
	}
}