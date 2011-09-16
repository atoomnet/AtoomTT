package net.atoom.android.tt2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import net.atoom.android.tt2.util.BoundStack;
import net.atoom.android.tt2.util.LogBridge;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ads.AdRequest;
import com.google.ads.AdView;

public class TTMainViewFragment extends Fragment {

	public static final String LOGGING_TAG = "AtoomTT";

	private static final String CONTENT_STARTPAGEURL = "http://teletekst.nos.nl/tekst/101-01.html";

	private static final String PREFS_CURRENT_URL = "currentUrl";
	private static final String PREFS_HOMEPAGE_URL = "homepageUrl";

	private static final String TEMPLATE_FILENAME = "template.html";
	private static final String TEMPLATE_PLACEHOLDER = "[pageContent]";

	private static final int MENU_ABOUT = 1;
	private static final int MENU_SETHOME = 2;
	private static final int MENU_CLOSE = 3;

	private static final int HISTORY_SIZE = 50;
	private static final long RELOAD_INTERVAL_MS = 60000;
	private static final long AD_INIT_DELAY_MS = 3000;

	private PageLoader myPageLoader;
	private Handler myHandler;
	private BoundStack<PageEntity> myHistoryStack;

	private MainWebViewAnimator myMainWebViewAnimator;
	private EditText myPageEditText;

	private Button myHomeButton;
	private Button myNextPageButton;
	private Button myNextSubPageButton;
	private Button myPrevPageButton;
	private Button myPrevSubPageButton;

	private String myStartPageUrl;
	private String myHomePageUrl;
	private String myTemplate;
	private PageEntity myCurrentPageEntity;
	private int myPageLoadCount = 0;

	private volatile Location myLocation = null;
	private volatile boolean isAdinitialized = false;
	private volatile boolean isStopped = false;

	private View myView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		myHandler = new Handler();
		myPageLoader = new PageLoader();
		myHistoryStack = new BoundStack<PageEntity>(HISTORY_SIZE);

		// default location in NL
		myLocation = new Location("AtoomTT");
		myLocation.setLatitude(52.4498d);
		myLocation.setLongitude(4.8223d);
		myLocation.setTime(System.currentTimeMillis());
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		loadPreferences();
		loadTemplate();

		final View view = inflater.inflate(R.layout.fragment_main, container, false);
		myView = view;

		initEditText(view);
		initButtons(view);
		initMainWebViewAnimator(view);
		initLocationRequest();

		loadPageUrl(myStartPageUrl, true);

