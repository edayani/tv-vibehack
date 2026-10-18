package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Native dialogs remain usable with only D-pad, OK and Back; no drag gestures required. */
final class CustomizeHome {
    static final int PICK_WALLPAPER = 42;
    private final Activity activity;
    private final LauncherPreferences preferences;
    private final AppAdapter adapter;
    private final Runnable changed;

    CustomizeHome(Activity activity, LauncherPreferences preferences, AppAdapter adapter, Runnable changed) {
        this.activity = activity;
        this.preferences = preferences;
        this.adapter = adapter;
        this.changed = changed;
    }

    void show() {
        new AlertDialog.Builder(activity).setTitle(R.string.customize_home)
                .setItems(R.array.customize_options, (dialog, which) -> {
                    switch (which) {
                        case 0: chooseApp(); break;
                        case 1: number(R.string.dock_size, "dock_slots", 2, 6, preferences.dockSlots()); break;
                        case 2: hiddenApps(); break;
                        case 3: number(R.string.grid_columns, "columns", 3, 5, preferences.columns()); break;
                        case 4: spacing(); break;
                        case 5: new AlertDialog.Builder(activity).setTitle(R.string.artwork)
                                .setSingleChoiceItems(R.array.artwork_options, preferences.icons() ? 1 : 0, (d, w) -> {
                                    preferences.setIcons(w == 1); d.dismiss(); changed.run();
                                }).setNegativeButton(android.R.string.cancel, null).show(); break;
                        case 6: wallpaper(); break;
                        case 7: new AlertDialog.Builder(activity).setTitle(R.string.reset_layout)
                                .setMessage(R.string.reset_layout_message)
                                .setPositiveButton(R.string.reset_layout, (d, w) -> { preferences.reset(); changed.run(); })
                                .setNegativeButton(android.R.string.cancel, null).show(); break;
                    }
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    void appOptions(AppAdapter.Entry entry) {
        List<String> dock = preferences.dock();
        boolean pinned = dock.contains(entry.pkg);
        CharSequence[] labels = {activity.getText(pinned ? R.string.unpin : R.string.pin),
                activity.getText(R.string.move_earlier), activity.getText(R.string.move_later),
                activity.getText(R.string.hide_app), activity.getText(R.string.app_info)};
        new AlertDialog.Builder(activity).setTitle(entry.label).setItems(labels, (dialog, which) -> {
            int index = dock.indexOf(entry.pkg);
            if (which == 0) {
                if (pinned) { dock.remove(entry.pkg); preferences.setDock(dock); changed.run(); }
                else pin(entry);
            } else if (which == 1 || which == 2) {
                int target = index + (which == 1 ? -1 : 1);
                if (index >= 0 && target >= 0 && target < dock.size()) {
                    Collections.swap(dock, index, target); preferences.setDock(dock); changed.run();
                }
            } else if (which == 3) {
                Set<String> hidden = preferences.hidden(); hidden.add(entry.pkg);
                preferences.setHidden(hidden); changed.run();
            } else if (which == 4) {
                ControlCenter.openSettings(activity, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + entry.pkg)));
            }
        }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void chooseApp() {
        List<AppAdapter.Entry> entries = adapter.catalog();
        CharSequence[] names = new CharSequence[entries.size()];
        for (int i = 0; i < entries.size(); i++) names[i] = entries.get(i).label;
        new AlertDialog.Builder(activity).setTitle(R.string.dock_apps_title).setItems(names,
                (dialog, which) -> appOptions(entries.get(which)))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void pin(AppAdapter.Entry entry) {
        // Drop uninstalled/hidden pins so a removed app never silently consumes a dock slot.
        List<String> dock = new ArrayList<>();
        Set<String> hidden = preferences.hidden();
        for (String pkg : preferences.dock()) {
            for (AppAdapter.Entry known : adapter.catalog()) {
                if (known.pkg.equals(pkg) && !hidden.contains(pkg) && dock.size() < preferences.dockSlots()) dock.add(pkg);
            }
        }
        if (dock.size() < preferences.dockSlots()) {
            dock.add(entry.pkg); preferences.setDock(dock);
            hidden.remove(entry.pkg); preferences.setHidden(hidden); changed.run();
        } else {
            CharSequence[] labels = new CharSequence[dock.size()];
            for (int i = 0; i < dock.size(); i++) {
                labels[i] = dock.get(i);
                for (AppAdapter.Entry known : adapter.catalog()) if (known.pkg.equals(dock.get(i))) labels[i] = known.label;
            }
            new AlertDialog.Builder(activity).setTitle(R.string.replace_dock_app).setItems(labels, (dialog, which) -> {
                dock.set(which, entry.pkg); preferences.setDock(dock);
                hidden.remove(entry.pkg); preferences.setHidden(hidden); changed.run();
            }).setNegativeButton(android.R.string.cancel, null).show();
        }
    }

    private void hiddenApps() {
        List<AppAdapter.Entry> entries = adapter.catalog();
        Set<String> hidden = preferences.hidden();
        CharSequence[] names = new CharSequence[entries.size()];
        boolean[] checked = new boolean[entries.size()];
        for (int i = 0; i < entries.size(); i++) { names[i] = entries.get(i).label; checked[i] = hidden.contains(entries.get(i).pkg); }
        new AlertDialog.Builder(activity).setTitle(R.string.hidden_apps_title)
                .setMultiChoiceItems(names, checked, (dialog, which, value) -> {
                    if (value) hidden.add(entries.get(which).pkg); else hidden.remove(entries.get(which).pkg);
                }).setPositiveButton(android.R.string.ok, (d, w) -> { preferences.setHidden(hidden); changed.run(); })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void number(int title, String key, int min, int max, int current) {
        CharSequence[] labels = new CharSequence[max - min + 1];
        for (int i = 0; i < labels.length; i++) labels[i] = String.valueOf(i + min);
        new AlertDialog.Builder(activity).setTitle(title).setSingleChoiceItems(labels, current - min, (d, w) -> {
            preferences.setInt(key, w + min); d.dismiss(); changed.run();
        }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void spacing() {
        new AlertDialog.Builder(activity).setTitle(R.string.tile_spacing)
                .setSingleChoiceItems(R.array.spacing_options, preferences.spacing() == 4 ? 0 : preferences.spacing() == 8 ? 1 : 2,
                        (d, w) -> { preferences.setInt("spacing", new int[]{4, 8, 16}[w]); d.dismiss(); changed.run(); })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void wallpaper() {
        new AlertDialog.Builder(activity).setTitle(R.string.wallpaper).setItems(R.array.wallpaper_options, (dialog, which) -> {
            if (which < 4) {
                preferences.setInt("background", new int[]{0xFF000000, 0xFF101B2C, 0xFF142820, 0xFF26182E}[which]);
                preferences.setWallpaper(""); changed.run();
            } else {
                try {
                    activity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                            .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), PICK_WALLPAPER);
                } catch (android.content.ActivityNotFoundException ex) {
                    android.widget.Toast.makeText(activity, R.string.no_file_picker, android.widget.Toast.LENGTH_LONG).show();
                }
            }
        }).setNegativeButton(android.R.string.cancel, null).show();
    }
}
