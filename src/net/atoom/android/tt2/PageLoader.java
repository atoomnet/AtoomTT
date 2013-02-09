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

public final class PageLoader {

	private final static Pattern PATTERN_PAGELINK = Pattern
			.compile("((?<=[\\s+\\+,-]|[^\\d]{1}\\.|^)(\\d{3}/\\d{1,2}(?=[\\s+\\+,-]|$))|((?<=[\\s+\\+,-]|[^\\d]{1}\\.|^)\\d{3}(?=[\\s+\\+,-]|$)))");

	private final static Pattern PATTERN_FASTTEKST = Pattern
			.compile("([^\\s]+.*)");

	private final static int CACHE_SIZE = 100;

	private final LRUCache<String, PageEntity> myPageCache = new LRUCache<String, PageEntity>(
			CACHE_SIZE);

	private final PriorityBlockingQueue<PageLoadRequest> myLoadRequests;
	private final ExecutorService myExecutorService;

	private final List<String> myFastTekstLinks = new LinkedList<String>();
	private final byte[] myReadBuffer = new byte[2048];
	private final StringBuffer myHtmlBuilder = new StringBuffer();
	private final StringBuilder myDivBuilder = new StringBuilder();
	private final Matcher myPageLinkMatcher = PATTERN_PAGELINK.matcher("");
	private final Matcher myFastTekstMatcher = PATTERN_FASTTEKST.matcher("");

	public PageLoader() {
		myLoadRequests = new PriorityBlockingQueue<PageLoadRequest>();
		myExecutorService = Executors.newFixedThreadPool(1);
		myExecutorService.submit(new PageLoadRunner());
	}

	public void loadPage(String pageId,
			final PageLoadPriority pageLoadPriority,
			final PageLoadCompletionHandler pageLoadCompletionHandler) {

		if (pageId == null || pageId.equals(""))
			return;
		pageId = PageIdUtil.normalize(pageId);

		PageEntity pageEntity = myPageCache.get(pageId);
		if (pageEntity != null
				&& System.currentTimeMillis() < pageEntity.getExpires()) {
			if (LogBridge.isLoggable())
				LogBridge.i("Returning cached entity: " + pageId);
			pageLoadCompletionHandler.pageLoadCompleted(pageEntity);
			return;
		}

		if (LogBridge.isLoggable())
			LogBridge.i("Scheduling pageload request: " + pageId);
		final PageLoadRequest pageLoadRequest = new PageLoadRequest(pageId,
				pageLoadPriority, pageLoadCompletionHandler);
		myLoadRequests.offer(pageLoadRequest);
	}

	private void preLoadPage(String pageId) {

		if (pageId == null || pageId.equals(""))
			return;
		pageId = PageIdUtil.normalize(pageId);

		PageEntity pageEntity = myPageCache.get(pageId);
		if (pageEntity != null
				&& System.currentTimeMillis() < pageEntity.getExpires()) {
			return;
		}

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

		URL pageUrl;
		try {
			// pageUrl = new URL("http://teletekst.e-office.com/g/android?p="
			// + pageId + "&id=" + REQUEST_ID);
			pageUrl = new URL("http://teletekst-data.nos.nl/page/" + pageId);

		} catch (MalformedURLException e) {
			return null;
		}

		final byte[] bytes = readPage(pageUrl);
		if (bytes == null)
			return null;

		pageEntity = createEntity(pageId, bytes);
		if (pageEntity == null)
			return null;

		myPageCache.put(pageId, pageEntity);
		if (preload) {
			preLoadPage(pageEntity.getNextPageId());
			preLoadPage(pageEntity.getPrevPageId());
			preLoadPage(pageEntity.getNextSubPageId());
			preLoadPage(pageEntity.getPrevSubPageId());
			for (String pid : pageEntity.getLinkedPageIds()) {
				preLoadPage(pid);
			}
		}

		if (LogBridge.isLoggable())
			LogBridge.i("Returning new entity: " + pageId);
		return pageEntity;
	}

