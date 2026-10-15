package io.github.int2t05.readweb;

/** 表示一条字段长度受限、仅保存在本机的网页收藏。 */
public final class Bookmark {
    public static final int MAX_URL_LENGTH = 2048;
    public static final int MAX_TITLE_LENGTH = 200;

    private final String url;
    private final String title;

    private Bookmark(String url, String title) {
        this.url = url;
        this.title = title;
    }

    public static Bookmark create(String value, String label) {
        String url = UrlPolicy.normalize(value).toString();
        if (url.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("Bookmark address is too long.");
        }
        String title = label == null || label.trim().isEmpty() ? url : label.trim();
        if (title.length() > MAX_TITLE_LENGTH) {
            int end = MAX_TITLE_LENGTH;
            if (Character.isHighSurrogate(title.charAt(end - 1))) end--;
            title = title.substring(0, end);
        }
        return new Bookmark(url, title);
    }

    public String url() { return url; }
    public String title() { return title; }
}
