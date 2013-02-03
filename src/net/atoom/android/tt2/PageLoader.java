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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.atoom.android.tt2.util.LRUCache;
import net.atoom.android.tt2.util.LogBridge;
import net.atoom.android.tt2.util.PageIdUtil;

public final class PageLoader {

	private final static int CACHE_SIZE = 100;
	private final static long CACHE_TIME = 30000;

	private final static String[] FTL_CLASS = new String[] { "red", "lime",
			"yellow", "aqua" };

	private final Pattern PATTERN_PAGELINK = Pattern
			.compile("((?<=[\\s+\\+,-]|^)(\\d{3}/\\d{1,2}(?=[\\s+\\+,-]|$))|((?<=[\\s+\\+,-]|^)\\d{3}(?=[\\s+\\+,-]|$)))");

	private final Pattern PATTERN_FASTTEKST = Pattern
			.compile("([^\\s]+\\s?[^\\s]+)");

	private final long REQUEST_ID = System.currentTimeMillis();

	private final LRUCache<String, PageEntity> myPageCache = new LRUCache<String, PageEntity>(
			CACHE_SIZE);

	private final PriorityBlockingQueue<PageLoadRequest> myLoadRequests;
	private final ExecutorService myExecutorService;

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

		final List<String> lines = readPageURL(pageUrl);
		if (lines == null)
			return null;

		pageEntity = createEntity(pageId, lines);
		if (pageEntity == null)
			return null;

		myPageCache.put(pageId, pageEntity);

		if (LogBridge.isLoggable())
			LogBridge.i("Returning new entity: " + pageId);

		return pageEntity;
	}

	private PageEntity createEntity(final String pageId,
			final List<String> lines) {

		final PageEntity pageEntity = new PageEntity(pageId);
		final List<String> ftl = new LinkedList<String>();
		final StringBuffer sb = new StringBuffer();

		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			line = line.replace("<pre>", "");
			line = line.replace("</pre>", "");
			if (line.startsWith("pn=p_")) {
				pageEntity.setPrevPageId(line.replace("pn=p_", ""));
			} else if (line.startsWith("pn=n_")) {
				pageEntity.setNextPageId(line.replace("pn=n_", ""));
			} else if (line.startsWith("pn=ps")) {
				pageEntity.setPrevSubPageId(line.replace("pn=ps", ""));
			} else if (line.startsWith("pn=ns")) {
				pageEntity.setNextSubPageId(line.replace("pn=ns", ""));
			} else if (line.startsWith("ftl=")) {
				ftl.add(line.replace("ftl=", ""));
			} else {
				if (i < (lines.size() - 1)) {
					final Matcher m = PATTERN_PAGELINK.matcher(line);
					while (m.find()) {
						m.appendReplacement(
								sb,
								"<a href=\""
										+ PageIdUtil.toInternalLink(m.group(1))
										+ "\">" + m.group(1) + "</a>");
					}
					m.appendTail(sb);
				} else {
					sb.append("\n  ");
					Matcher m = PATTERN_FASTTEKST.matcher(line);
					int ftlIndex = 0;
					while (m.find() && ftlIndex < 4) {
						if (ftlIndex < ftl.size()) {
							m.appendReplacement(
									sb,
									"<a class=\""
											+ FTL_CLASS[ftlIndex]
											+ "\" href=\""
											+ PageIdUtil.toInternalLink(ftl
													.get(ftlIndex++)) + "\">"
											+ m.group(1) + "</a>");
						} else {
							m.appendReplacement(sb, m.group(1));
						}
					}
					m.appendTail(sb);
				}
				sb.append("\n");
			}
		}
		pageEntity.setHtmlData(sb.toString());
		return pageEntity;
	}

	private List<String> readPageURL(URL url) {

		List<String> lines = null;
		byte[] bytes = new byte[50];
		byte[] bytes2 = new byte[2024];

		URLConnection con = null;
		InputStream is = null;
		try {
			con = url.openConnection();
			is = new BufferedInputStream(con.getInputStream());

			int index = 0;
			int index2 = 0;
			int c = is.read();
			while (c != -1) {
				byte b = (byte) c;
				bytes2[index2++] = b;
				if (b > 32) {
					bytes[index++] = b;
				} else if (index < 39 && b != 10) {
					bytes[index++] = 32;
				} else {
					if (lines == null)
						lines = new LinkedList<String>();
					lines.add(new String(bytes));
					for (int i = 0; i < 50; i++)
						bytes[i] = 0;
					index = 0;
				}
				c = is.read();
			}
			lines.add(new String(bytes));
		} catch (final IOException e) {
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
		}
		return lines;
	}
}
