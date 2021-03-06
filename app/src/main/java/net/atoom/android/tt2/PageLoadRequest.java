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

class PageLoadRequest implements Comparable<PageLoadRequest> {

	private final String myPageId;
	private final long myTimestamp;
	private final boolean myPreload;
	private final PageLoadPriority myPageLoadPriority;
	private final PageLoadCompletionHandler myPageLoadCompletionHandler;

	public PageLoadRequest(String pageId, PageLoadPriority pageLoadPriority,
			PageLoadCompletionHandler pageLoadCompletionHandler) {
		this(pageId, pageLoadPriority, pageLoadCompletionHandler, true);
	}

	public PageLoadRequest(String pageId, PageLoadPriority pageLoadPriority,
			PageLoadCompletionHandler pageLoadCompletionHandler, boolean preload) {
		myPageId = pageId;
		myTimestamp = System.currentTimeMillis();
		myPreload = preload;
		myPageLoadPriority = pageLoadPriority;
		myPageLoadCompletionHandler = pageLoadCompletionHandler;
	}

	public String getPageId() {
		return myPageId;
	}

	public PageLoadPriority getPageLoadPriority() {
		return myPageLoadPriority;
	}

	public long getTimestamp() {
		return myTimestamp;
	}
	
	public boolean isPreload() {
		return myPreload;
	}

	public PageLoadCompletionHandler getPageLoadCompletionHandler() {
		return myPageLoadCompletionHandler;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("PageLoadRequest{");
		sb.append(getPageLoadPriority());
		sb.append(",");
		sb.append(getPageId());
		sb.append(",");
		sb.append(getTimestamp());
		sb.append("}");
		return sb.toString();
	}

	@Override
	public int compareTo(PageLoadRequest other) {
		if (getPageLoadPriority() == PageLoadPriority.HIGH) {
			if (other.getPageLoadPriority() == PageLoadPriority.LOW) {
				return -1;
			}
			return (int) (getTimestamp() - other.getTimestamp());
		}
		if (other.getPageLoadPriority() == PageLoadPriority.HIGH) {
			return 1;
		}
		return (int) (getTimestamp() - other.getTimestamp());
	}
}