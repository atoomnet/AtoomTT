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

import net.atoom.android.tt2.util.LogBridge;

import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PageProcessor {

	private final static Pattern PATTERN_PAGELINK = Pattern
			.compile("((?<=[\\s]|\\d{3}[\\+-]|[^\\d]{1}[\\.,]|^)\\d{3}(/\\d{1,2})?(?=[\\s]|[\\+,-]\\d{3}|$))");

	private final static Pattern PATTERN_FASTTEKST = Pattern
			.compile("([^\\s]+.*)");

	public PageEntity process(final String pageId, final byte[] bytes) {

		final PageEntity pageEntity = new PageEntity(pageId);
		int videoTextIndex = processFile(pageEntity, bytes);
		if (videoTextIndex == -1)
			return null;

		processVideoText(pageEntity, bytes, videoTextIndex + 40);
		return pageEntity;
	}

	private int processFile(final PageEntity pageEntity, final byte[] bytes) {

		int index = 0;
		int mark = 0;
		for (; index < bytes.length; index++) {

			if (index < (bytes.length - 5)
					&& // detect <pre>
					bytes[index] == (byte) 60 && bytes[index + 1] == (byte) 112
					&& bytes[index + 2] == (byte) 114
					&& bytes[index + 3] == (byte) 101
					&& bytes[index + 4] == (byte) 62) {
				return index + 5; // start of videotext
			}

			if (bytes[index] != 10)
				continue;

			final String line = new String(bytes, mark, index - mark);
			mark = index + 1;
			if (line.startsWith("pn=p_"))
				pageEntity.setPrevPageId(line.replace("pn=p_", ""));
			else if (line.startsWith("pn=n_"))
				pageEntity.setNextPageId(line.replace("pn=n_", ""));
			else if (line.startsWith("pn=ps"))
				pageEntity.setPrevSubPageId(line.replace("pn=ps", ""));
			else if (line.startsWith("pn=ns"))
				pageEntity.setNextSubPageId(line.replace("pn=ns", ""));
			else if (line.startsWith("ftl="))
				pageEntity.addFastLinkPageId(line.replace("ftl=", ""));
		}
		return -1;
	}

	private final VideoTextState myVideoTextState = new VideoTextState();

	static class VideoTextState {

		final StringBuffer htmlBuilder = new StringBuffer();
		final StringBuilder divBuilder = new StringBuilder();
		final Matcher pageLinkMatcher = PATTERN_PAGELINK.matcher("");
		final Matcher fastLinkMatcher = PATTERN_FASTTEKST.matcher("");

		int rowIndex;
		int colIndex;
		boolean skipLine;
		boolean textMode;
		boolean doubleSize;
		String backColor;
		String textColor;
		String mosaicLining;
		byte holdMosaic;
		int divPosition;
		int fastLinkPosition;

		void reset() {
			htmlBuilder.setLength(0);
		}

		void nextLine() {
			divBuilder.setLength(0);
			textMode = true;
			doubleSize = false;
			skipLine = false;
			backColor = "bl";
			textColor = "w";
			mosaicLining = "c";
			holdMosaic = 0;
			divPosition = 0;
			fastLinkPosition = 0;
		}
	}

	private void processVideoText(final PageEntity pageEntity,
			final byte[] bytes, final int videoTextIndex) {

		final VideoTextState state = myVideoTextState; // reuse
		state.reset();
		for (state.rowIndex = 0; state.rowIndex < 24; state.rowIndex++) {

			state.nextLine();
			for (state.colIndex = 0; state.colIndex < 40 && !state.skipLine; state.colIndex++) {
				final int byteIndex = (state.rowIndex * 40) + state.colIndex
						+ videoTextIndex;
				checkControlsPre(state, bytes, byteIndex);
				if (state.textMode)
					processTextByte(pageEntity, bytes, byteIndex, state);
				else
					processMosaicByte(pageEntity, bytes, byteIndex, state);
				checkControlsPost(state, bytes, byteIndex);
			}
		}
		pageEntity.setHtmlData(state.htmlBuilder.toString());
	}

	private void processMosaicByte(final PageEntity pageEntity,
			final byte[] bytes, final int byteIndex, final VideoTextState state) {

		byte mosciacByte = bytes[byteIndex];
		if (mosciacByte > 64 && mosciacByte < 96) {
			state.htmlBuilder.append("<div class=\"t x" + state.divPosition
					+ " y" + state.rowIndex + " h1 w1" + " b" + state.backColor
					+ " t" + state.textColor + "\" data-m=\"" + mosciacByte
					+ "\">" + byteToString(bytes, byteIndex) + "</div>");
			state.divPosition++;
			return;
		}

		if (mosciacByte < 32)
			if (state.holdMosaic > 0)
				mosciacByte = state.holdMosaic;
			else
				mosciacByte = 32;

		int lineWidth = 1;
		if (state.mosaicLining.equals("c")
				&& (mosciacByte == 32 || mosciacByte == 35 || mosciacByte == 44
						|| mosciacByte == 47 || mosciacByte == 112
						|| mosciacByte == 124 || mosciacByte == 127)) {
			while (bytes[byteIndex + lineWidth] == mosciacByte)
				lineWidth++;
		}

		if (lineWidth > 1) {
			state.htmlBuilder.append("<div class=\"t x" + state.divPosition
					+ " y" + state.rowIndex + " h1 w" + lineWidth + " b"
					+ state.backColor + " t" + state.textColor + "\" data-m=\""
					+ mosciacByte + "\"><svg>");
			if (mosciacByte != 32)
				state.htmlBuilder.append("<use xlink:href=\"#lc" + mosciacByte
						+ "\" />");
			state.htmlBuilder.append("</svg></div>\n");

			state.colIndex += (lineWidth - 1);
			state.divPosition += lineWidth;
		} else {
			state.htmlBuilder.append("<div class=\"t x" + state.divPosition
					+ " y" + state.rowIndex + " h1 w1" + " b" + state.backColor
					+ " t" + state.textColor + "\" data-m=\"" + mosciacByte
					+ "\"><svg>");
			for (int bit = 0; bit < 5; bit++) {
				if ((mosciacByte & (1 << bit)) != 0)
					state.htmlBuilder.append("<use xlink:href=\"#p"
							+ state.mosaicLining + bit + "\" />");
			}
			if ((mosciacByte & (1 << 6)) != 0)
				state.htmlBuilder.append("<use xlink:href=\"#p"
						+ state.mosaicLining + "5\" />");
			state.htmlBuilder.append("</svg></div>\n");
			state.divPosition++;
		}
	}

	private void processTextByte(final PageEntity pageEntity,
			final byte[] bytes, final int byteIndex, final VideoTextState state) {

        final String text = byteToString(bytes, byteIndex);
        state.divBuilder.append(text);

		if ((bytes[byteIndex] < 0 || bytes[byteIndex] >= 32)
				&& state.colIndex < 39)
			return;

		final String line = state.divBuilder.toString();
		state.divBuilder.setLength(0);

		if (state.doubleSize)
			state.htmlBuilder.append("<div class=\"t1 x" + state.divPosition
					+ " y" + state.rowIndex + " h2 w" + line.length() + " b"
					+ state.backColor + " t" + state.textColor + "\" data-m=\""
					+ bytes[byteIndex] + "\">");
		else
			state.htmlBuilder.append("<div class=\"t x" + state.divPosition
					+ " y" + state.rowIndex + " h1 w" + line.length() + " b"
					+ state.backColor + " t" + state.textColor + "\" data-m=\""
					+ bytes[byteIndex] + "\">");

		if (state.rowIndex == 23) {
			state.fastLinkMatcher.reset(line);
			if (state.fastLinkMatcher.find()
					&& pageEntity.getFastLinkPageIds().size() > state.fastLinkPosition) {
				final String link = pageEntity.getFastLinkPageIds().get(
						state.fastLinkPosition++);
				state.fastLinkMatcher.appendReplacement(state.htmlBuilder,
						"<a  href=\"" + PageIdUtil.toInternalLink(link) + "\">"
								+ state.fastLinkMatcher.group(1) + "</a>");
			}
			state.fastLinkMatcher.appendTail(state.htmlBuilder);
		} else {
			state.pageLinkMatcher.reset(line);
			while (state.pageLinkMatcher.find()) {

				// exclude broken politie/ticker links
				final String link = state.pageLinkMatcher.group(1);
				if (link.startsWith("147") || link.startsWith("199"))
					continue;

				pageEntity.addLinkedPageId(link);
				state.pageLinkMatcher.appendReplacement(state.htmlBuilder,
						"<a href=\"" + PageIdUtil.toInternalLink(link) + "\">"
								+ state.pageLinkMatcher.group(1) + "</a>");
			}
			state.pageLinkMatcher.appendTail(state.htmlBuilder);
		}
		state.htmlBuilder.append("</div>\n");
		state.divPosition += line.length();
	}

	private void checkControlsPre(final VideoTextState state,
			final byte[] bytes, final int byteIndex) {
		switch (bytes[byteIndex]) {
		case 12:
			state.doubleSize = false;
			break;
		case 13:
			state.doubleSize = true;
			break;
		case 30:
			state.holdMosaic = bytes[byteIndex - 1];
			break;
		case 31:
			state.holdMosaic = 0;
			break;
		}
	}

	private void checkControlsPost(final VideoTextState state,
			final byte[] bytes, final int byteIndex) {
		switch (bytes[byteIndex]) {
		case 0:
			state.textMode = true;
			state.textColor = "bl";
			break;
		case 1:
			state.textMode = true;
			state.textColor = "r";
			break;
		case 2:
			state.textMode = true;
			state.textColor = "g";
			break;
		case 3:
			state.textMode = true;
			state.textColor = "y";
			break;
		case 4:
			state.textMode = true;
			state.textColor = "b";
			break;
		case 5:
			state.textMode = true;
			state.textColor = "m";
			break;
		case 6:
			state.textMode = true;
			state.textColor = "c";
			break;
		case 7:
			state.textMode = true;
			state.textColor = "w";
			break;
		case 16:
			state.textMode = false;
			state.textColor = "bl";
			break;
		case 17:
			state.textMode = false;
			state.textColor = "r";
			break;
		case 18:
			state.textMode = false;
			state.textColor = "g";
			break;
		case 19:
			state.textMode = false;
			state.textColor = "y";
			break;
		case 20:
			state.textMode = false;
			state.textColor = "b";
			break;
		case 21:
			state.textMode = false;
			state.textColor = "m";
			break;
		case 22:
			state.textMode = false;
			state.textColor = "c";
			break;
		case 23:
			state.textMode = false;
			state.textColor = "w";
			break;
		case 25:
			state.mosaicLining = "c";
			break;
		case 26:
			state.mosaicLining = "s";
			state.textColor = "w";
			break;
		case 28:
			state.backColor = "bl";
			break;
		case 29:
			state.backColor = state.textColor;
			break;
		}
	}

	private String byteToString(final byte[] bytes, int byteIndex) {
        if(bytes[byteIndex] > 32 || bytes[byteIndex] < 0){
            try {
                return new String(bytes, byteIndex, 1, "ISO-8859-1");
            } catch (UnsupportedEncodingException e){
                LogBridge.w("Unsupported encoding....");
            }
        }
        return " ";
	}
}
