# The prompt

Paste this into Claude Code (or another coding assistant with shell access) on a computer that is
on the same Wi-Fi as the TV. Works on Android TV / Google TV sets (Xiaomi, TCL, Hisense, Sony,
OnePlus, Realme, Nvidia Shield and most others). It will not work on Samsung Tizen, LG webOS or
Fire TV. If you cloned this repo, mention that: the assistant can reuse `tools/` and `launcher/`
instead of writing them from scratch. Use Customize Home on the TV for layout changes and
`launcher/.../values/config.xml` for initial defaults and OEM integration.

Before you start, on the TV: Settings → Device Preferences → About → press OK on "Build" 7 times,
then go back → Developer options → turn on USB debugging (and Network/ADB debugging if your TV
shows it). Keep the TV on.

---

I want to debloat my Android TV and replace its launcher with a clean Apple TV style home screen.
My TV model is: <MODEL NAME HERE>. Work on the real device, step by step, and verify everything you
do with screenshots pulled from the TV. Ask me before anything that depends on my preferences.

Connect
- Find the TV on my network (hostname, ARP table, or ask me for the IP) and connect over ADB on port 5555.
- If the stock adb binary fails with "No route to host" on macOS even though the port is open, that is
  macOS Local Network privacy blocking adb. Don't debug it: use the Python `adb-shell` package in a venv
  as the ADB client instead. If its file push hangs on larger files, transfer files as base64 chunks
  through the shell and verify the md5.
- Tell me when the "Allow USB debugging?" prompt will appear on the TV so I can accept it.

Back up first
- Save the full package list, disabled-package list, getprop output and all settings to a backup folder
  before changing anything, and write a restore script that re-enables everything you disable.
- Install the custom launcher first, retain the stock launcher as a fallback, and verify AirPlay,
  Bluetooth remote/audio/pairing, casting, Wi-Fi, HDMI-CEC, voice input and firmware updates before debloating.

Debloat
- List installed packages and sort them into manufacturer telemetry/analytics, manufacturer content
  and ad services, optional preloaded/streaming apps, and protected connectivity/input/update services.
- Preview the proposed list through `tools/disable.sh <list>`. Preserve AirPlay, HomeKit, Bluetooth,
  Google Cast, Miracast, Wi-Fi, device discovery, HDMI-CEC, voice/remote control and firmware updates,
  including OEM services and their dependencies. Unknown system services remain enabled until reviewed.
- Use `tools/disable.sh <list> --apply` only after reviewing the report. Never bypass its protected
  services or uninstall system packages. Keep the generated journal for exact rollback. For other
  streaming/preloaded apps, ask which ones I use before including them in the proposed list.
- Keep the TV app, input/source menu, remote pairing, keyboard, Google Play Store and Play Services enabled.
- Run `pm` commands one per shell call, chained commands may silently stop after the first.

Launcher
- Don't install a third-party launcher that charges for basics like wallpapers. Build a small native
  Android TV launcher instead (Java, AndroidX RecyclerView, minSdk matching the TV, debug build is fine),
  install it over ADB and set it as the default home while keeping the stock launcher enabled. If several apps claim
  the HOME intent, set the default with `cmd package set-home-activity`.
- Design, Apple TV 4K style: pitch black background; a dock row at the bottom of the screen inside a
  slightly lighter rounded band holding the four apps I pick (ask me which, and the order); the rest of
  the apps in a 4-column grid below that scrolls up when I press Down; one translucent "glass" highlight
  that springs from tile to tile instead of per-tile borders; the focused tile scales up slightly and
  shows its name underneath; above the dock, the focused dock app's name in large light type with an
  ambient glow in a colour sampled from its banner; frosted pill buttons top right for Inputs, Wi-Fi
  (connection type, without location permission) and Control Center; a greeting, a large thin clock and the date top left in a
  downloaded open-licence typeface such as Outfit. Inputs opens a compact glass dropdown under the pill
  listing the HDMI ports, AV and Live TV, switching via the TV input framework.
- Make D-pad navigation deterministic: Up/Down move exactly one row, Up from the dock goes to the top
  bar, the ends of the top bar don't fall through to tiles. Remember the last focused tile.
- Keep it light: the TV has about 1 GB of RAM. No blur, no video, no network polling.
- Reuse Control Center and Customize Home: remote-driven dock ordering/size, hidden apps,
  grid columns/spacing, artwork and static wallpaper; changes persist without rebuilding.

Verify
- After each change, inject remote key presses over ADB, take a screenshot from the TV and look at it.
  Fix what you see before moving on.
- After debloating, run `tools/compatibility.py --check <apply-report-dir>` and perform the checklist
  in `docs/COMPATIBILITY.md`. Test actual AirPlay/casting playback, Bluetooth remote/audio/pairing,
  Wi-Fi, HDMI-CEC, voice input and firmware update access. Roll back that journal if anything regresses.
- When done, give me a restore script, a build-and-install script, and a README that explains how to
  re-enable any app and how to change the dock order or the wallpaper.

---

Expect to answer three or four checklist questions (which apps you use, which four go in the dock,
whether to keep the voice assistant, Chromecast and firmware updates). Everything is reversible.
