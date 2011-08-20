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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.atoom.android.tt2.util.LogBridge;

public final class PageProcessor {

	private static final Pattern PATTERN_PAGEID = Pattern.compile("http://teletekst.nos.nl/tekst/(.*)\\.html/*");
	private static final Pattern PATTERN_TTDATA = Pattern.compile("<pre>(.*)</pre>");

	private static final Pattern PATTERN_FTDATA = Pattern.compile("</pre>.*(<table.*?</table>)");
	private static final Pattern PATTERN_NEXTPAGEID = Pattern.compile("HREF=\"(.*)\\.html.*<!--TB_NEXT-->");
	private static final Pattern PATTERN_NEXTSUBPAGEID = Pattern.compile("HREF=\"(.*)\\.html.*<!--TB_NEXT_SUB-->");
	private static final Pattern PATTERN_PREVPAGEID = Pattern.compile("HREF=\"(.*)\\.html.*<!--TB_PREV-->");
	private static final Pattern PATTERN_PREVSUBPAGEID = Pattern.compile("HREF=\"(.*)\\.html.*<!--TB_PREV_SUB-->");

	private static final String PAGEURL_PID = "[pid]";
	private static final String PAGEURL_TMPL = "http://teletekst.nos.nl/tekst/" + PAGEURL_PID + ".html";
	private static final String PAGEURL_BASE = "http://teletekst.nos.nl/tekst/";

	private static final String CONTENT_HREF_START = "href=\"";
	private static final String CONTENT_EOL_CODE = "\n";
	private static final String CONTENT_EOL_MARKER = "EOL";
	private static final String CONTENT_EOL_HTML = "<br>";
	private static final String CONTENT_PRE_START = "<pre>";
	private static final String CONTENT_PRE_END = "</pre>";

	public String processRawPage(final String pageHtml) {

		String fastTekstData = pageHtml.replaceAll(CONTENT_EOL_CODE, CONTENT_EOL_MARKER);
		Matcher fastTekstMatcher = PATTERN_FTDATA.matcher(fastTekstData);
		if (fastTekstMatcher.find()) {
			if (LogBridge.isLoggable())
				LogBridge.i("Fasttekst block found");
			fastTekstData = fastTekstMatcher.group(1);
			fastTekstData = fastTekstData.replaceAll(CONTENT_HREF_START, CONTENT_HREF_START + PAGEURL_BASE);
			fastTekstData = fastTekstData.replaceAll(CONTENT_EOL_MARKER, CONTENT_EOL_CODE);
		} else {
			if (LogBridge.isLoggable())
				LogBridge.w("Fasttekst not found");
		}

		String newspageData = pageHtml.replaceAll(CONTENT_EOL_CODE, CONTENT_EOL_HTML);
		Matcher newsPageMatcher = PATTERN_TTDATA.matcher(newspageData);
		if (newsPageMatcher.find()) {
			if (LogBridge.isLoggable())
				LogBridge.i("Newspagedata block found");
			newspageData = newsPageMatcher.group(1);
			newspageData = newspageData.replaceAll(CONTENT_HREF_START, CONTENT_HREF_START + PAGEURL_BASE);
		} else {
			if (LogBridge.isLoggable())
				LogBridge.w("Newspagedata not found");
		}

		return CONTENT_PRE_START + newspageData + CONTENT_PRE_END + fastTekstData;
	}

	public String pageIdFromUrl(final String pageUrl) {
		Matcher pageIdMatcher = PATTERN_PAGEID.matcher(pageUrl);
		if (pageIdMatcher.find()) {
			return pageIdMatcher.group(1);
		}
		return "";
	}

	public String pageUrlFromId(final String pageId) {
		return PAGEURL_TMPL.replace(PAGEURL_PID, pageId);
	}

	public String nextPageIdFromData(final String htmlData) {
		Matcher nextPageIdMatcher = PATTERN_NEXTPAGEID.matcher(htmlData);
		if (nextPageIdMatcher.find()) {
			String result = nextPageIdMatcher.group(1);
			if (LogBridge.isLoggable())
				LogBridge.i(" nextPageId\t: " + result);
			return result;
		}
		if (LogBridge.isLoggable())
			LogBridge.i(" nextPageId\t: not found");
		return "";
	}

	public String nextSubPageIdFromData(final String htmlData) {
		Matcher nextSubPageIdMatcher = PATTERN_NEXTSUBPAGEID.matcher(htmlData);
		if (nextSubPageIdMatcher.find()) {
			String result = nextSubPageIdMatcher.group(1);
			if (LogBridge.isLoggable())
				LogBridge.i(" nextSubPageId\t: " + result);
			return result;
		}
		if (LogBridge.isLoggable())
			LogBridge.i(" nextSubPageId : not found");
		return "";
	}

	public String prevPageIdFromData(final String htmlData) {
		Matcher prevPageIdMatcher = PATTERN_PREVPAGEID.matcher(htmlData);
		if (prevPageIdMatcher.find()) {
			String result = prevPageIdMatcher.group(1);
			if (LogBridge.isLoggable())
				LogBridge.i(" prevPageId\t: " + result);
			return result;
		}
		if (LogBridge.isLoggable())
			LogBridge.i(" prevPageId\t: not found");
		return "";
	}

	public String prevSubPageIdFromData(final String htmlData) {
		Matcher prevSubPageIdMatcher = PATTERN_PREVSUBPAGEID.matcher(htmlData);
		if (prevSubPageIdMatcher.find()) {
			String result = prevSubPageIdMatcher.group(1);
			if (LogBridge.isLoggable())
				LogBridge.i(" prevSubPageId\t: " + result);
			return result;
		}
		if (LogBridge.isLoggable())
			LogBridge.i(" prevSubPageId : not found");
		return "";
	}
}