	private PageEntity createEntity(final String pageId, final byte[] bytes) {

		final PageEntity pageEntity = new PageEntity(pageId);
		myFastTekstLinks.clear();

		int mark = 0;
		for (int i = 0; i < (bytes.length - 5); i++) {

			if (bytes[i] == (byte) 60 && bytes[i + 1] == (byte) 112
					&& bytes[i + 2] == (byte) 114 && bytes[i + 3] == (byte) 101
					&& bytes[i + 4] == (byte) 62) { // <pre>
				final String html = buildHtml(pageEntity, bytes, i + 5,
						myFastTekstLinks);
				pageEntity.setHtmlData(html);
				break;
			}

			if (bytes[i] != 10) {
				continue;
			}

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
				myFastTekstLinks.add(line.replace("ftl=", ""));
			}
		}
		return pageEntity;
	}

	private String buildHtml(final PageEntity pageEntity, final byte[] bytes,
			final int offset, final List<String> ftl) {

		myHtmlBuilder.setLength(0);
		for (int row = 0; row < 25; row++) {

			String bgcolor = "bl";
			String color = "w";
			String lining = "c";
			byte hold = 0;
			int mode = 0;
			int divx = 0;
			boolean dsize = false;

			myDivBuilder.setLength(0);
			for (int col = 0; col < 40; col++) {

				final int i = (row * 40) + col;
				final byte b = bytes[offset + i];

				switch (b) {
				case 12:
					dsize = false;
					break;
				case 13:
					dsize = true;
					break;
				case 30:
					hold = bytes[offset + i - 1];
					break;
				case 31:
					hold = 0;
					break;
				}

				if (mode == 0) { // text

					String s;
					if (b > 32) {
						s = String.valueOf((char) b);
					} else {
						s = " ";
					}
					myDivBuilder.append(s);

					if (b < 32 || col == 39) {

						final String line = myDivBuilder.toString();
						myDivBuilder.setLength(0);

						if (dsize) {
							myHtmlBuilder.append("<div class=\"t1 x" + divx
									+ " y" + row + " h2 w" + line.length()
									+ " b" + bgcolor + " t" + color
									+ "\" data-m=\"" + b + "\">");

						} else {
							myHtmlBuilder.append("<div class=\"t x" + divx
									+ " y" + row + " h1 w" + line.length()
									+ " b" + bgcolor + " t" + color
									+ "\" data-m=\"" + b + "\">");

						}

						if (row == 24) {
							myFastTekstMatcher.reset(line);
							if (myFastTekstMatcher.find() && ftl.size() > 0) {
								final String link = ftl.remove(0);
								pageEntity.addLinkedPageId(link);
								myFastTekstMatcher.appendReplacement(
										myHtmlBuilder,
										"<a  href=\""
												+ PageIdUtil
														.toInternalLink(link)
												+ "\">"
												+ myFastTekstMatcher.group(1)
												+ "</a>");
							}
							myFastTekstMatcher.appendTail(myHtmlBuilder);
						} else {
							myPageLinkMatcher.reset(line);
							while (myPageLinkMatcher.find()) {

								// exclude broken politie/ticker links
								final String link = myPageLinkMatcher.group(1);
								if (link.startsWith("147")
										|| link.startsWith("199"))
									continue;
								
								pageEntity.addLinkedPageId(link);
								myPageLinkMatcher.appendReplacement(
										myHtmlBuilder,
										"<a href=\""
												+ PageIdUtil
														.toInternalLink(link)
												+ "\">"
												+ myPageLinkMatcher.group(1)
												+ "</a>");
							}
							myPageLinkMatcher.appendTail(myHtmlBuilder);
						}

						myHtmlBuilder.append("</div>\n");
						divx += line.length();
					}
				} else {

					byte m = 32;
					if (b < 32) {
						if (hold > 0) {
							m = hold;
						}
					} else {
						m = b;
					}

					myHtmlBuilder.append("<div class=\"t x" + divx + " y" + row
							+ " h1 w1" + " b" + bgcolor + " t" + color
							+ "\" data-m=\"" + m + "\"><svg>");
					for (int bit = 0; bit < 5; bit++) {
						if ((m & (1 << bit)) != 0)
							myHtmlBuilder.append("<use xlink:href=\"#p"
									+ lining + bit + "\" />");
					}
					if ((m & (1 << 6)) != 0)
						myHtmlBuilder.append("<use xlink:href=\"#p" + lining
								+ "5\" />");

					// myHtmlBuilder.append("<svg><use xlink:href=\"#m" + lining
					// + m + "\"></svg>");
					myHtmlBuilder.append("</svg></div>\n");
					divx++;
				}

				switch (b) {
				case 0:
					mode = 0;
					color = "bl";
					break;
				case 1:
					mode = 0;
					color = "r";
					break;
				case 2:
					mode = 0;
					color = "g";
					break;
				case 3:
					mode = 0;
					color = "y";
					break;
				case 4:
					mode = 0;
					color = "b";
					break;
				case 5:
					mode = 0;
					color = "m";
					break;
				case 6:
					mode = 0;
					color = "c";
					break;
				case 7:
					mode = 0;
					color = "w";
					break;
				case 16:
					mode = 1;
					color = "bl";
					break;
				case 17:
					mode = 1;
					color = "r";
					break;
				case 18:
					mode = 1;
					color = "g";
					break;
				case 19:
					mode = 1;
					color = "y";
					break;
				case 20:
					mode = 1;
					color = "b";
					break;
				case 21:
					mode = 1;
					color = "m";
					break;
				case 22:
					mode = 1;
					color = "c";
					break;
				case 23:
					mode = 1;
					color = "w";
					break;
				case 25:
					lining = "c";
					break;
				case 26:
					lining = "s";
					color = "w";
					break;
				case 28:
					bgcolor = "bl";
					break;
				case 29:
					bgcolor = color;
					break;
				}
			}
		}
		return myHtmlBuilder.toString();
	}

	private byte[] readPage(final URL url) {
		URLConnection urlConnection = null;
		InputStream inputStream = null;
		try {
			urlConnection = url.openConnection();
			inputStream = new BufferedInputStream(
					urlConnection.getInputStream());
			int byteCount = inputStream.read(myReadBuffer);

			byte[] resultBuffer = new byte[byteCount];
			System.arraycopy(myReadBuffer, 0, resultBuffer, 0, byteCount);
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
