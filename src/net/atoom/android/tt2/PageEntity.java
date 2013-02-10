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

import java.util.LinkedList;
import java.util.List;

public final class PageEntity {

	private final static long CACHE_TIME_LONG = 300000;
	private final static long CACHE_TIME_SHORT = 60000;

	private String myPageId;
	private String myHtmlData;

	private String myNextPageId;
	private String myNextSubPageId;
	private String myPrevPageId;
	private String myPrevSubPageId;

	private List<String> myFastLinkPageIds = new LinkedList<String>();
	private List<String> myLinkPageIds = new LinkedList<String>();

	private long myExpires;

	public PageEntity(final String pageId) {
		myPageId = pageId;
		if (pageId.startsWith("8"))
			myExpires = System.currentTimeMillis() + CACHE_TIME_SHORT;
		else
			myExpires = System.currentTimeMillis() + CACHE_TIME_LONG;
	}

	public String getPageId() {
		return myPageId;
	}

	public void setPageId(final String pageId) {
		myPageId = pageId;
	}

	public String getHtmlData() {
		return myHtmlData;
	}

	public void setHtmlData(final String htmlData) {
		myHtmlData = htmlData;
	}

	public long getExpires() {
		return myExpires;
	}

	public void setExpires(final long expires) {
		myExpires = expires;
	}

	public String getNextPageId() {
		return myNextPageId;
	}

	public void setNextPageId(String nextPageId) {
		myNextPageId = nextPageId;
	}

	public String getNextSubPageId() {
		return myNextSubPageId;
	}

	public void setNextSubPageId(String nextSubPageId) {
		myNextSubPageId = nextSubPageId;
	}

	public String getPrevPageId() {
		return myPrevPageId;
	}

	public void setPrevPageId(String prevPageId) {
		myPrevPageId = prevPageId;
	}

	public String getPrevSubPageId() {
		return myPrevSubPageId;
	}

	public void setPrevSubPageId(String prevSubPageId) {
		myPrevSubPageId = prevSubPageId;
	}

	public List<String> getFastLinkPageIds() {
		return myFastLinkPageIds;
	}

	public void addFastLinkPageId(String fastPageId) {
		myFastLinkPageIds.add(fastPageId);
	}

	public List<String> getLinkedPageIds() {
		return myLinkPageIds;
	}

	public void addLinkedPageId(String linkedPageId) {
		myLinkPageIds.add(linkedPageId);
	}
}
