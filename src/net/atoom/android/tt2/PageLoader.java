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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;

import net.atoom.android.tt2.util.LRUCache;
import net.atoom.android.tt2.util.LogBridge;
import net.atoom.android.tt2.util.PageIdUtil;

public final class PageLoader {

	private final static int CACHE_SIZE = 100;
	private final static long CACHE_TIME = 60000;

	private final long REQUEST_ID = System.currentTimeMillis();

	private final LRUCache<String, PageEntity> myPageCache = new LRUCache<String, PageEntity>(
			CACHE_SIZE);

	private final PriorityBlockingQueue<PageLoadRequest> myLoadRequests;
	private final ExecutorService myExecutorService;

	private final StringBuilder stringBuilder = new StringBuilder();
	private final List<String> fastTekstLinks = new LinkedList<String>();
	private final byte[] readBuffer = new byte[2048];

	public PageLoader() {
		myLoadRequests = new PriorityBlockingQueue<PageLoadRequest>();
		myExecutorService = Executors.newFixedThreadPool(1);
		myExecutorService.submit(new Runnable() {
			@Override
			public void run() {
				try {
					while (true) {
						PageLoadRequest plr = myLoadRequests.take();

						try {
							PageEntity pe = doLoadPage(plr.getPageId());
							if (plr.getPageLoadCompletionHandler() != null) {
								plr.getPageLoadCompletionHandler()
										.pageLoadCompleted(pe);
							}
						} catch (Exception e) {
							LogBridge.w("Exception!!!!!!!!!");
						}
					}
				} catch (InterruptedException e) {
				}
			}
		});
	}

	public void loadPage(String pageId, PageLoadPriority pageLoadPriority,
			PageLoadCompletionHandler pageLoadCompletionHandler) {

		if (pageId == null || pageId.equals("")) {
			return;
		}
		pageId = PageIdUtil.normalize(pageId);

		if (LogBridge.isLoggable())
			LogBridge.i("Scheduling pageload request: " + pageId);

		final PageLoadRequest pageLoadRequest = new PageLoadRequest(pageId,
				pageLoadPriority, pageLoadCompletionHandler);

		if (!myLoadRequests.offer(pageLoadRequest)) {
			LogBridge.w("Offer failed");
		}
	}

	private PageEntity doLoadPage(final String pageId) {
		PageEntity pageEntity = myPageCache.get(pageId);
		if (pageEntity != null) {
			if ((System.currentTimeMillis() - CACHE_TIME) < pageEntity
					.getCreated()) {
				if (LogBridge.isLoggable())
					LogBridge.i("Returning cached entity: " + pageId);
				return pageEntity;
			}
			myPageCache.remove(pageId);
		}

		URL pageUrl;
		try {
			pageUrl = new URL("http://teletekst.e-office.com/g/android?p="
					+ pageId + "&id=" + REQUEST_ID);
		} catch (MalformedURLException e) {
			return null;
		}

		final byte[] bytes = readPage(pageUrl);
		pageEntity = createEntity(pageId, bytes);
		if (pageEntity == null)
			return null;

		myPageCache.put(pageId, pageEntity);

		if (LogBridge.isLoggable())
			LogBridge.i("Returning new entity: " + pageId);

		return pageEntity;
	}

	private PageEntity createEntity(final String pageId, final byte[] bytes) {

		final PageEntity pageEntity = new PageEntity(pageId);
		fastTekstLinks.clear();
		stringBuilder.setLength(0);

		int mark = 0;
		for (int i = 0; i < bytes.length; i++) {

			if (i == bytes.length - 1) {
				stringBuilder.append("[");
				for (int j = mark + 5; j < (bytes.length - 6); j++) {
					stringBuilder.append(bytes[j]);
					if (j < (bytes.length - 7))
						stringBuilder.append(",");
				}
				stringBuilder.append("]");
				pageEntity.setHtmlData(stringBuilder.toString());
				break;
			}

			if (bytes[i] != 10)
				continue;

			final String line = new String(bytes, mark, i - mark);
			mark = i + 1;
			if (line.startsWith("pn=")) {
				if (line.startsWith("pn=p_")) {
					pageEntity.setPrevPageId(line.replace("pn=p_", ""));
				} else if (line.startsWith("pn=n_")) {
					pageEntity.setNextPageId(line.replace("pn=n_", ""));
				} else if (line.startsWith("pn=ps")) {
					pageEntity.setPrevSubPageId(line.replace("pn=ps", ""));
				} else if (line.startsWith("pn=ns")) {
					pageEntity.setNextSubPageId(line.replace("pn=ns", ""));
				}
			} else if (line.startsWith("ftl=")) {
				fastTekstLinks.add(line.replace("ftl=", ""));
			}
		}
		return pageEntity;
	}

	private byte[] readPage(URL url) {
		URLConnection urlConnection = null;
		InputStream inputStream = null;
		try {
			urlConnection = url.openConnection();
			inputStream = new BufferedInputStream(
					urlConnection.getInputStream());
			int byteCount = inputStream.read(readBuffer);

			byte[] resultBuffer = new byte[byteCount];
			System.arraycopy(readBuffer, 0, resultBuffer, 0, byteCount);
			return resultBuffer;
		} catch (final IOException e) {
		} finally {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException e) {
				}
			}
		}
		return null;
	}
}
