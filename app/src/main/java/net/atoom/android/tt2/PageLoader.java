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
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;

import net.atoom.android.tt2.util.LRUCache;
import net.atoom.android.tt2.util.LogBridge;

public final class PageLoader {

	private static final String BASE_URL = "http://teletekst-data.nos.nl/page/";

	private final LRUCache<String, PageEntity> myPageCache = new LRUCache<String, PageEntity>(
			100);

	private final PriorityBlockingQueue<PageLoadRequest> myLoadRequests = new PriorityBlockingQueue<PageLoadRequest>();
	private final ExecutorService myExecutorService = Executors
			.newFixedThreadPool(1);

	private final PageProcessor myPageProcessor = new PageProcessor();

	private final byte[] myReadBuffer = new byte[2048];

	public PageLoader() {
		myExecutorService.submit(new PageLoadRunner());
	}

	public void loadPage(String pageId,
			final PageLoadPriority pageLoadPriority,
			final PageLoadCompletionHandler pageLoadCompletionHandler) {

		if (pageId == null || pageId.equals(""))
			return;
		pageId = PageIdUtil.normalize(pageId);

		final PageEntity pageEntity = myPageCache.get(pageId);
		if (pageEntity != null
				&& System.currentTimeMillis() < pageEntity.getExpires()) {

			if (LogBridge.isLoggable())
				LogBridge.i("Returning cached entity: " + pageId);
			pageLoadCompletionHandler.pageLoadCompleted(pageEntity);
			preLoadReferencedPages(pageEntity);
			return;
		}

		if (LogBridge.isLoggable())
			LogBridge.i("Scheduling pageload request: " + pageId);
		final PageLoadRequest pageLoadRequest = new PageLoadRequest(pageId,
				pageLoadPriority, pageLoadCompletionHandler);
		myLoadRequests.offer(pageLoadRequest);
	}

	private void preLoadReferencedPages(final PageEntity pageEntity) {
		preLoadPage(pageEntity.getNextPageId());
		preLoadPage(pageEntity.getPrevPageId());
		preLoadPage(pageEntity.getNextSubPageId());
		preLoadPage(pageEntity.getPrevSubPageId());
		for (final String fastLinkPageId : pageEntity.getFastLinkPageIds()) {
			preLoadPage(fastLinkPageId);
		}
		for (final String linkedPageId : pageEntity.getLinkedPageIds()) {
			preLoadPage(linkedPageId);
		}
	}

	private void preLoadPage(String pageId) {
		if (pageId == null || pageId.equals(""))
			return;
		pageId = PageIdUtil.normalize(pageId);
		final PageEntity pageEntity = myPageCache.get(pageId);
		if (pageEntity != null
				&& System.currentTimeMillis() < pageEntity.getExpires())
			return;

		if (LogBridge.isLoggable())
			LogBridge.i("Scheduling pageload request: " + pageId);
		final PageLoadRequest pageLoadRequest = new PageLoadRequest(pageId,
				PageLoadPriority.LOW, null, false);
		myLoadRequests.offer(pageLoadRequest);
	}

	private PageEntity doLoadPage(final String pageId, final boolean preload) {

		PageEntity pageEntity = myPageCache.get(pageId);
		if (pageEntity != null) {
			if (System.currentTimeMillis() < pageEntity.getExpires()) {
				if (LogBridge.isLoggable())
					LogBridge.i("Returning cached entity: " + pageId);
				return pageEntity;
			}
			myPageCache.remove(pageId);
		}

		final byte[] bytes = readPageBytes(pageId);
		if (bytes == null)
			return null;

		pageEntity = myPageProcessor.process(pageId, bytes);
		if (pageEntity == null)
			return null;

		myPageCache.put(pageId, pageEntity);
		if (preload) {
			preLoadReferencedPages(pageEntity);
		}

		if (LogBridge.isLoggable())
			LogBridge.i("Returning new entity: " + pageId);
		return pageEntity;
	}

	private byte[] readPageBytes(final String pageId) {
		URLConnection urlConnection = null;
		InputStream inputStream = null;
		try {
			URL pageUrl = new URL(BASE_URL + pageId);
			urlConnection = pageUrl.openConnection();
			inputStream = new BufferedInputStream(
					urlConnection.getInputStream());
			int byteCount = inputStream.read(myReadBuffer);

			byte[] resultBuffer = new byte[byteCount];
			System.arraycopy(myReadBuffer, 0, resultBuffer, 0, byteCount);
			return resultBuffer;
		} catch (final IOException e) {
			LogBridge.w("IoException while loading " + pageId);
			return null;
		} finally {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private class PageLoadRunner implements Runnable {

		@Override
		public void run() {
			try {
				while (true) {
					final PageLoadRequest pageLoadRequest = myLoadRequests
							.take();
					try {
						final PageEntity pageEntity = doLoadPage(
								pageLoadRequest.getPageId(),
								pageLoadRequest.isPreload());
						if (pageLoadRequest.getPageLoadCompletionHandler() != null) {
							pageLoadRequest.getPageLoadCompletionHandler()
									.pageLoadCompleted(pageEntity);
						}
					} catch (final Exception e) {
						LogBridge.w("Received an Exception "
								+ e.getClass().getName() + " " + e.getCause());
					}
				}
			} catch (final InterruptedException e) {
				LogBridge.w("Received an InterruptedException");
			}
		}
	}
}