		myHandler.postDelayed(new Runnable() {
			public void run() {
				if (!isAdinitialized) {
					isAdinitialized = true;
					initAdView(view);
				}
			}
		}, AD_INIT_DELAY_MS);
		return view;
	}

	@Override
	public void onStop() {
		isStopped = true;
		super.onStop();
		storePreferences();
	}

	// @Override
	// public boolean onKeyDown(int keyCode, KeyEvent event) {
	// if (keyCode == KeyEvent.KEYCODE_BACK) {
	// return handleBackButton();
	// }
	// return super.onKeyDown(keyCode, event);
	// }

	@Override
	public void onConfigurationChanged(Configuration config) {
		// Do nothing, this is to prevent the activity from being restarted when
		// the keyboard opens.
		super.onConfigurationChanged(config);
	}

	// @Override
	// public boolean onCreateOptionsMenu(Menu menu) {
	// menu.add(0, MENU_SETHOME, 0, R.string.menu_sethomepage);
	// menu.add(0, MENU_ABOUT, 1, R.string.menu_about);
	// menu.add(0, MENU_CLOSE, 2, R.string.menu_close);
	// return true;
	// }

	// @Override
	// public boolean onOptionsItemSelected(MenuItem item) {
	// switch (item.getItemId()) {
	// case MENU_ABOUT:
	// return handleAboutDialog();
	// case MENU_SETHOME:
	// return handleSetHomePage();
	// case MENU_CLOSE:
	// isStopped = true;
	// storePreferences();
	// finish();
	// }
	// return false;
	// }

	public synchronized void loadPageUrl(final String pageUrl, final boolean updateHistory) {

		myPageLoadCount++; // cancels previous reloads

		myPageLoader.loadPage(pageUrl, PageLoadPriority.HIGH, new PageLoadCompletionHandler() {

			@Override
			public void pageLoadCompleted(final PageEntity pageEntity) {

				if (pageEntity == null) {
					myHandler.post(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(getActivity().getApplicationContext(), R.string.toast_pagenotfound,
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

						if (previousPageEntity != null && updateHistory
								&& !previousPageEntity.getPageUrl().equals(pageUrl)) {
							myHistoryStack.push(previousPageEntity);
						}
						updateEditText(pageEntity);
						updateButtons(pageEntity);
						updateWebView(pageEntity);

						final ActionBar actionBar = getActivity().getActionBar();
						actionBar.setTitle(myCurrentPageEntity.getPageTitle());

						myPageLoader.loadPage(pageEntity.getNextPageUrl(), PageLoadPriority.LOW, null);
						myPageLoader.loadPage(pageEntity.getPrevPageUrl(), PageLoadPriority.LOW, null);
						myPageLoader.loadPage(pageEntity.getNextSubPageUrl(), PageLoadPriority.LOW, null);
						myPageLoader.loadPage(pageEntity.getPrevSubPageUrl(), PageLoadPriority.LOW, null);

						myHandler.postDelayed(new ReloadRunnable(TTMainViewFragment.this, myPageLoadCount),
								RELOAD_INTERVAL_MS);
					}
				});
			}
		});

	}

	public synchronized void reloadPageUrl(final int pageLoadCount) {
		PageEntity pageEntity = myCurrentPageEntity;
		if (myPageLoadCount == pageLoadCount && !isStopped && pageEntity != null) {
			if (LogBridge.isLoggable())
				LogBridge.i("Reloading...");
			Toast.makeText(getActivity().getApplicationContext(), R.string.toast_pagereload, Toast.LENGTH_SHORT).show();
			loadPageUrl(pageEntity.getPageUrl(), false);
		} else {
			if (LogBridge.isLoggable())
				LogBridge.i("Aborting reload");
		}
	}

	public synchronized void loadNextPage() {
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

	private boolean handleAboutDialog() {
		AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
		alert.setTitle(getResources().getText(R.string.dialog_about_title));
		alert.setMessage(getResources().getText(R.string.dialog_about_message));
		TextView view = new TextView(getActivity());
		view.setText(getResources().getText(R.string.dialog_about_text));
		alert.setView(view);
		alert.setPositiveButton(getResources().getText(R.string.dialog_about_ok),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
					}
				});
		alert.show();
		return true;
	}

	private boolean handleBackButton() {
		if (myHistoryStack.size() > 0) {
			loadPageUrl(myHistoryStack.pop().getPageUrl(), false);
		} else {
			isStopped = true;
			storePreferences();
			// finish();
		}
		return true;
	}

	private boolean handleSetHomePage() {
		myHomePageUrl = myCurrentPageEntity.getPageUrl();
		Toast.makeText(getActivity().getApplicationContext(), R.string.toast_homepageset, Toast.LENGTH_SHORT).show();
		return true;
	}

	private void initLocationRequest() {
		final LocationManager locationManager = (LocationManager) getActivity().getSystemService(
				Context.LOCATION_SERVICE);
		if (locationManager != null) {
			locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 300000, 100f,
					new LocationListener() {
						public void onLocationChanged(final Location location) {
							if (LogBridge.isLoggable())
								LogBridge.i("Location update recieved: " + location);
							locationManager.removeUpdates(this);
							if (!isAdinitialized) {
								isAdinitialized = true;
								myLocation = location;
								myHandler.post(new Runnable() {
									public void run() {
										initAdView(myView);
									}
								});
							}
						}

						public void onStatusChanged(String provider, int status, Bundle extras) {
						}

						public void onProviderEnabled(String provider) {
						}

						public void onProviderDisabled(String provider) {
						}
					});
		}
	}

	private void initMainWebViewAnimator(View view) {
		myMainWebViewAnimator = new MainWebViewAnimator(getActivity(), this);
		FrameLayout frameLayout = (FrameLayout) view.findViewById(R.id.webview);
		frameLayout.addView(myMainWebViewAnimator);
	}

	private void initEditText(View view) {

		myPageEditText = (EditText) view.findViewById(R.id.gotopageview);
		myPageEditText.setSelectAllOnFocus(true);
		myPageEditText.addTextChangedListener(new TextWatcher() {

			public void afterTextChanged(Editable s) {
				if (myPageEditText.getText().length() == 3) {

					String newPageId = myPageEditText.getText() + "";
					String currentPageId = "";
					PageEntity pageEntity = myCurrentPageEntity;
					if (pageEntity != null && pageEntity.getPageId() != null) {
						currentPageId = pageEntity.getPageId();
						if (currentPageId.length() == 6) {
							currentPageId = currentPageId.substring(0, 3);
						}
					}

					if (currentPageId.equals(newPageId)) {
						if (LogBridge.isLoggable())
							LogBridge.i("Ignoring newPageId " + newPageId + " to prevent recursion");
					} else {
						String newPageUrl = "http://teletekst.nos.nl/tekst/" + newPageId + "-01.html";
						loadPageUrl(newPageUrl, true);
						myPageEditText.clearFocus();

						// close soft keyboard
						InputMethodManager inputManager = (InputMethodManager) TTMainViewFragment.this.getActivity()
								.getSystemService(Context.INPUT_METHOD_SERVICE);
						inputManager.hideSoftInputFromWindow(myPageEditText.getWindowToken(),
								InputMethodManager.HIDE_NOT_ALWAYS);

					}
				}
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

		});
	}

	private void initButtons(View view) {

		myHomeButton = (Button) view.findViewById(R.id.homebuttonview);
		myHomeButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				loadPageUrl(myHomePageUrl, true);
			}
		});

		myNextPageButton = (Button) view.findViewById(R.id.nextpagebuttonview);
		myNextPageButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				PageEntity pageEntity = myCurrentPageEntity;
				if (pageEntity != null && !pageEntity.getNextPageUrl().equals(""))
					loadPageUrl(pageEntity.getNextPageUrl(), true);
			}
		});

		myNextSubPageButton = (Button) view.findViewById(R.id.nextsubbuttonview);
		myNextSubPageButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				PageEntity pageEntity = myCurrentPageEntity;
				if (pageEntity != null && !pageEntity.getNextSubPageUrl().equals(""))
					loadPageUrl(pageEntity.getNextSubPageUrl(), true);
			}
		});

		myPrevPageButton = (Button) view.findViewById(R.id.prevpagebuttonview);
		myPrevPageButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				PageEntity pageEntity = myCurrentPageEntity;
				if (pageEntity != null && !pageEntity.getPrevPageUrl().equals(""))
					loadPageUrl(pageEntity.getPrevPageUrl(), true);
			}
		});

		myPrevSubPageButton = (Button) view.findViewById(R.id.prevsubbuttonview);
		myPrevSubPageButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				PageEntity pageEntity = myCurrentPageEntity;
				if (pageEntity != null && !pageEntity.getPrevSubPageUrl().equals(""))
					loadPageUrl(pageEntity.getPrevSubPageUrl(), true);
			}
		});
	}

	private void initAdView(View view) {
		if (LogBridge.isLoggable())
			LogBridge.i("Initializing AdView : " + myLocation);
		AdView adView = (AdView) view.findViewById(R.id.ad);
		AdRequest adRequest = new AdRequest();
		adRequest.setLocation(myLocation);
		adView.loadAd(adRequest);
	}

	private void loadPreferences() {
		SharedPreferences settings = getActivity().getSharedPreferences(LOGGING_TAG, Activity.MODE_PRIVATE);
		if (settings != null) {
			myStartPageUrl = settings.getString(PREFS_CURRENT_URL, CONTENT_STARTPAGEURL);
			myHomePageUrl = settings.getString(PREFS_HOMEPAGE_URL, CONTENT_STARTPAGEURL);
		}
	}

	private void loadTemplate() {
		if (myTemplate == null || myTemplate.equals("")) {
			InputStream is = null;
			try {
				is = getActivity().getAssets().open(TEMPLATE_FILENAME);
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

	private void storePreferences() {
		SharedPreferences settings = getActivity().getSharedPreferences(LOGGING_TAG, Activity.MODE_PRIVATE);
		if (settings != null) {
			SharedPreferences.Editor editor = settings.edit();
			if (editor != null) {
				PageEntity pageEntity = myCurrentPageEntity;
				if (pageEntity != null) {
					editor.putString(PREFS_CURRENT_URL, pageEntity.getPageUrl());
				} else {
					editor.putString(PREFS_CURRENT_URL, "");
				}
				editor.putString(PREFS_HOMEPAGE_URL, myHomePageUrl);
				editor.commit();
			}
		}
	}

	private void updateEditText(PageEntity pageEntity) {
		myPageEditText.setText(pageEntity.getPageId());
	}

	private void updateWebView(PageEntity pageEntity) {
		String htmlData = myTemplate.replace(TEMPLATE_PLACEHOLDER, pageEntity.getHtmlData());
		myMainWebViewAnimator.updateWebView(htmlData);
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
}