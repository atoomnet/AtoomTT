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


public final class PageEntity {

	private String myPageId;
	private String myHtmlData;

	private String myNextPageId;
	private String myNextSubPageId;
	private String myPrevPageId;
	private String myPrevSubPageId;

	private long myCreated;

	public PageEntity(final String pageId) {
		myPageId = pageId;
		myCreated = System.currentTimeMillis();
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

	public long getCreated() {
		return myCreated;
	}

	public void setCreated(final long created) {
		myCreated = created;
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
}
