/*
 * Licensed to Cloudkick, Inc ('Cloudkick') under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * Cloudkick licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloudkick;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.http.auth.InvalidCredentialsException;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import com.cloudkick.exceptions.EmptyCredentialsException;

public class DashboardActivity extends Activity implements OnItemClickListener {
	private static final String TAG = "DashboardActivity";
	private static final int SETTINGS_ACTIVITY_ID = 0;
	private static final int LOGIN_ACTIVITY_ID = 1;
	private static final int refreshRate = 30;
	private CloudkickAPI api;
	private ProgressDialog progress;
	private ListView dashboard;
	private NodesAdapter adapter;
	private boolean haveNodes = false;
	private boolean isRunning = false;
	private final ArrayList<Node> nodes = new ArrayList<Node>();
	private final Handler reloadHandler = new Handler();

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		dashboard = new ListView(this);
		adapter = new NodesAdapter(this, R.layout.node_item, nodes);
		dashboard.setAdapter(adapter);
		dashboard.setOnItemClickListener(this);
		dashboard.setBackgroundColor(Color.WHITE);
		setContentView(dashboard);
		reloadAPI();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.dashboard_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.refresh_dashboard:
				refreshNodes();
				return true;
			case R.id.log_out:
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
				SharedPreferences.Editor editor = prefs.edit();
				editor.putString("editKey", "");
				editor.putString("editSecret", "");
				editor.commit();
				nodes.clear();
				adapter.notifyDataSetChanged();
				Intent loginActivity = new Intent(getBaseContext(), LoginActivity.class);
				startActivityForResult(loginActivity, LOGIN_ACTIVITY_ID);
				return true;
			case R.id.settings:
				Intent settingsActivity = new Intent(getBaseContext(), Preferences.class);
				startActivityForResult(settingsActivity, SETTINGS_ACTIVITY_ID);
				return true;
			default:
				// If its not recognized, do nothing
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		setContentView(dashboard);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == SETTINGS_ACTIVITY_ID) {
			reloadAPI();
		}
		if (requestCode == LOGIN_ACTIVITY_ID) {
			// TODO: There is definitely a better way to do this
			try {
				if (data.getBooleanExtra("login", false)) {
					reloadAPI();
				}
				else {
					finish();
				}
			}
			catch (NullPointerException e) {
				finish();
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		isRunning = true;
		reloadService.run();
		Log.i(TAG, "Reloading service started");
	}

	@Override
	protected void onPause() {
		super.onPause();
		isRunning = false;
		if (progress != null) {
			progress.dismiss();
			progress = null;
		}
		reloadHandler.removeCallbacks(reloadService);
		Log.i(TAG, "Reloading callbacks canceled");
	}

	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		Bundle data = new Bundle();
		data.putSerializable("node", nodes.get(position));
		Intent intent = new Intent(DashboardActivity.this, NodeViewActivity.class);
		intent.putExtras(data);
		startActivity(intent);
	}

	private void refreshNodes() {
		if (api != null) {
			if (!haveNodes) {
				progress = ProgressDialog.show(this, "", "Loading Nodes...", true);
			}
			new NodeUpdater().execute();
		}
	}

	private void reloadAPI() {
		try {
			api = new CloudkickAPI(this);
			haveNodes = false;
		}
		catch (EmptyCredentialsException e) {
			Log.i(TAG, "Empty Credentials, forcing login");
			Intent loginActivity = new Intent(getBaseContext(), LoginActivity.class);
			startActivityForResult(loginActivity, LOGIN_ACTIVITY_ID);
		}
	}

	private class NodeUpdater extends AsyncTask<Void, Void, ArrayList<Node>> {
		private Exception e = null;

		@Override
		protected ArrayList<Node> doInBackground(Void...voids) {
			try {
				return api.getNodes();
			}
			catch (Exception e) {
				this.e = e;
				return null;
			}
		}

		@Override
		protected void onPostExecute(ArrayList<Node> retrieved_nodes) {
			// Get rid of the progress dialog either way
			if (progress != null) {
				progress.dismiss();
				progress = null;
			}
			// Handle errors
			if (e != null) {
				if (e instanceof InvalidCredentialsException) {
					Toast.makeText(DashboardActivity.this.getApplicationContext(), "Invalid Credentials", Toast.LENGTH_SHORT).show();
					Intent settingsActivity = new Intent(getBaseContext(), Preferences.class);
					startActivityForResult(settingsActivity, SETTINGS_ACTIVITY_ID);
				}
				else if (e instanceof IOException) {
					Toast.makeText(DashboardActivity.this.getApplicationContext(), "A Network Error Occurred", Toast.LENGTH_SHORT).show();
				}
				else {
					Toast.makeText(DashboardActivity.this.getApplicationContext(), "Unknown Refresh Error", Toast.LENGTH_SHORT).show();
					Log.e(TAG, "Unknown Refresh Error", e);
				}
			}
			// Handle success
			else if (isRunning) {
				nodes.clear();
				nodes.addAll(retrieved_nodes);
				haveNodes = true;
				adapter.notifyDataSetChanged();
				// Schedule the next run
				reloadHandler.postDelayed(reloadService, refreshRate * 1000);
				Log.i(TAG, "Next reload in " + refreshRate + " seconds");
			}
		}
	}

	private final Runnable reloadService = new Runnable() {
		public void run() {
			// This happens asynchronously and schedules the next run
			refreshNodes();
		}
	};
}
