package io.github.int2t05.readweb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import org.junit.Test;

public final class BookmarkCollectionTest {
    @Test
    public void normalizesUrlsAndUsesTheAddressAsTheBookmarkIdentity() {
        BookmarkCollection bookmarks = new BookmarkCollection();

        assertTrue(bookmarks.add(" HTTPS://Example.com ", "Reading"));
        assertFalse(bookmarks.add("https://example.com/", "Duplicate"));
        assertEquals("https://example.com/", bookmarks.find("https://EXAMPLE.com").url());
        assertEquals("Reading", bookmarks.find("https://example.com/").title());
    }

    @Test
    public void rejectsUnsupportedAddressesAndKeepsTitlesBounded() {
        BookmarkCollection bookmarks = new BookmarkCollection();

        assertFalse(bookmarks.add("javascript:alert(1)", "unsafe"));
        assertFalse(bookmarks.add("https://example.com/" + "x".repeat(Bookmark.MAX_URL_LENGTH), "long address"));
        assertTrue(bookmarks.add("https://example.com", "x".repeat(Bookmark.MAX_TITLE_LENGTH + 20)));
        assertEquals(Bookmark.MAX_TITLE_LENGTH, bookmarks.all().get(0).title().length());
    }

    @Test
    public void enforcesLimitAndRestoresOnlyUniqueValidEntries() {
        BookmarkCollection bookmarks = new BookmarkCollection();
        for (int i = 0; i < BookmarkCollection.MAX_BOOKMARKS; i++) {
            assertTrue(bookmarks.add("https://example.com/" + i, "Page " + i));
        }

        assertFalse(bookmarks.add("https://example.com/overflow", "Overflow"));
        assertTrue(bookmarks.remove("https://example.com/0"));
        assertEquals(BookmarkCollection.MAX_BOOKMARKS - 1, bookmarks.size());

        BookmarkCollection restored = new BookmarkCollection();
        ArrayList<Bookmark> saved = new ArrayList<>(bookmarks.all());
        saved.add(bookmarks.all().get(0));
        restored.restore(saved);
        assertEquals(bookmarks.size(), restored.size());
        assertEquals("https://example.com/1", restored.all().get(0).url());
        assertNull(restored.find("file:///private.md"));
    }
}
