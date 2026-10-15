package io.github.int2t05.readweb;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;

/** 将有限数量的收藏保存到应用私有偏好存储。 */
public final class BookmarkStore {
    private static final String PREFS = "readweb_bookmarks";
    private static final String KEY_BOOKMARKS = "items";
    private static final int MAX_SERIALIZED_CHARACTERS = 512 * 1024;

    private final SharedPreferences preferences;

    public BookmarkStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public BookmarkCollection load() {
        BookmarkCollection bookmarks = new BookmarkCollection();
        String raw = preferences.getString(KEY_BOOKMARKS, null);
        if (raw == null) return bookmarks;
        if (raw.length() > MAX_SERIALIZED_CHARACTERS) {
            preferences.edit().remove(KEY_BOOKMARKS).apply();
            return bookmarks;
        }
        try {
            JSONArray records = new JSONArray(raw);
            ArrayList<Bookmark> restored = new ArrayList<>();
            for (int i = 0; i < records.length() && restored.size() < BookmarkCollection.MAX_BOOKMARKS; i++) {
                JSONObject item = records.optJSONObject(i);
                if (item == null) continue;
                try {
                    restored.add(Bookmark.create(item.optString("url", ""), item.optString("title", "")));
                } catch (IllegalArgumentException ignored) {
                    // 忽略不再符合当前地址策略的收藏项。
                }
            }
            bookmarks.restore(restored);
        } catch (JSONException ignored) {
            preferences.edit().remove(KEY_BOOKMARKS).apply();
        }
        return bookmarks;
    }

    public void save(BookmarkCollection bookmarks) {
        JSONArray records = new JSONArray();
        for (Bookmark bookmark : bookmarks.all()) {
            JSONObject item = new JSONObject();
            try {
                item.put("url", bookmark.url());
                item.put("title", bookmark.title());
            } catch (JSONException impossible) {
                throw new IllegalStateException("Could not save bookmark.", impossible);
            }
            records.put(item);
        }
        preferences.edit().putString(KEY_BOOKMARKS, records.toString()).apply();
    }
}
