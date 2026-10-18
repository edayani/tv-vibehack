# Preserving native TV features

Install the launcher first and test the TV before debloating. A launcher cannot supply AirPlay
on a TV without an AirPlay receiver, or prove that an OEM receiver is functioning. This tool
reduces risk; it does not certify compatibility with every firmware.

## Preview and apply

From the repository root, after setting up `tools/venv` and `TV_HOST`:

```bash
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt --apply
```

The first command is read-only. Each run creates a timestamped directory under
`backup/compatibility/`, containing the package dump, firmware properties and `journal.json`.
Backups contain device identifiers; keep them private. No package is uninstalled.

On Windows, use a Python environment with `tools/requirements.txt` installed:

```powershell
$env:TV_HOST = "192.168.1.50"
python tools/compatibility.py examples/xiaomi-mi-tv-4a/debloat.txt
python tools/compatibility.py examples/xiaomi-mi-tv-4a/debloat.txt --apply
```

The PowerShell wrapper `tools/disable.ps1 -TvHost <ip> <list>` also previews by default; pass
`-Apply` to apply eligible packages. `-Protect`, `-ReviewedSystem` and `-Output` mirror the Python
options. Both installation scripts retain the stock launcher and do not grant location access.

The policy preserves core services, recognized AirPlay/HomeKit/casting/Bluetooth/Wi-Fi/input/
HDMI/voice/update packages and packages requesting sensitive connectivity permissions. It
propagates protection through visible shared UIDs, Java libraries and custom permission
providers. Unknown system/vendor packages stay enabled. Existing disabled packages are skipped.
Missing package metadata or a failed inventory prevents mutation.

Package names and Android's dumps do not expose all native libraries, provider relationships,
dynamic dependencies or OEM integrations. Add model-specific dependencies to a package list and
pass `--protect my-protected-packages.txt`. Independently reviewed system candidates can be
listed with `--reviewed-system my-reviewed-packages.txt`; this does **not** override recognized
connectivity protection or its visible dependencies. Re-read the preview on every firmware.

## Verify and roll back

Use the report directory printed by the **apply** run:

```bash
tools/venv/bin/python tools/compatibility.py --check backup/compatibility/<timestamp>
tools/venv/bin/python tools/compatibility.py --restore backup/compatibility/<timestamp>
```

Rollback restores only packages attempted by that run, including their original default versus
explicitly enabled setting, for user 0. A journal is saved before each disable and after each
verified restore, so it can resume after interruption. Reports are bound to the TV's serial.
If an apply fails, stop and restore that report before making further changes.

`tools/restore.sh <list> [stock-launcher]` remains a **legacy full reset**: it enables all listed
packages, restores the stock HOME and resets animation scales. It is not exact journal rollback.
The launcher installer keeps the stock launcher enabled as a fallback.

Apply automatically verifies protected package presence/enabled state; `--check` repeats that
verification later. It cannot test radio
hardware, network discovery, playback, pairing or HDMI-CEC. Before and after applying changes:

- Use the Bluetooth remote, including Home, Back, volume and voice search.
- Connect Bluetooth headphones/speakers and play audio; try pairing a device in native settings.
- From an Apple device on the same network, discover the TV and play AirPlay audio/video; check
  HomeKit if supported.
- Cast from a supported Google Cast app and test Miracast if used.
- Reconnect Wi-Fi/Ethernet, switch HDMI inputs and check HDMI-CEC power/volume controls.
- Open the firmware-update screen, a streaming app and the on-screen keyboard.

## Control Center and customization

Select **Control Center** on the home screen or press the remote's Menu key. It offers network,
paired Bluetooth devices (where Android permits access), available audio devices, receiver app
state, native settings, HDMI/TV inputs and **Customize Home**. A paired device is not necessarily
connected; an available audio output is not necessarily the active route. Receiver app enabled
state is not a live AirPlay/casting health indicator. Unsupported/restricted settings actions fall
back to general system settings. No Bluetooth scanning or system-wide audio routing is attempted.

Customize Home supports independent 2–6 dock slots and 3–5 grid columns, pin/unpin and reorder,
hide/unhide, spacing, banner/icon artwork, four solid backgrounds and a local static image via
the TV's document picker. Long-press OK on a tile for its options, or choose it from **Dock apps
& order**. When full, pinning asks which dock app to replace. Hidden apps remain available in
the customization list; this is presentation only, not access control. All-hidden layouts retain
access to Control Center. Layout settings survive restarts and APK updates; Reset layout restores
resource defaults. Folder grouping and third-party icon-pack integration are not included.

Wallpapers are downsampled off the UI thread to at most 1280×720 pixels before display. Some TVs
have no document picker; solid backgrounds still work. The launcher adds no background service,
network permission, discovery scan or location permission. Network status shows the connection
type instead of the SSID. Clock updates run while Home is visible; the network label reacts to
Android network callbacks.

## Developer checks

```bash
python -m unittest discover -s tools/tests -v
mkdir -p /tmp/tvhome-tests
javac -d /tmp/tvhome-tests launcher/app/src/main/java/com/gathin/tvhome/HomeLayout.java launcher/tests/HomeLayoutTest.java
java -cp /tmp/tvhome-tests com.gathin.tvhome.HomeLayoutTest
cd launcher && ./gradlew assembleDebug lintDebug
```

The policy suite uses fake device responses, including failure and rollback paths. The layout
suite exhausts dock sizes, grid widths and partial-row edges without Android dependencies.
Real-TV verification remains required. The existing target SDK 29 is retained for this sideloaded
project; lint reports the Play publication requirement as a warning. A future target-SDK upgrade
must also review Bluetooth permissions and package visibility on actual TV firmware.
