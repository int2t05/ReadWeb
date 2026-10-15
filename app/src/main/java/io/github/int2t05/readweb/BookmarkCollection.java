package io.github.int2t05.readweb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/** 按添加顺序保存去重后的网页收藏。 */
public final class BookmarkCollection {
    public static final int MAX_BOOKMARKS = 100;

    private final LinkedHashMap<String, Bookmark> bookmarks = new LinkedHashMap<>();

    public boolean add(String url, String title) {
        final Bookmark bookmark;
        try {
            bookmark = Bookmark.create(url, title);
        } catch (IllegalArgumentException error) {
            return false;
        }
        if (bookmarks.containsKey(bookmark.url()) || bookmarks.size() >= MAX_BOOKMARKS) return false;
        bookmarks.put(bookmark.url(), bookmark);
        return true;
    }

    public boolean contains(String url) { return find(url) != null; }

    public Bookmark find(String url) {
        try {
            return bookmarks.get(Bookmark.create(url, null).url());
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    public boolean remove(String url) {
        Bookmark bookmark = find(url);
        return bookmark != null && bookmarks.remove(bookmark.url()) != null;
    }

    public int size() { return bookmarks.size(); }

    public List<Bookmark> all() {
        return Collections.unmodifiableList(new ArrayList<>(bookmarks.values()));
    }

    public void restore(List<Bookmark> saved) {
        bookmarks.clear();
        if (saved == null) return;
        for (Bookmark candidate : saved) {
            if (candidate != null && bookmarks.size() < MAX_BOOKMARKS) {
                add(candidate.url(), candidate.title());
            }
        }
    }
}
