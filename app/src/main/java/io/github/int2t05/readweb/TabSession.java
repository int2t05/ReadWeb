package io.github.int2t05.readweb;

public final class TabSession {
    private final long id;
    private String url;
    private String title;
    private int scrollY;
    private boolean markdownReader;

    TabSession(long id, String url, String title, int scrollY, boolean markdownReader) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.scrollY = Math.max(0, scrollY);
        this.markdownReader = markdownReader;
    }

    public long id() { return id; }
    public String url() { return url; }
    public String title() { return title; }
    public int scrollY() { return scrollY; }
    public boolean isMarkdownReader() { return markdownReader; }

    void updateUrl(String value) { url = value == null ? "" : value; }
    void updateTitle(String value) {
        if (value == null || value.trim().isEmpty()) {
            title = url;
        } else {
            title = value.length() <= 200 ? value : value.substring(0, 200);
        }
    }
    void updateScrollY(int value) { scrollY = Math.max(0, value); }
    void setMarkdownReader(boolean value) { markdownReader = value; }

    static TabSession restore(long id, String url, String title, int scrollY, boolean markdownReader) {
        return new TabSession(id, url, title, scrollY, markdownReader);
    }
}
