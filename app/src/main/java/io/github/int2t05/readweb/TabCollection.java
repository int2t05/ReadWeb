package io.github.int2t05.readweb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class TabCollection {
    public static final int MAX_TABS = 10;
    private static final AtomicLong NEXT_ID = new AtomicLong(1);
    private final ArrayList<TabSession> tabs = new ArrayList<>();
    private long activeId = -1;

    public TabSession newTab(String url) {
        if (tabs.size() >= MAX_TABS) throw new IllegalStateException("The tab limit has been reached.");
        TabSession tab = new TabSession(NEXT_ID.getAndIncrement(), url == null ? "" : url,
                url == null || url.trim().isEmpty() ? "新标签" : url, 0, false);
        tabs.add(tab);
        activeId = tab.id();
        return tab;
    }

    public TabSession active() { return find(activeId); }
    public long activeId() { return activeId; }
    public int size() { return tabs.size(); }
    public List<TabSession> all() { return Collections.unmodifiableList(new ArrayList<>(tabs)); }
    public TabSession find(long id) {
        for (TabSession tab : tabs) if (tab.id() == id) return tab;
        return null;
    }

    public boolean select(long id) {
        if (find(id) == null) return false;
        activeId = id;
        return true;
    }

    public boolean close(long id) {
        int index = -1;
        for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).id() == id) { index = i; break; }
        if (index < 0) return false;
        tabs.remove(index);
        if (activeId == id) {
            activeId = tabs.isEmpty() ? -1 : tabs.get(Math.min(index, tabs.size() - 1)).id();
        }
        return true;
    }

    public void restore(List<TabSession> savedTabs, long savedActiveId) {
        tabs.clear();
        if (savedTabs != null) {
            for (TabSession tab : savedTabs) {
                if (tab != null && tabs.size() < MAX_TABS && find(tab.id()) == null) {
                    tabs.add(tab);
                    NEXT_ID.accumulateAndGet(tab.id() + 1, Math::max);
                }
            }
        }
        activeId = find(savedActiveId) == null ? (tabs.isEmpty() ? -1 : tabs.get(0).id()) : savedActiveId;
    }
}
