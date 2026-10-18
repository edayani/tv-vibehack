package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.provider.Settings;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Foreground-only snapshots. System settings own pairing, routing and receiver controls. */
final class ControlCenter {
    private final Activity activity;
    private final Runnable inputs;
    private final Runnable customize;

    ControlCenter(Activity activity, Runnable inputs, Runnable customize) {
        this.activity = activity;
        this.inputs = inputs;
        this.customize = customize;
    }

    void show() {
        CharSequence[] rows = {
                activity.getString(R.string.network_row, network()),
                activity.getText(R.string.bluetooth_row), activity.getText(R.string.audio_row),
                activity.getText(R.string.airplay_row), activity.getText(R.string.cast_row),
                activity.getText(R.string.hdmi_row), activity.getText(R.string.customize_home),
                activity.getText(R.string.system_settings)};
        AlertDialog panel = new AlertDialog.Builder(activity).setTitle(R.string.control_center).setItems(rows, (dialog, which) -> {
            switch (which) {
                case 0: openSettings(activity, new Intent(Settings.ACTION_WIFI_SETTINGS)); break;
                case 1: bluetooth(); break;
                case 2: audio(); break;
                case 3: receivers(true); break;
                case 4: receivers(false); break;
                case 5: inputs.run(); break;
                case 6: customize.run(); break;
                case 7: openSettings(activity, new Intent(Settings.ACTION_SETTINGS)); break;
            }
        }).setNegativeButton(android.R.string.cancel, null).create();
        panel.setOnShowListener(dialog -> {
            android.view.Window window = panel.getWindow();
            if (window != null) {
                window.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
                int width = Math.min((int) (460 * activity.getResources().getDisplayMetrics().density),
                        activity.getResources().getDisplayMetrics().widthPixels * 3 / 4);
                window.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        panel.show();
    }

    private String network() {
        try {
            ConnectivityManager manager = activity.getSystemService(ConnectivityManager.class);
            NetworkCapabilities caps = manager == null ? null : manager.getNetworkCapabilities(manager.getActiveNetwork());
            if (caps == null) return activity.getString(R.string.offline);
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return activity.getString(R.string.ethernet);
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return activity.getString(R.string.wifi);
        } catch (SecurityException ignored) { }
        return activity.getString(R.string.status_unknown);
    }

    private void bluetooth() {
        StringBuilder text = new StringBuilder();
        try {
            BluetoothManager manager = activity.getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null) text.append(activity.getString(R.string.bluetooth_unavailable));
            else if (!adapter.isEnabled()) text.append(activity.getString(R.string.bluetooth_off));
            else {
                text.append(activity.getString(R.string.paired_devices));
                for (BluetoothDevice device : adapter.getBondedDevices()) {
                    String name = device.getName();
                    text.append("\n• ").append(name == null ? activity.getString(R.string.bluetooth_device) : name);
                }
                text.append("\n\n").append(activity.getString(R.string.paired_not_connected));
            }
        } catch (SecurityException ex) {
            text.setLength(0);
            text.append(activity.getString(R.string.bluetooth_settings_status));
        }
        details(R.string.bluetooth_row, text.toString(), new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
    }

    private void audio() {
        StringBuilder text = new StringBuilder(activity.getString(R.string.audio_available));
        try {
            AudioManager manager = activity.getSystemService(AudioManager.class);
            if (manager != null) {
                for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                    text.append("\n• ").append(device.getProductName());
                }
            }
        } catch (SecurityException ignored) { }
        text.append("\n\n").append(activity.getString(R.string.audio_routing_note));
        details(R.string.audio_row, text.toString(), new Intent(Settings.ACTION_SOUND_SETTINGS));
    }

    private void receivers(boolean airplay) {
        List<ApplicationInfo> detected = new ArrayList<>();
        List<String> configured = Arrays.asList(activity.getResources().getStringArray(
                airplay ? R.array.airplay_packages : R.array.cast_packages));
        for (ApplicationInfo app : activity.getPackageManager().getInstalledApplications(0)) {
            String pkg = app.packageName.toLowerCase(Locale.ROOT);
            boolean match = airplay ? pkg.matches(".*(airplay|airpin|airscreen|homekit|happycast).*")
                    : pkg.matches(".*(chromecast|miracast|mediashell|milink|smartshare).*");
            if (match || configured.contains(app.packageName)) detected.add(app);
        }
        StringBuilder text = new StringBuilder();
        if (detected.isEmpty()) text.append(activity.getString(R.string.receiver_unknown));
        for (ApplicationInfo app : detected) {
            text.append(activity.getPackageManager().getApplicationLabel(app)).append(" — ")
                    .append(activity.getString(app.enabled ? R.string.app_enabled : R.string.app_disabled)).append('\n');
        }
        text.append("\n").append(activity.getString(R.string.receiver_status_note));
        Intent target = new Intent(airplay ? Settings.ACTION_SETTINGS : Settings.ACTION_CAST_SETTINGS);
        for (ApplicationInfo app : detected) {
            if (!app.enabled) continue;
            Intent launch = activity.getPackageManager().getLeanbackLaunchIntentForPackage(app.packageName);
            if (launch != null) { target = launch; break; }
        }
        details(airplay ? R.string.airplay_row : R.string.cast_row, text.toString(), target);
    }

    private void details(int title, String text, Intent settings) {
        new AlertDialog.Builder(activity).setTitle(title).setMessage(text)
                .setPositiveButton(R.string.open_settings, (dialog, which) -> openSettings(activity, settings))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    static void openSettings(Activity activity, Intent preferred) {
        try {
            activity.startActivity(preferred);
        } catch (ActivityNotFoundException | SecurityException ex) {
            try { activity.startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (ActivityNotFoundException | SecurityException unavailable) {
                Toast.makeText(activity, R.string.settings_unavailable, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
