/**
 *    Copyright 2009 Bram de Kruijff <bdekruijff [at] gmail [dot] com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package net.atoom.android.tt2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import net.atoom.android.tt2.util.BoundStack;
import net.atoom.android.tt2.util.LogBridge;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ads.AdRequest;
import com.google.ads.AdView;

public final class TTActivity extends Activity {

	public static final String LOGGING_TAG = "AtoomTT";

	private static final String ATOOMTT_PACKAGE = "net.atoom.android.tt2";
	private static final String ATOOMTTDONATE_PACKAGE = "net.atoom.android.tt3";

	private static final String CONTENT_STARTPAGEID = "101-0";

	private static final String PREFS_HOMEPAGE_ID = "homepageId";
	private static final String PREFS_INSTALLED_VERSION = "installedVersion";

	private static final String TEMPLATE_FILENAME = "template.html";
	private static final String TEMPLATE_PLACEHOLDER = "[pageContent]";

	private static final String DEFAULT_VERSION = "0.0.0";

	private static final int DIALOG_ABOUT_ID = 0;

	private static final int MENU_ABOUT = 1;
	private static final int MENU_SETHOME = 2;
	private static final int MENU_CLOSE = 3;
	private static final int MENU_REFRESH = 4;

	private static final int HISTORY_SIZE = 50;
	private static final long RELOAD_INTERVAL_MS = 60000;
	private static final long AD_INIT_DELAY_MS = 3000;

	private final PageLoader myPageLoader = new PageLoader();
	private final Handler myHandler = new Handler();
	private final BoundStack<PageEntity> myHistoryStack = new BoundStack<PageEntity>(
			HISTORY_SIZE);
	private String myHomePageId = CONTENT_STARTPAGEID;
	private String myInstalledVersion = DEFAULT_VERSION;
	private String myCurrentVersion = DEFAULT_VERSION;
	private PageEntity myCurrentPageEntity;
	private String myTemplate;
	private int myPageLoadCount = 0;
	private volatile boolean isStopped = false;

	private MainWebViewAnimator myMainWebViewAnimator = null;
	private EditText myPageEditText;
	private Button myHomeButton = null;
	private Button myNextPageButton = null;
	private Button myNextSubPageButton = null;
	private Button myPrevPageButton = null;
	private Button myPrevSubPageButton = null;

	private LocationListener myLocationListener = null;
	private boolean myAdsEnabled = true;
	private volatile Location myLocation = null;

	public TTActivity() {
		myLocation = new Location(LOGGING_TAG);
		myLocation.setLatitude(52.4498d);
		myLocation.setLongitude(4.8223d);
		myLocation.setTime(System.currentTimeMillis());
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		loadPreferences();
		loadTemplate();

		initGraphics();
		initEditText();
		initButtons();
		initWebView();

		loadCurrentVersion();
		// initLocationTracking();
		initAdvertising();
	}

	@Override
	protected void onStart() {
		super.onStart();
		isStopped = false;
		if (handleShowWelcome()) {
		} else {
			loadPageUrl(myHomePageId, true);
		}
	}

	@Override
	protected void onStop() {
		isStopped = true;
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		destroyLocationTracking();
		destroyAdView();
		super.onDestroy();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (isStopped)
			return false;
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			return handleBackButton();
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (isStopped)
			return false;
		menu.add(0, MENU_SETHOME, 0, R.string.menu_homepage);
		menu.add(0, MENU_REFRESH, 1, R.string.menu_refresh);
		menu.add(0, MENU_CLOSE, 2, R.string.menu_close);
		menu.add(0, MENU_ABOUT, 3, R.string.menu_about);
		menu.getItem(0).setIcon(R.drawable.ic_menu_star);
		menu.getItem(1).setIcon(R.drawable.ic_menu_refresh);
		menu.getItem(2).setIcon(R.drawable.ic_menu_close);
		menu.getItem(3).setIcon(R.drawable.ic_menu_info);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (isStopped)
			return false;
		switch (item.getItemId()) {
		case MENU_ABOUT:
			showDialog(DIALOG_ABOUT_ID);
			return true;
		case MENU_SETHOME:
			return handleSetHomePage();
		case MENU_REFRESH:
			reloadPageUrl(myPageLoadCount);
			return true;
		case MENU_CLOSE:
			finish();
		}
		return false;
	}

	public synchronized void loadPageUrl(final String pageId,
			final boolean updateHistory) {
		if (isStopped) {
			return;
		}

		myPageLoadCount++; // cancels previous reloads
		myPageLoader.loadPage(pageId, PageLoadPriority.HIGH,
				new PageLoadCompletionHandler() {

					@Override
					public void pageLoadCompleted(final PageEntity pageEntity) {

						if (pageEntity == null) {
							myHandler.post(new Runnable() {
								@Override
								public void run() {
									Toast.makeText(getApplicationContext(),
											R.string.toast_pagenotfound,
											Toast.LENGTH_SHORT).show();
								}
							});
							return;
						}

						myHandler.post(new Runnable() {
							@Override
							public void run() {
								PageEntity previousPageEntity = myCurrentPageEntity;
								myCurrentPageEntity = pageEntity;

								if (previousPageEntity != null
										&& updateHistory
										&& !previousPageEntity.getPageId()
												.equals(pageId)) {
									myHistoryStack.push(previousPageEntity);
								}
								updateEditText(pageEntity);
								updateButtons(pageEntity);
								updateWebView(pageEntity);
								myHandler.postDelayed(new ReloadRunnable(
										TTActivity.this, myPageLoadCount),
										RELOAD_INTERVAL_MS);
							}
						});
					}
				});

	}

	public synchronized void reloadPageUrl(final int pageLoadCount) {
		if (isStopped)
			return;
		PageEntity pageEntity = myCurrentPageEntity;
		if (myPageLoadCount == pageLoadCount && !isStopped
				&& pageEntity != null) {
			if (LogBridge.isLoggable())
				LogBridge.i("Reloading...");
			Toast.makeText(getApplicationContext(), R.string.toast_pagereload,
					Toast.LENGTH_SHORT).show();
			loadPageUrl(pageEntity.getPageId(), false);
		} else {
			if (LogBridge.isLoggable())
				LogBridge.i("Aborting reload");
		}
	}

	public synchronized void loadNextPage() {
		if (isStopped)
			return;
		PageEntity pageEntity = myCurrentPageEntity;
		if (pageEntity != null) {
			if (pageEntity.getNextSubPageId() != null) {
				loadPageUrl(pageEntity.getNextSubPageId(), true);
			} else {
				if (pageEntity.getNextPageId() != null) {
					loadPageUrl(pageEntity.getNextPageId(), true);
				}
			}
		}
	}

	public synchronized void loadPrevPage() {
		if (isStopped)
			return;
		PageEntity pageEntity = myCurrentPageEntity;
		if (pageEntity != null) {
			if (pageEntity.getPrevSubPageId() != null) {
				loadPageUrl(pageEntity.getPrevSubPageId(), true);
			} else {
				if (pageEntity.getPrevPageId() != null) {
					loadPageUrl(pageEntity.getPrevPageId(), true);
				}
			}
		}
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		TextView view = new TextView(this);
		view.setPadding(10, 10, 10, 10);
		builder.setTitle(getResources().getText(R.string.dialog_title) + " v"
				+ myCurrentVersion);
		builder.setView(view);
		switch (id) {
		case DIALOG_ABOUT_ID:
			if (myAdsEnabled) {
				view.setText(getResources().getText(R.string.dialog_about_text));
				builder.setNegativeButton(
						getResources().getText(R.string.dialog_about_donate),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								Intent intent = new Intent(Intent.ACTION_VIEW);
								intent.setData(Uri.parse("market://details?id="
										+ ATOOMTTDONATE_PACKAGE));
								startActivity(intent);
								finish();
							}
						});
			} else {
				view.setText(getResources().getText(
						R.string.dialog_about_text_noads));
			}
			builder.setPositiveButton(
					getResources().getText(R.string.dialog_about_ok), null);
			break;
		}
		return builder.create();
	}

	private boolean handleBackButton() {
		if (isStopped)
			return true;
		if (myHistoryStack.size() > 0) {
			loadPageUrl(myHistoryStack.pop().getPageId(), false);
		} else {
			finish();
		}
		return true;
	}

	private boolean handleSetHomePage() {
		if (isStopped)
			return true;
		myHomePageId = myCurrentPageEntity.getPageId();
		storePreferences();
		Toast.makeText(getApplicationContext(), R.string.toast_homepageset,
				Toast.LENGTH_SHORT).show();
		return true;
	}

	private boolean handleShowWelcome() {
		if (!myInstalledVersion.equals(myCurrentVersion)) {
			myInstalledVersion = myCurrentVersion;
			storePreferences();
			showDialog(DIALOG_ABOUT_ID);
			return true;
		}
		return false;
	}

	private void initLocationTracking() {
		if (LogBridge.isLoggable())
			LogBridge.i("Initializing locationlistener");
		final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		if (manager == null)
			return;
		myLocationListener = new LocationListener() {
			public void onLocationChanged(final Location location) {
				if (LogBridge.isLoggable())
					LogBridge.i("Location update recieved: " + location);
				myLocation = location;
			}

			public void onStatusChanged(String provider, int status,
					Bundle extras) {
			}

			public void onProviderEnabled(String provider) {
			}

			public void onProviderDisabled(String provider) {
			}
		};
		manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
				300000, 100f, myLocationListener);
	}

	private void destroyLocationTracking() {
		if (LogBridge.isLoggable())
			LogBridge.i("Destroying locationlistener");
		LocationListener locationListener = myLocationListener;
		myLocationListener = null;
		if (locationListener == null)
			return;
		LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		if (manager == null)
			return;
		manager.removeUpdates(locationListener);
	}

	private void initGraphics() {
		if (LogBridge.isLoggable())
			LogBridge.i("Initializing graphics");
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().setTitle(getResources().getText(R.string.main_title));
		getWindow().setSoftInputMode(1);
		setContentView(R.layout.main);
	}

	private void initWebView() {
		if (LogBridge.isLoggable())
			LogBridge.i("Initializing webview");
		myMainWebViewAnimator = new MainWebViewAnimator(this);
		FrameLayout frameLayout = (FrameLayout) findViewById(R.id.webview);
		frameLayout.addView(myMainWebViewAnimator);
	}

	private void updateWebView(PageEntity pageEntity) {
		String htmlData = myTemplate.replace(TEMPLATE_PLACEHOLDER,
				pageEntity.getHtmlData());
		myMainWebViewAnimator.updateWebView(htmlData);
	}

	private void initAdvertising() {
		if (LogBridge.isLoggable())
			LogBridge.i("Initialzing advertising");

		try {
			PackageInfo info = getPackageManager().getPackageInfo(
					ATOOMTTDONATE_PACKAGE, PackageManager.GET_ACTIVITIES);
			if (getPackageManager().checkSignatures(ATOOMTT_PACKAGE,
					ATOOMTTDONATE_PACKAGE) == PackageManager.SIGNATURE_MATCH) {
				myAdsEnabled = false;
			}
		} catch (NameNotFoundException e) {
		}

		if (!myAdsEnabled) {
			hideAdView();
		} else {
			showAdView();
			myHandler.postDelayed(new Runnable() {
				public void run() {
					initAdView();
				}
			}, AD_INIT_DELAY_MS);
		}
	}

	private void initAdView() {
		if (LogBridge.isLoggable())
			LogBridge.i("Initializing adview");
		AdView adView = (AdView) findViewById(R.id.ad);
		AdRequest adRequest = new AdRequest();
		adRequest.setLocation(myLocation);
		adView.loadAd(adRequest);
	}

	private void destroyAdView() {
		if (LogBridge.isLoggable())
			LogBridge.i("Destroying adview");
		AdView adView = (AdView) findViewById(R.id.ad);
		adView.destroy();
	}

	private void showAdView() {
		if (LogBridge.isLoggable())
			LogBridge.i("Showing AdView : " + myLocation);
		AdView adView = (AdView) findViewById(R.id.ad);
		adView.setVisibility(View.VISIBLE);
	}

	private void hideAdView() {
		if (LogBridge.isLoggable())
			LogBridge.i("Hiding AdView : " + myLocation);
		AdView adView = (AdView) findViewById(R.id.ad);
		adView.setVisibility(View.GONE);
	}

	private void loadPreferences() {
		if (LogBridge.isLoggable())
			LogBridge.i("Loading preferences");
		SharedPreferences settings = getSharedPreferences(LOGGING_TAG,
				MODE_PRIVATE);
		if (settings != null) {
			myHomePageId = settings.getString(PREFS_HOMEPAGE_ID,
					CONTENT_STARTPAGEID);
			myInstalledVersion = settings.getString(PREFS_INSTALLED_VERSION,
					myInstalledVersion);
			if (LogBridge.isLoggable()) {
				LogBridge.i(" " + PREFS_HOMEPAGE_ID + "=" + myHomePageId);
				LogBridge.i(" " + PREFS_INSTALLED_VERSION + "="
						+ myInstalledVersion);
			}
		}
	}

	private void storePreferences() {
		if (LogBridge.isLoggable())
			LogBridge.i("Storing preferences");
		SharedPreferences settings = getSharedPreferences(LOGGING_TAG,
				MODE_PRIVATE);
		if (settings != null) {
			SharedPreferences.Editor editor = settings.edit();
			if (editor != null) {
				editor.putString(PREFS_HOMEPAGE_ID, myHomePageId);
				editor.putString(PREFS_INSTALLED_VERSION, myInstalledVersion);
				editor.commit();
			}
			if (LogBridge.isLoggable()) {
				LogBridge.i(" " + PREFS_HOMEPAGE_ID + "=" + myHomePageId);
				LogBridge.i(" " + PREFS_INSTALLED_VERSION + "="
						+ myInstalledVersion);
			}
		}
	}

	private void resetPreferences() {
		if (LogBridge.isLoggable())
			LogBridge.i("Resetting preferences");
		myHomePageId = CONTENT_STARTPAGEID;
		myInstalledVersion = DEFAULT_VERSION;
		storePreferences();
		Toast.makeText(this, "Preferences reset", Toast.LENGTH_SHORT).show();
	}

	private void loadTemplate() {
		if (myTemplate == null || myTemplate.equals("")) {
			InputStream is = null;
			try {
				is = getAssets().open(TEMPLATE_FILENAME);
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				StringBuilder sb = new StringBuilder(500);
				String line = null;
				while ((line = br.readLine()) != null) {
					sb.append(line);
					sb.append("\n");
				}
				myTemplate = sb.toString();
			} catch (IOException e) {
				if (is != null) {
					try {
						is.close();
					} catch (IOException e1) {
					}
				}
			}
		}
	}

	private void loadCurrentVersion() {
		if (LogBridge.isLoggable())
			LogBridge.i("Initializing currentVersion");
		try {
			PackageInfo packageInfo = getPackageManager().getPackageInfo(
					ATOOMTT_PACKAGE, PackageManager.GET_ACTIVITIES);
			myCurrentVersion = packageInfo.versionName;
			if (LogBridge.isLoggable())
				LogBridge.i("Installed version is " + myCurrentVersion);
		} catch (NameNotFoundException e) {
			if (LogBridge.isLoggable())
				LogBridge.w("Failed to determine current version");
		}
	}

	private void initEditText() {
		if (LogBridge.isLoggable())
			LogBridge.i("Initializing edittext");
		myPageEditText = (EditText) findViewById(R.id.gotopageview);
		myPageEditText.setSelectAllOnFocus(true);
		myPageEditText.addTextChangedListener(new TextWatcher() {

			public void afterTextChanged(Editable s) {
				if (myPageEditText.getText().length() != 3)
					return;

				String newPageId = myPageEditText.getText() + "";

				String currentPageId = "";
				PageEntity pageEntity = myCurrentPageEntity;
				if (pageEntity != null && pageEntity.getPageId() != null) {
					currentPageId = pageEntity.getPageId();
					if (currentPageId.length() == 6) {
						currentPageId = currentPageId.substring(0, 3);
					}
				}

				if (newPageId.equals("000")) {
					resetPreferences();
				} else {
					if (currentPageId.startsWith(newPageId)) {
						if (LogBridge.isLoggable())
							LogBridge.i("Ignoring newPageId " + newPageId
									+ " to prevent recursion");
					} else {
						loadPageUrl(newPageId, true);
					}
					myPageEditText.clearFocus();
				}

				// close soft keyboard
				InputMethodManager inputManager = (InputMethodManager) TTActivity.this
						.getSystemService(Context.INPUT_METHOD_SERVICE);
				inputManager.hideSoftInputFromWindow(
						myPageEditText.getWindowToken(),
						InputMethodManager.HIDE_NOT_ALWAYS);
			}

			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
			}

		});
	}

	private void updateEditText(PageEntity pageEntity) {
		myPageEditText.setText(pageEntity.getPageId());
	}

	private void initButtons() {
		if (LogBridge.isLoggable())
			LogBridge.i("Initializing buttons");
		myHomeButton = (Button) findViewById(R.id.homebuttonview);
		myHomeButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				loadPageUrl(myHomePageId, true);
			}
		});
		myNextPageButton = (Button) findViewById(R.id.nextpagebuttonview);
		myNextPageButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				PageEntity pageEntity = myCurrentPageEntity;
				if (pageEntity != null
						&& !pageEntity.getNextPageId().equals(""))
					loadPageUrl(pageEntity.getNextPageId(), true);
			}
		});
		myNextSubPageButton = (Button) findViewById(R.id.nextsubbuttonview);
		myNextSubPageButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				PageEntity pageEntity = myCurrentPageEntity;
				if (pageEntity != null
						&& !pageEntity.getNextSubPageId().equals(""))
					loadPageUrl(pageEntity.getNextSubPageId(), true);
			}
		});
		myPrevPageButton = (Button) findViewById(R.id.prevpagebuttonview);
		myPrevPageButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				PageEntity pageEntity = myCurrentPageEntity;
				if (pageEntity != null
						&& !pageEntity.getPrevPageId().equals(""))
					loadPageUrl(pageEntity.getPrevPageId(), true);
			}
		});
		myPrevSubPageButton = (Button) findViewById(R.id.prevsubbuttonview);
		myPrevSubPageButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				PageEntity pageEntity = myCurrentPageEntity;
				if (pageEntity != null
						&& !pageEntity.getPrevSubPageId().equals(""))
					loadPageUrl(pageEntity.getPrevSubPageId(), true);
			}
		});
	}

	private void updateButtons(PageEntity pageEntity) {
		if (isEmpty(pageEntity.getNextPageId()))
			disableButton(myNextPageButton);
		else
			enableButton(myNextPageButton);
		if (isEmpty(pageEntity.getNextSubPageId()))
			disableButton(myNextSubPageButton);
		else
			enableButton(myNextSubPageButton);
		if (isEmpty(pageEntity.getPrevPageId()))
			disableButton(myPrevPageButton);
		else
			enableButton(myPrevPageButton);
		if (isEmpty(pageEntity.getPrevSubPageId()))
			disableButton(myPrevSubPageButton);
		else
			enableButton(myPrevSubPageButton);
	}

	private void enableButton(final Button button) {
		button.setEnabled(true);
		button.setFocusable(true);
	}

	private void disableButton(final Button button) {
		button.setEnabled(false);
		button.setFocusable(false);
	}

	private boolean isEmpty(final String string) {
		return string == null || string.equals("");
	}
}
