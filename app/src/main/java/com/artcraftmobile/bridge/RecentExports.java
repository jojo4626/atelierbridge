package com.artcraftmobile.bridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class RecentExports {
    private static final String PREFS = "artcraft_exports_v1";
    private static final String KEY = "recent";
    private static final int LIMIT = 25;

    static final class Entry {
        final String uri, name, mime, appName;
        final long timestamp;
        Entry(String uri, String name, String mime, String appName, long timestamp) {
            this.uri = uri; this.name = name; this.mime = mime;
            this.appName = appName; this.timestamp = timestamp;
        }
    }

    static synchronized List<Entry> list(Context context) {
        List<Entry> entries = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"));
            for (int i = 0; i < Math.min(data.length(), LIMIT); i++) {
                JSONObject o = data.optJSONObject(i);
                if (o == null) continue;
                String uri = o.optString("uri", "");
                if (!"content".equals(Uri.parse(uri).getScheme())) continue;
                entries.add(new Entry(uri, o.optString("name", "creation"),
                    o.optString("mime", "application/octet-stream"),
                    o.optString("app", "ArtCraft"), o.optLong("time", 0)));
            }
        } catch (Exception ignored) {  }
        return entries;
    }

    static synchronized void record(Context context, Uri uri, String name, String mime, String app) {
        if (uri == null || !"content".equals(uri.getScheme())) return;
        try {
            JSONArray array = new JSONArray();
            array.put(entryToJson(uri.toString(), name, mime, app));
            for (Entry e : list(context)) {
                if (!e.uri.equals(uri.toString()) && array.length() < LIMIT)
                    array.put(entryToJson(e.uri, e.name, e.mime, e.appName, e.timestamp));
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply();
        } catch (Exception ignored) { }
    }

    private static JSONObject entryToJson(String uri, String name, String mime, String app) throws Exception {
        return entryToJson(uri, name, mime, app, System.currentTimeMillis());
    }
    private static JSONObject entryToJson(String uri, String name, String mime, String app, long time) throws Exception {
        JSONObject o = new JSONObject();
        o.put("uri", uri); o.put("name", name); o.put("mime", mime);
        o.put("app", app); o.put("time", time);
        return o;
    }

    static synchronized void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }
    private RecentExports() { }
}
