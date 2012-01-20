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

import net.atoom.android.tt2.BillingService.RequestPurchase;
import net.atoom.android.tt2.BillingService.RestoreTransactions;
import net.atoom.android.tt2.Consts.PurchaseState;
import net.atoom.android.tt2.Consts.ResponseCode;
import net.atoom.android.tt2.util.BoundStack;
import net.atoom.android.tt2.util.LogBridge;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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

	private static final String CONTENT_STARTPAGEURL = "http://teletekst.nos.nl/tekst/101-01.html";

	private static final String PREFS_HOMEPAGE_URL = "homepageUrl";
	private static final String PREFS_TX_RESTORED = "txRestored";
	private static final String PREFS_ADS_ENABLED = "adsEnabled";

	private static final String TEMPLATE_FILENAME = "template.html";
	private static final String TEMPLATE_PLACEHOLDER = "[pageContent]";

	private static final int DIALOG_PURCHASE_ID = 0;
	private static final int DIALOG_PURCHASE_SUCCES_ID = 1;
	private static final int DIALOG_PURCHASE_CANCEL_ID = 2;
	private static final int DIALOG_PURCHASE_REFUND_ID = 3;
	private static final int DIALOG_PURCHASE_FAIL_ID = 4;
	private static final int DIALOG_ABOUT_ID = 5;

	private static final int MENU_ABOUT = 1;
	private static final int MENU_SETHOME = 2;
	private static final int MENU_CLOSE = 3;
	private static final int MENU_REFRESH = 4;

	private static final int HISTORY_SIZE = 50;
	private static final long RELOAD_INTERVAL_MS = 60000;
	private static final long AD_INIT_DELAY_MS = 3000;

	// app
	private final PageLoader myPageLoader = new PageLoader();
	private final Handler myHandler = new Handler();
	private final BoundStack<PageEntity> myHistoryStack = new BoundStack<PageEntity>(HISTORY_SIZE);
	private String myHomePageUrl = CONTENT_STARTPAGEURL;
	private PageEntity myCurrentPageEntity;
	private String myTemplate;
	private int myPageLoadCount = 0;
	private volatile boolean isStopped = false;

	// ui
	private MainWebViewAnimator myMainWebViewAnimator = null;
	private EditText myPageEditText;
	private Button myHomeButton = null;
	private Button myNextPageButton = null;
	private Button myNextSubPageButton = null;
	private Button myPrevPageButton = null;
	private Button myPrevSubPageButton = null;

	// location
	private LocationListener myLocationListener = null;
	private boolean myAdsEnabled = true;
	private volatile Location myLocation = null;

	// vending
	private BillingService myBillingService = null;
	private TTPurchaseObserver myPurchaseObserver = null;
	private boolean myBillingSupported = true;
	private boolean myTxRestored = false;

	public TTActivity() {
		myLocation = new Location(LOGGING_TAG);
		myLocation.setLatitude(52.4498d);
		myLocation.setLongitude(4.8223d);
		myLocation.setTime(System.currentTimeMillis());
	}

	// lifecycle
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		loadPreferences();
		loadTemplate();

		initGraphics();
		initEditText();
		initButtons();
		initWebView();

		initVending();
		initLocationTracking();
		initAdvertising();
	}

	@Override
	protected void onStart() {
		super.onStart();
		isStopped = false;
		ResponseHandler.register(myPurchaseObserver);
		loadPageUrl(myHomePageUrl, true);
	}

	@Override
	protected void onStop() {
		isStopped = true;
		ResponseHandler.unregister(myPurchaseObserver);
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		myBillingService.unbind();
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
		menu.add(0, MENU_ABOUT, 1, R.string.menu_about);
		menu.add(0, MENU_CLOSE, 2, R.string.menu_close);
		menu.add(0, MENU_REFRESH, 3, R.string.menu_refresh);
		menu.getItem(0).setIcon(R.drawable.ic_menu_star);
		menu.getItem(1).setIcon(R.drawable.ic_menu_help);
		menu.getItem(2).setIcon(R.drawable.ic_menu_close);
		menu.getItem(3).setIcon(R.drawable.ic_menu_refresh);
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
			showDialog(DIALOG_PURCHASE_ID);
			reloadPageUrl(myPageLoadCount);
			return true;
		case MENU_CLOSE:
			finish();
		}
		return false;
	}

	// api
	public synchronized void loadPageUrl(final String pageUrl, final boolean updateHistory) {
		if (isStopped)
			return;

		myPageLoadCount++; // cancels previous reloads

		myPageLoader.loadPage(pageUrl, PageLoadPriority.HIGH, new PageLoadCompletionHandler() {

			@Override
			public void pageLoadCompleted(final PageEntity pageEntity) {

				if (pageEntity == null) {
					myHandler.post(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(getApplicationContext(), R.string.toast_pagenotfound, Toast.LENGTH_SHORT)
									.show();
						}
					});
					return;
				}

				myHandler.post(new Runnable() {
					@Override
					public void run() {
						PageEntity previousPageEntity = myCurrentPageEntity;
						myCurrentPageEntity = pageEntity;

						if (previousPageEntity != null && updateHistory
								&& !previousPageEntity.getPageUrl().equals(pageUrl)) {
							myHistoryStack.push(previousPageEntity);
						}
						updateEditText(pageEntity);
						updateButtons(pageEntity);
						updateWebView(pageEntity);

						myPageLoader.loadPage(pageEntity.getNextPageUrl(), PageLoadPriority.LOW, null);
						myPageLoader.loadPage(pageEntity.getPrevPageUrl(), PageLoadPriority.LOW, null);
						myPageLoader.loadPage(pageEntity.getNextSubPageUrl(), PageLoadPriority.LOW, null);
						myPageLoader.loadPage(pageEntity.getPrevSubPageUrl(), PageLoadPriority.LOW, null);

						myHandler.postDelayed(new ReloadRunnable(TTActivity.this, myPageLoadCount), RELOAD_INTERVAL_MS);
					}
				});
			}
		});

	}

	public synchronized void reloadPageUrl(final int pageLoadCount) {
		if (isStopped)
			return;
		PageEntity pageEntity = myCurrentPageEntity;
		if (myPageLoadCount == pageLoadCount && !isStopped && pageEntity != null) {
			if (LogBridge.isLoggable())
				LogBridge.i("Reloading...");
			Toast.makeText(getApplicationContext(), R.string.toast_pagereload, Toast.LENGTH_SHORT).show();
			loadPageUrl(pageEntity.getPageUrl(), false);
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
			if (pageEntity.getNextSubPageUrl() != null) {
				loadPageUrl(pageEntity.getNextSubPageUrl(), true);
			} else {
				if (pageEntity.getNextPageUrl() != null) {
					loadPageUrl(pageEntity.getNextPageUrl(), true);
				}
			}
		}
	}

	public synchronized void loadPrevPage() {
		if (isStopped)
			return;
		PageEntity pageEntity = myCurrentPageEntity;
		if (pageEntity != null) {
			if (pageEntity.getPrevSubPageUrl() != null) {
				loadPageUrl(pageEntity.getPrevSubPageUrl(), true);
			} else {
				if (pageEntity.getPrevPageUrl() != null) {
					loadPageUrl(pageEntity.getPrevPageUrl(), true);
				}
			}
		}
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		TextView view = new TextView(this);
		view.setPadding(10, 10, 10, 10);
		builder.setView(view);
		switch (id) {
		case DIALOG_PURCHASE_ID:
			builder.setTitle(getResources().getText(R.string.dialog_about_message));
			view.setText("Steun mij...");
			builder.setPositiveButton(getResources().getText(R.string.dialog_about_ok),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int whichButton) {
							myBillingService.requestPurchase("android.test.purchased", null);
						}
					});
			builder.setNegativeButton("nee", null);
			break;
		case DIALOG_PURCHASE_SUCCES_ID:
			builder.setTitle(getResources().getText(R.string.dialog_about_message));
			view.setText("Bedankt! Banners zijn weg");
			builder.setView(view);
			builder.setPositiveButton(getResources().getText(R.string.dialog_about_ok), null);
			break;
		case DIALOG_PURCHASE_CANCEL_ID:
			builder.setTitle(getResources().getText(R.string.dialog_about_message));
			view.setText("Gestopt....");
			builder.setPositiveButton(getResources().getText(R.string.dialog_about_ok), null);
			break;
		case DIALOG_PURCHASE_REFUND_ID:
			builder.setTitle(getResources().getText(R.string.dialog_about_message));
			view.setText("Refunded");
			builder.setView(view);
			builder.setPositiveButton(getResources().getText(R.string.dialog_about_ok), null);
			break;
		case DIALOG_PURCHASE_FAIL_ID:
			builder.setTitle(getResources().getText(R.string.dialog_about_message));
			view.setText("Bedankt! Banners zijn weg");
			builder.setView(view);
			builder.setPositiveButton(getResources().getText(R.string.dialog_about_ok), null);
			break;
		case DIALOG_ABOUT_ID:
			builder.setTitle(getResources().getText(R.string.dialog_about_message));
			view.setText(getResources().getText(R.string.dialog_about_text));
			builder.setPositiveButton(getResources().getText(R.string.dialog_about_ok), null);
			break;
		}
		return builder.create();
	}

	// handlers
	private boolean handleBackButton() {
		if (isStopped)
			return true;
		if (myHistoryStack.size() > 0) {
			loadPageUrl(myHistoryStack.pop().getPageUrl(), false);
		} else {
			finish();
		}
		return true;
	}

	private boolean handleSetHomePage() {
		if (isStopped)
			return true;
		myHomePageUrl = myCurrentPageEntity.getPageUrl();
		storePreferences();
		Toast.makeText(getApplicationContext(), R.string.toast_homepageset, Toast.LENGTH_SHORT).show();
		return true;
	}

	// location
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

			public void onStatusChanged(String provider, int status, Bundle extras) {
			}

			public void onProviderEnabled(String provider) {
			}

			public void onProviderDisabled(String provider) {
			}
		};
		manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 300000, 100f, myLocationListener);
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

	// graphics
	private void initGraphics() {
		if (LogBridge.isLoggable())
			LogBridge.i("Initializing graphics");
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().setTitle(getResources().getText(R.string.main_title));
		getWindow().setSoftInputMode(1);
		setContentView(R.layout.main);
	}

	// webview
	private void initWebView() {
		if (LogBridge.isLoggable())
			LogBridge.i("Initializing webview");
		myMainWebViewAnimator = new MainWebViewAnimator(this);
		FrameLayout frameLayout = (FrameLayout) findViewById(R.id.webview);
		frameLayout.addView(myMainWebViewAnimator);
	}

	private void updateWebView(PageEntity pageEntity) {
		String htmlData = myTemplate.replace(TEMPLATE_PLACEHOLDER, pageEntity.getHtmlData());
		myMainWebViewAnimator.updateWebView(htmlData);
	}

	// ads
	private void initAdvertising() {
		if (LogBridge.isLoggable())
			LogBridge.i("Initialzing advertising");
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

	private void enableAdvertising() {
		if (LogBridge.isLoggable())
			LogBridge.i("Enabling advertising");
		myAdsEnabled = true;
		storePreferences();
		showAdView();
		initAdView();
	}

	private void disableAdvertising() {
		if (LogBridge.isLoggable())
			LogBridge.i("Disabling advertising");
		myAdsEnabled = false;
		storePreferences();
		hideAdView();
		destroyAdView();
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

	// preferences
	private void loadPreferences() {
		if (LogBridge.isLoggable())
			LogBridge.i("Loading preferences");
		SharedPreferences settings = getSharedPreferences(LOGGING_TAG, MODE_PRIVATE);
		if (settings != null) {
			myHomePageUrl = settings.getString(PREFS_HOMEPAGE_URL, CONTENT_STARTPAGEURL);
			myAdsEnabled = settings.getBoolean(PREFS_ADS_ENABLED, true);
			myTxRestored = settings.getBoolean(PREFS_TX_RESTORED, false);
			if (LogBridge.isLoggable()) {
				LogBridge.i(" " + PREFS_HOMEPAGE_URL + "=" + myHomePageUrl);
				LogBridge.i(" " + PREFS_ADS_ENABLED + "=" + myAdsEnabled);
				LogBridge.i(" " + PREFS_TX_RESTORED + "=" + myTxRestored);
			}
		}
	}

	private void storePreferences() {
		if (LogBridge.isLoggable())
			LogBridge.i("Storing preferences");
		SharedPreferences settings = getSharedPreferences(LOGGING_TAG, MODE_PRIVATE);
		if (settings != null) {
			SharedPreferences.Editor editor = settings.edit();
			if (editor != null) {
				editor.putString(PREFS_HOMEPAGE_URL, myHomePageUrl);
				editor.putBoolean(PREFS_ADS_ENABLED, myAdsEnabled);
				editor.putBoolean(PREFS_TX_RESTORED, myTxRestored);
				editor.commit();
			}
			if (LogBridge.isLoggable()) {
				LogBridge.i(" " + PREFS_HOMEPAGE_URL + "=" + myHomePageUrl);
				LogBridge.i(" " + PREFS_ADS_ENABLED + "=" + myAdsEnabled);
				LogBridge.i(" " + PREFS_TX_RESTORED + "=" + myTxRestored);
			}
		}
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

	// edittext
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

				if (newPageId.equals("050")) {
					myTxRestored = false;
					storePreferences();
					enableAdvertising();
					newPageId = currentPageId;
				}
				if (newPageId.equals("051")) {
					myTxRestored = true;
					storePreferences();
					disableAdvertising();
					newPageId = currentPageId;
				}
				if (newPageId.equals("052")) {
					myTxRestored = true;
					storePreferences();
					enableAdvertising();
					newPageId = currentPageId;
				}
				if (newPageId.equals("053")) {
					if (!myBillingService.requestPurchase("android.test.purchased", null)) {
					}
					newPageId = currentPageId;
				}
				if (newPageId.equals("054")) {
					if (!myBillingService.requestPurchase("android.test.canceled", null)) {
					}
					newPageId = currentPageId;
				}
				if (newPageId.equals("055")) {
					if (!myBillingService.requestPurchase("android.test.refunded", null)) {
					}
					newPageId = currentPageId;
				}
				if (newPageId.equals("056")) {
					if (!myBillingService.requestPurchase("net.atoom.android.tt2.noads", null)) {
					}
					newPageId = currentPageId;
				}

				if (currentPageId.equals(newPageId)) {
					if (LogBridge.isLoggable())
						LogBridge.i("Ignoring newPageId " + newPageId + " to prevent recursion");
				} else {
					String newPageUrl = "http://teletekst.nos.nl/tekst/" + newPageId + "-01.html";
					loadPageUrl(newPageUrl, true);
				}
				myPageEditText.clearFocus();

				// close soft keyboard
				InputMethodManager inputManager = (InputMethodManager) TTActivity.this
						.getSystemService(Context.INPUT_METHOD_SERVICE);
				inputManager.hideSoftInputFromWindow(myPageEditText.getWindowToken(),
						InputMethodManager.HIDE_NOT_ALWAYS);
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

		});
	}

	private void updateEditText(PageEntity pageEntity) {
		myPageEditText.setText(pageEntity.getPageId());
	}

	// buttons
	private void initButtons() {
		if (LogBridge.isLoggable())
			LogBridge.i("Initializing buttons");
		myHomeButton = (Button) findViewById(R.id.homebuttonview);
		myHomeButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				loadPageUrl(myHomePageUrl, true);
			}
		});
		myNextPageButton = (Button) findViewById(R.id.nextpagebuttonview);
		myNextPageButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				PageEntity pageEntity = myCurrentPageEntity;
				if (pageEntity != null && !pageEntity.getNextPageUrl().equals(""))
					loadPageUrl(pageEntity.getNextPageUrl(), true);
			}
		});
		myNextSubPageButton = (Button) findViewById(R.id.nextsubbuttonview);
		myNextSubPageButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				PageEntity pageEntity = myCurrentPageEntity;
				if (pageEntity != null && !pageEntity.getNextSubPageUrl().equals(""))
					loadPageUrl(pageEntity.getNextSubPageUrl(), true);
			}
		});
		myPrevPageButton = (Button) findViewById(R.id.prevpagebuttonview);
		myPrevPageButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				PageEntity pageEntity = myCurrentPageEntity;
				if (pageEntity != null && !pageEntity.getPrevPageUrl().equals(""))
					loadPageUrl(pageEntity.getPrevPageUrl(), true);
			}
		});
		myPrevSubPageButton = (Button) findViewById(R.id.prevsubbuttonview);
		myPrevSubPageButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				PageEntity pageEntity = myCurrentPageEntity;
				if (pageEntity != null && !pageEntity.getPrevSubPageUrl().equals(""))
					loadPageUrl(pageEntity.getPrevSubPageUrl(), true);
			}
		});
	}

	private void updateButtons(PageEntity pageEntity) {
		if (pageEntity.getPageUrl().equals(myHomePageUrl))
			disableButton(myHomeButton);
		else
			enableButton(myHomeButton);
		if (pageEntity.getNextPageId().equals(""))
			disableButton(myNextPageButton);
		else
			enableButton(myNextPageButton);
		if (pageEntity.getNextSubPageId().equals(""))
			disableButton(myNextSubPageButton);
		else
			enableButton(myNextSubPageButton);
		if (pageEntity.getPrevPageId().equals(""))
			disableButton(myPrevPageButton);
		else
			enableButton(myPrevPageButton);
		if (pageEntity.getPrevSubPageId().equals(""))
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

	// vending
	private void initVending() {
		myPurchaseObserver = new TTPurchaseObserver(this, myHandler);
		myBillingService = new BillingService();
		myBillingService.setContext(this);
		ResponseHandler.register(myPurchaseObserver);
		if (!myBillingService.checkBillingSupported()) {
			myBillingSupported = false;
		} else {
			if (!myTxRestored) {
				myBillingService.restoreTransactions();
			}
		}
	}

	private class TTPurchaseObserver extends PurchaseObserver {

		public TTPurchaseObserver(Activity activity, Handler handler) {
			super(activity, handler);
		}

		@Override
		public void onBillingSupported(boolean supported) {
			myBillingSupported = supported;
			if (LogBridge.isLoggable()) {
				if (supported) {
					LogBridge.i("Billing is supported");
				} else {
					LogBridge.w("Billing not supported");
				}
			}
		}

		@Override
		public void onPurchaseStateChange(PurchaseState purchaseState, String itemId, int quantity, long purchaseTime,
				String developerPayload) {
			if (LogBridge.isLoggable())
				LogBridge.w("onPurchaseStateChange() itemId: " + itemId + " " + purchaseState);
			if (purchaseState == PurchaseState.PURCHASED) {
				showDialog(DIALOG_PURCHASE_SUCCES_ID);
				disableAdvertising();
			} else if (purchaseState == PurchaseState.REFUNDED) {
				enableAdvertising();
				showDialog(DIALOG_PURCHASE_REFUND_ID);
			} else if (purchaseState == PurchaseState.CANCELED) {
				showDialog(DIALOG_PURCHASE_CANCEL_ID);
			}
		}

		@Override
		public void onRequestPurchaseResponse(RequestPurchase request, ResponseCode responseCode) {
			if (responseCode == ResponseCode.RESULT_OK) {
				if (LogBridge.isLoggable())
					LogBridge.w("Purchase successfull (" + request.mProductId + ")");
			} else if (responseCode == ResponseCode.RESULT_USER_CANCELED) {
				if (LogBridge.isLoggable())
					LogBridge.w("Purchase canceled (" + request.mProductId + ")");
				showDialog(DIALOG_PURCHASE_CANCEL_ID);
			} else {
				if (LogBridge.isLoggable())
					LogBridge.w("Purchase failed (" + request.mProductId + ")");
				showDialog(DIALOG_PURCHASE_FAIL_ID);
			}
		}

		@Override
		public void onRestoreTransactionsResponse(RestoreTransactions request, ResponseCode responseCode) {
			if (responseCode == ResponseCode.RESULT_OK) {
				if (LogBridge.isLoggable())
					LogBridge.i("Transactions restored: " + responseCode);
				myTxRestored = true;
				storePreferences();
			} else {
				if (LogBridge.isLoggable())
					LogBridge.w("Transactions error: " + responseCode);
			}
		}
	}
}
