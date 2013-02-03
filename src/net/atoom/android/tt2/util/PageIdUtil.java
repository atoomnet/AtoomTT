package net.atoom.android.tt2.util;

public class PageIdUtil {

	private final static String LINK_PREFIX = "http://foo.bar/#";

	public static String normalize(String pageId) {
		if (pageId == null || "".equals(pageId)) {
			pageId = "101-0";
		}
		pageId = pageId.replace("/", "-");
		if (pageId.indexOf("-") == -1)
			return pageId + "-0";
		return pageId;
	}

	public static String toInternalLink(final String pageId) {
		return LINK_PREFIX + pageId;
	}

	public static String fromInternalLink(final String pageUrl) {
		return pageUrl.replace(LINK_PREFIX, "");
	}
}
