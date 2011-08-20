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
	private String myPageUrl;
	private String myHtmlData;
	private String myETag;

	private String myNextPageId;
	private String myNextSubPageId;
	private String myPrevPageId;
	private String myPrevSubPageId;

	private String myNextPageUrl;
	private String myNextSubPageUrl;
	private String myPrevPageUrl;
	private String myPrevSubPageUrl;

	private long myCreated;

	public PageEntity(final String pageUrl, final String htmlData, final String eTag) {
		myPageUrl = pageUrl;
		myHtmlData = htmlData;
		myETag = eTag;
		myCreated = System.currentTimeMillis();
	}

	public String getPageId() {
		return myPageId;
	}

	public void setPageId(final String pageId) {
		myPageId = pageId;
	}

	public String getPageUrl() {
		return myPageUrl;
	}

	public void setPageUrl(final String pageUrl) {
		myPageUrl = pageUrl;
	}

	public String getHtmlData() {
		return myHtmlData;
	}

	public void setHtmlData(final String htmlData) {
		myHtmlData = htmlData;
	}

	public String getETag() {
		return myETag;
	}

	public void setETag(final String eTag) {
		myETag = eTag;
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

	public String getNextPageUrl() {
		return myNextPageUrl;
	}

	public void setNextPageUrl(final String nextPageUrl) {
		myNextPageUrl = nextPageUrl;
	}

	public String getNextSubPageUrl() {
		return myNextSubPageUrl;
	}

	public void setNextSubPageUrl(final String nextSubPageUrl) {
		myNextSubPageUrl = nextSubPageUrl;
	}

	public String getPrevPageUrl() {
		return myPrevPageUrl;
	}

	public void setPrevPageUrl(final String prevPageUrl) {
		myPrevPageUrl = prevPageUrl;
	}

	public String getPrevSubPageUrl() {
		return myPrevSubPageUrl;
	}

	public void setPrevSubPageUrl(final String prevSubPageUrl) {
		myPrevSubPageUrl = prevSubPageUrl;
	}
}
