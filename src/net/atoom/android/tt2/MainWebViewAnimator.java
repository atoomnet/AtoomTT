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

import android.graphics.Color;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ViewAnimator;

public class MainWebViewAnimator extends ViewAnimator {

	private static final String CONTENT_STARTPAGEURL = "http://teletekst.nos.nl/tekst/101-01.html";
	private static final String CONTENT_BASEURL = "http://teletekst.nos.nl/tekst/";

	private static final String CONTENT_MIME_TYPE = "text/html";
	private static final String CONTENT_ENCODING = "utf-8";

	private final TTActivity myTTActivity;
	private final GestureDetector myGestureDetector;
	private final WebView myWebView;

	public MainWebViewAnimator(TTActivity ttActivity) {
		super(ttActivity);
		myTTActivity = ttActivity;
		myGestureDetector = new GestureDetector(myTTActivity,
				new MainWebViewGestureDetector(this));

		myWebView = new WebView(myTTActivity);
		initWebView();
		addView(myWebView);
	}

	public void updateWebView(final String htmlData) {
		myWebView.loadDataWithBaseURL(CONTENT_BASEURL, htmlData,
				CONTENT_MIME_TYPE, CONTENT_ENCODING, CONTENT_STARTPAGEURL);
	}

	public void loadNextPage() {
		myTTActivity.loadNextPage();
	}

	public void loadPrevPage() {
		myTTActivity.loadPrevPage();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return myGestureDetector.onTouchEvent(event);
	}

	private void initWebView() {
		myWebView.setOnTouchListener(new MainWebViewOnTouchListener(this));
		myWebView.setWebViewClient(new MainWebViewClient(myTTActivity));
		myWebView.setScrollContainer(false);
		myWebView.setVerticalScrollBarEnabled(true);
		myWebView.setHorizontalScrollBarEnabled(false);
		myWebView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
		myWebView.setBackgroundColor(Color.BLACK);
		WebSettings webSettings = myWebView.getSettings();
		webSettings.setSavePassword(false);
		webSettings.setSaveFormData(false);
		webSettings.setJavaScriptEnabled(false);
		webSettings.setSupportZoom(false);
		webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
	}

}
