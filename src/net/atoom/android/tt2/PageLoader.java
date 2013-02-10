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

	private static final String BASE_URL = "http://teletekst-data.nos.nl/page/";

	private final static Pattern PATTERN_PAGELINK = Pattern
			.compile("((?<=[\\s+\\+,-]|[^\\d]{1}\\.|^)(\\d{3}/\\d{1,2}(?=[\\s+\\+,-]|$))|((?<=[\\s+\\+,-]|[^\\d]{1}\\.|^)\\d{3}(?=[\\s+\\+,-]|$)))");

	private final static Pattern PATTERN_FASTTEKST = Pattern
			.compile("([^\\s]+.*)");

	private final LRUCache<String, PageEntity> myPageCache = new LRUCache<String, PageEntity>(
			100);

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

		final byte[] bytes = readPage(pageId);
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
		for (int i = 0; i < bytes.length; i++) {

			if (i < (bytes.length - 5) && bytes[i] == (byte) 60
					&& bytes[i + 1] == (byte) 112 && bytes[i + 2] == (byte) 114
					&& bytes[i + 3] == (byte) 101 && bytes[i + 4] == (byte) 62) { // <pre>
				final String html = buildHtml(pageEntity, bytes, i + 5,
						myFastTekstLinks);
				pageEntity.setHtmlData(html);
				return pageEntity;
			}

			if (bytes[i] != 10) {
				continue;
			}

			final String line = new String(bytes, mark, i - mark);
			mark = i + 1;
			if (line.startsWith("pn=p_")) {
				pageEntity.setPrevPageId(line.replace("pn=p_", ""));
			} else if (line.startsWith("pn=n_")) {
				pageEntity.setNextPageId(line.replace("pn=n_", ""));
			} else if (line.startsWith("pn=ps")) {
				pageEntity.setPrevSubPageId(line.replace("pn=ps", ""));
			} else if (line.startsWith("pn=ns")) {
				pageEntity.setNextSubPageId(line.replace("pn=ns", ""));
			} else if (line.startsWith("ftl=")) {
				myFastTekstLinks.add(line.replace("ftl=", ""));
			}
		}
		return null;
	}

	private String buildHtml(final PageEntity pageEntity, final byte[] bytes,
			final int offset, final List<String> ftl) {

		myHtmlBuilder.setLength(0);
		for (int row = 0; row < 25; row++) {

			String backColor = "bl";
			String textColor = "w";
			String lining = "c";
			byte hold = 0;
			int divx = 0;

			boolean textMode = true;
			boolean doubleSize = false;

			myDivBuilder.setLength(0);
			for (int col = 0; col < 40; col++) {

				final int byteIndex = (row * 40) + col + offset;
				final byte currentByte = bytes[byteIndex];

				switch (currentByte) {
				case 12:
					doubleSize = false;
					break;
				case 13:
					doubleSize = true;
					break;
				case 30:
					hold = bytes[byteIndex - 1];
					break;
				case 31:
					hold = 0;
					break;
				case 127:
					textMode = false;
					break;
				}

				if (textMode) { // text

					String s;
					if (currentByte > 32) {
						s = String.valueOf((char) currentByte);
					} else {
						s = " ";
					}
					myDivBuilder.append(s);

					if (currentByte < 32 || col == 39) {

						final String line = myDivBuilder.toString();
						myDivBuilder.setLength(0);

						if (doubleSize) {
							myHtmlBuilder.append("<div class=\"t1 x" + divx
									+ " y" + row + " h2 w" + line.length()
									+ " b" + backColor + " t" + textColor
									+ "\" data-m=\"" + currentByte + "\">");

						} else {
							myHtmlBuilder.append("<div class=\"t x" + divx
									+ " y" + row + " h1 w" + line.length()
									+ " b" + backColor + " t" + textColor
									+ "\" data-m=\"" + currentByte + "\">");
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
					if (currentByte < 32) {
						if (hold > 0) {
							m = hold;
						}
					} else {
						m = currentByte;
					}

					boolean line = false;
					if (m == 44) {
						line = true;
						for (int x = 1; x < (40 - col); x++) {
							line &= (bytes[byteIndex + x] == m);
							if (!line)
								break;
						}
					}

					if (line) {
						int width = 40 - col;
						myHtmlBuilder.append("<div class=\"t x" + divx + " y"
								+ row + " h1 w" + width + " b" + backColor
								+ " t" + textColor + "\" data-m=\"" + m
								+ "\"><svg>");
						myHtmlBuilder.append("<use xlink:href=\"#l" + lining
								+ "0\" />");
						myHtmlBuilder.append("</svg></div>\n");
						col = 40;
					} else {
						myHtmlBuilder.append("<div class=\"t x" + divx + " y"
								+ row + " h1 w1" + " b" + backColor + " t"
								+ textColor + "\" data-m=\"" + m + "\"><svg>");
						for (int bit = 0; bit < 5; bit++) {
							if ((m & (1 << bit)) != 0)
								myHtmlBuilder.append("<use xlink:href=\"#p"
										+ lining + bit + "\" />");
						}
						if ((m & (1 << 6)) != 0)
							myHtmlBuilder.append("<use xlink:href=\"#p"
									+ lining + "5\" />");
						myHtmlBuilder.append("</svg></div>\n");
						divx++;
					}
				}

				switch (currentByte) {
				case 0:
					textMode = true;
					textColor = "bl";
					break;
				case 1:
					textMode = true;
					textColor = "r";
					break;
				case 2:
					textMode = true;
					textColor = "g";
					break;
				case 3:
					textMode = true;
					textColor = "y";
					break;
				case 4:
					textMode = true;
					textColor = "b";
					break;
				case 5:
					textMode = true;
					textColor = "m";
					break;
				case 6:
					textMode = true;
					textColor = "c";
					break;
				case 7:
					textMode = true;
					textColor = "w";
					break;
				case 16:
					textMode = false;
					textColor = "bl";
					break;
				case 17:
					textMode = false;
					textColor = "r";
					break;
				case 18:
					textMode = false;
					textColor = "g";
					break;
				case 19:
					textMode = false;
					textColor = "y";
					break;
				case 20:
					textMode = false;
					textColor = "b";
					break;
				case 21:
					textMode = false;
					textColor = "m";
					break;
				case 22:
					textMode = false;
					textColor = "c";
					break;
				case 23:
					textMode = false;
					textColor = "w";
					break;
				case 25:
					lining = "c";
					break;
				case 26:
					lining = "s";
					textColor = "w";
					break;
				case 28:
					backColor = "bl";
					break;
				case 29:
					backColor = textColor;
					break;
				}
			}
		}
		return myHtmlBuilder.toString();
	}

	private byte[] readPage(final String pageId) {
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
