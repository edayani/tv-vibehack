package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Resource defaults on first launch; all on-TV edits survive restarts and APK updates. */
final class LauncherPreferences {
    private final Context context;
    private final SharedPreferences prefs;

    LauncherPreferences(Context context) {
        this.context = context;
        prefs = context.getSharedPreferences("home_layout", Context.MODE_PRIVATE);
    }

    List<String> dock() {
        String raw = prefs.getString("dock", null);
        if (raw != null) {
            try {
                JSONArray array = new JSONArray(raw);
                List<String> result = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    String pkg = array.getString(i);
                    if (!result.contains(pkg)) result.add(pkg);
                }
                return result;
            } catch (JSONException ignored) { /* Recover defaults from a malformed preference. */ }
        }
        return new ArrayList<>(Arrays.asList(context.getResources().getStringArray(R.array.dock_apps)));
    }

    void setDock(List<String> packages) {
        prefs.edit().putString("dock", new JSONArray(packages).toString()).apply();
    }

    Set<String> hidden() {
        return new HashSet<>(prefs.getStringSet("hidden", new HashSet<>(Arrays.asList(
                context.getResources().getStringArray(R.array.hidden_apps)))));
    }

    void setHidden(Set<String> hidden) { prefs.edit().putStringSet("hidden", new HashSet<>(hidden)).apply(); }
    int columns() { return clamp(prefs.getInt("columns", context.getResources().getInteger(R.integer.columns)), 3, 5); }
    int dockSlots() { return clamp(prefs.getInt("dock_slots", context.getResources().getInteger(R.integer.columns)), 2, 6); }
    int spacing() { return clamp(prefs.getInt("spacing", 8), 4, 16); }
    int background() { return prefs.getInt("background", 0xFF000000); }
    boolean icons() { return prefs.getBoolean("icons", false); }
    String wallpaper() { return prefs.getString("wallpaper", ""); }
    void setInt(String key, int value) { prefs.edit().putInt(key, value).apply(); }
    void setIcons(boolean icons) { prefs.edit().putBoolean("icons", icons).apply(); }
    void setWallpaper(String uri) {
        String previous = wallpaper();
        if (!previous.isEmpty() && !previous.equals(uri)) {
            try { context.getContentResolver().releasePersistableUriPermission(android.net.Uri.parse(previous),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException ignored) { }
        }
        prefs.edit().putString("wallpaper", uri).apply();
    }
    void reset() { setWallpaper(""); prefs.edit().clear().apply(); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
