package io.github.int2t05.readweb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TabCollectionTest {
    @Test
    public void createsSelectsAndClosesTabsWithoutLosingSelection() {
        TabCollection tabs = new TabCollection();
        TabSession first = tabs.newTab("https://example.com/1");
        TabSession second = tabs.newTab("https://example.com/2");

        assertEquals(second.id(), tabs.active().id());
        assertTrue(tabs.select(first.id()));
        assertEquals(first.id(), tabs.active().id());
        assertTrue(tabs.close(first.id()));
        assertEquals(second.id(), tabs.active().id());
        assertTrue(tabs.close(second.id()));
        assertNull(tabs.active());
    }

    @Test
    public void enforcesTheLogicalTabLimit() {
        TabCollection tabs = new TabCollection();
        for (int i = 0; i < TabCollection.MAX_TABS; i++) {
            tabs.newTab("https://example.com/" + i);
        }

        assertEquals(TabCollection.MAX_TABS, tabs.size());
        assertThrows(IllegalStateException.class, () -> tabs.newTab("https://example.com/overflow"));
    }

    @Test
    public void rejectsSelectionAndCloseForUnknownIds() {
        TabCollection tabs = new TabCollection();

        assertFalse(tabs.select(44));
        assertFalse(tabs.close(44));
    }
}
