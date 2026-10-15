package io.github.int2t05.readweb;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;

/** 仅在应用私有偏好存储中保存当前标签和选中项。 */
public final class SessionStore {
    private static final String PREFS = "readweb_session";
    private static final String KEY_TABS = "tabs";
    private static final String KEY_ACTIVE = "active_tab";
    private final SharedPreferences preferences;

    public SessionStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public TabCollection load() {
        TabCollection tabs = new TabCollection();
        String raw = preferences.getString(KEY_TABS, null);
        if (raw == null) return tabs;
        try {
            JSONArray saved = new JSONArray(raw);
            ArrayList<TabSession> restored = new ArrayList<>();
            for (int i = 0; i < saved.length() && restored.size() < TabCollection.MAX_TABS; i++) {
                JSONObject item = saved.optJSONObject(i);
                if (item == null) continue;
                long id = item.optLong("id", -1);
                String url = item.optString("url", "");
                if (id < 1) continue;
                if (!url.trim().isEmpty()) {
                    try {
                        url = UrlPolicy.normalize(url).toString();
                    } catch (IllegalArgumentException error) {
                        continue;
                    }
                }
                restored.add(TabSession.restore(id, url, item.optString("title", url),
                        item.optInt("scrollY", 0), item.optBoolean("markdown", false)));
            }
            tabs.restore(restored, preferences.getLong(KEY_ACTIVE, -1));
        } catch (JSONException ignored) {
            preferences.edit().remove(KEY_TABS).remove(KEY_ACTIVE).apply();
        }
        return tabs;
    }

    public void save(TabCollection tabs) {
        JSONArray records = new JSONArray();
        for (TabSession tab : tabs.all()) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", tab.id());
                item.put("url", tab.url());
                item.put("title", tab.title());
                item.put("scrollY", tab.scrollY());
                item.put("markdown", tab.isMarkdownReader());
            } catch (JSONException impossible) {
                throw new IllegalStateException("Could not save tab session.", impossible);
            }
            records.put(item);
        }
        preferences.edit().putString(KEY_TABS, records.toString()).putLong(KEY_ACTIVE, tabs.activeId()).apply();
    }
}
