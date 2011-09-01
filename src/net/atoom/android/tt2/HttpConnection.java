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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import net.atoom.android.tt2.util.LogBridge;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

public final class HttpConnection {

	private static final int HTTP_OK = 200;
	private static final String ETAG_HEADER = "ETag";
	private static final String DEFAULT_ENCODING = "Cp1252";

	private final DefaultHttpClient myHttpClient;

	public HttpConnection() {
		HttpParams httpParameters = new BasicHttpParams();
		int timeoutConnection = 5000;
		HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
		int timeoutSocket = 5000;
		HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
		myHttpClient = new DefaultHttpClient(httpParameters);
	}

	public synchronized PageEntity loadPage(final String pageUrl) {
		HttpGet httpUriRequest = new HttpGet(pageUrl);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			HttpResponse httpResponse = myHttpClient.execute(httpUriRequest);

			// this happens...
			if (httpResponse.getStatusLine().getStatusCode() != HTTP_OK) {
				if (LogBridge.isLoggable())
					LogBridge.w("Invalid statuscode for: " + pageUrl);
				return null;
			}
			HttpEntity httpEntity = httpResponse.getEntity();
			httpEntity.writeTo(baos);
			String eTag = httpResponse.getFirstHeader(ETAG_HEADER).getValue();
			String htmlData = baos.toString(DEFAULT_ENCODING);

			// this happens too..
			if (htmlData.equals("")) {
				if (LogBridge.isLoggable())
					LogBridge.w("Empty responsebody for: " + pageUrl);
				return null;
			}

			return new PageEntity(pageUrl, htmlData, eTag);
		} catch (ClientProtocolException e) {
			if (LogBridge.isLoggable())
				LogBridge.w("Failed to load page: " + e.getMessage());
		} catch (IOException e) {
			if (LogBridge.isLoggable())
				LogBridge.w("Failed to load page: " + e.getMessage());
		}
		return null;
	}

	public synchronized boolean isPageModified(final String pageUrl, final String eTag) {
		HttpHead httpUriRequest = new HttpHead(pageUrl);
		try {
			HttpResponse httpResponse = myHttpClient.execute(httpUriRequest);
			if (httpResponse.getStatusLine().getStatusCode() != HTTP_OK) {
				if (LogBridge.isLoggable())
					LogBridge.w("Invalid statuscode for: " + pageUrl);
				return false;
			}
			Header eTagHeader = httpResponse.getFirstHeader(ETAG_HEADER);
			if (eTagHeader != null && eTagHeader.getValue().equals(eTag)) {
				if (LogBridge.isLoggable())
					LogBridge.i("Page not modified: " + pageUrl + " (" + eTag + ")");
				return true;
			}
		} catch (ClientProtocolException e) {
			if (LogBridge.isLoggable())
				LogBridge.w("Failed to check page: " + e.getMessage());
		} catch (IOException e) {
			if (LogBridge.isLoggable())
				LogBridge.w("Failed to check page: " + e.getMessage());
		}
		if (LogBridge.isLoggable())
			LogBridge.i("Page is modified: " + pageUrl + "/ (" + eTag + ")");
		return false;
	}
}