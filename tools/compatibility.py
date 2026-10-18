#!/usr/bin/env python3
"""Conservative debloating with a device inventory, protected services and rollback journal.

No package-name heuristic can prove that an OEM service is safe to disable. Unknown system
packages therefore stay enabled unless explicitly reviewed; known connectivity services and
their visible shared-UID / library / permission dependencies cannot be overridden.
"""
import argparse
import datetime
import json
import re
import shlex
from pathlib import Path

PACKAGE = re.compile(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+\Z")
CONNECTIVITY = re.compile(
    r"airplay|airpin|airscreen|homekit|happycast|cast|miracast|milink|smartshare|"
    r"bluetooth|(?:^|\.)bt(?:\.|$)|wifi|wlan|connectivity|networkstack|"
    r"remote|cec|hdmi|tvinput|tvcenter|inputmethod|voice|hotword|updat|ota(?:\.|$)", re.I)
CORE = {"android", "com.android.systemui", "com.android.settings", "com.android.providers.settings",
        "com.google.android.gms", "com.google.android.gsf", "com.android.tv.settings",
        "com.google.android.tv.frameworkpackagestubs", "com.google.android.apps.mediashell",
        "com.android.vending", "com.google.android.katniss", "com.gathin.tvhome"}
# Small reviewed list, not a blanket exemption for manufacturer packages.
REVIEWED = {"com.miui.tv.analytics", "com.xiaomi.statistic", "com.xm.webcontent",
            "com.xiaomi.floatingframe", "com.google.android.tvrecommendations",
            "com.google.android.leanbacklauncher.recommendations"}
SENSITIVE = re.compile(
    r"android\.permission\.(?:BLUETOOTH\w*|CHANGE_WIFI_STATE|NETWORK_SETTINGS|"
    r"NETWORK_STACK|MAINLINE_NETWORK_STACK|TV_INPUT_HARDWARE|HDMI_CEC|"
    r"MODIFY_AUDIO_ROUTING|BIND_TV_INPUT|BIND_VOICE_INTERACTION|REBOOT|RECOVERY)\b")


def read_list(path):
    result = []
    for number, raw in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        value = raw.split("#", 1)[0].strip()
        if not value:
            continue
        if not PACKAGE.fullmatch(value):
            raise ValueError(f"{path}:{number}: expected one package name")
        if value not in result:
            result.append(value)
    return result


def packages(output):
    return set(re.findall(r"^package:([\w.]+)\s*$", output, re.M))


def metadata(dump):
    """Read the Packages section, not the preceding resolver tables."""
    records = {}
    for match in re.finditer(r"^\s{2}Package \[([^\]]+)\].*?\n(.*?)(?=^\s{2}Package \[|\Z)", dump, re.M | re.S):
        pkg, body = match.groups()
        uid = re.search(r"^\s+(?:sharedUserId|userId|appId)=(\S+)", body, re.M)
        uses = set(re.findall(r"\b([\w.]+\.permission\.[\w.]+)\b", body))
        declared = set(re.findall(r"^\s+([\w.]+): prot=", body, re.M))
        # Android prints both one-line and indented library lists depending on release.
        def libraries(names):
            result = set()
            for raw in re.findall(r"^\s+(?:" + names + r")=\[([^\]]+)\]", body, re.M):
                result.update(re.split(r"[,\s]+", raw.strip()))
            for section in re.finditer(r"^\s+(?:" + names + r"):[ \t]*\n((?:[ \t]{6,}[^\n]+\n)*)", body, re.M):
                result.update(section.group(1).split())
            return result
        records[pkg] = {"body": body, "uid": uid.group(1) if uid else None,
                        "uses": uses, "declared": declared, "provided_libs": libraries("libraryNames"),
                        "used_libs": libraries("usesLibraries|usesOptionalLibraries")}
    return records


def make_plan(candidates, installed, system, disabled, dump, extra=(), reviewed=()):
    info = metadata(dump)
    protected = {}
    for pkg in installed:
        body = info.get(pkg, {}).get("body", "")
        if pkg in CORE or CONNECTIVITY.search(pkg) or SENSITIVE.search(body):
            protected[pkg] = "connectivity, input, update or core service"
        elif pkg in extra:
            protected[pkg] = "explicitly protected by owner"
    # Propagate relationships visible in dumpsys. Undocumented OEM dependencies may still exist.
    changed = True
    while changed:
        changed = False
        for pkg, data in info.items():
            if pkg not in installed or pkg in protected:
                continue
            for other in list(protected):
                parent = info.get(other, {})
                same_uid = data["uid"] and data["uid"] == parent.get("uid")
                shared_library = data["provided_libs"] & parent.get("used_libs", set())
                permission_provider = data["declared"] & parent.get("uses", set())
                if same_uid or shared_library or permission_provider:
                    protected[pkg] = f"visible dependency of {other}"
                    changed = True
                    break
    plan = []
    for pkg in candidates:
        if pkg not in installed:
            status, reason = "skip", "not installed for user 0"
        elif pkg in protected:
            status, reason = "protect", protected[pkg]
        elif pkg in disabled:
            status, reason = "skip", "already disabled; rollback will leave it disabled"
        elif pkg not in info:
            status, reason = "protect", "package metadata unavailable"
        elif pkg in system and pkg not in REVIEWED | set(reviewed):
            status, reason = "protect", "unreviewed system/vendor package"
        else:
            status, reason = "disable", "reviewed candidate" if pkg in system else "user-installed candidate"
        plan.append({"package": pkg, "action": status, "reason": reason})
    return plan, protected


def checked_shell(dev, command):
    output = dev.shell(command + "; echo __TVHOME_STATUS:$?", read_timeout_s=120.0)
    match = re.search(r"\n?__TVHOME_STATUS:(\d+)\s*$", output)
    if not match or match.group(1) != "0":
        raise RuntimeError(f"Command failed: {command}\n{output}")
    return output[:match.start()]


def inventory(dev):
    installed = packages(checked_shell(dev, "pm list packages --user 0"))
    system = packages(checked_shell(dev, "pm list packages -s --user 0"))
    disabled = packages(checked_shell(dev, "pm list packages -d --user 0"))
    dump = checked_shell(dev, "dumpsys package")
    if not installed or not system or not metadata(dump):
        raise RuntimeError("Incomplete package inventory; refusing to change the TV")
    return installed, system, disabled, dump


def apply_plan(dev, plan, journal, save):
    for item in plan:
        if item["action"] != "disable":
            continue
        pkg = item["package"]
        # Journal the original setting BEFORE the command, including the interrupted-command case.
        state = checked_shell(dev, "dumpsys package " + shlex.quote(pkg))
        match = re.search(r"User 0:.*?enabled=(\d+)", state)
        if not match or match.group(1) not in {"0", "1"}:
            raise RuntimeError(f"Cannot establish original enabled state for {pkg}; stopping")
        journal["changes"].append({"package": pkg, "enabled": int(match.group(1)), "restored": False})
        save()
        checked_shell(dev, "pm disable-user --user 0 " + shlex.quote(pkg))
        if pkg not in packages(checked_shell(dev, "pm list packages -d --user 0")):
            raise RuntimeError(f"{pkg} did not become disabled; stopping")


def restore(dev, journal, save):
    # Validate the entire journal before applying any entries.
    for item in journal["changes"]:
        if not PACKAGE.fullmatch(item["package"]) or item["enabled"] not in (0, 1):
            raise ValueError("Invalid rollback journal")
    for item in reversed(journal["changes"]):
        if item.get("restored"):
            continue
        pkg = item["package"]
        verb = "default-state" if item["enabled"] == 0 else "enable"
        checked_shell(dev, f"pm {verb} --user 0 {shlex.quote(pkg)}")
        output = checked_shell(dev, "dumpsys package " + shlex.quote(pkg))
        match = re.search(r"User 0:.*?enabled=(\d+)", output)
        if not match or int(match.group(1)) != item["enabled"]:
            raise RuntimeError(f"Could not verify restored state for {pkg}; stopping")
        item["restored"] = True
        save()


def verify_protected(journal, installed, disabled):
    previously_enabled = set(journal["protected"]) - set(journal["disabled_before"])
    broken = previously_enabled & (set(disabled) | (set(journal["protected"]) - set(installed)))
    if broken:
        raise RuntimeError("Protected packages missing/disabled: " + ", ".join(sorted(broken)))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("list", nargs="?", help="debloat list; default action is preview only")
    parser.add_argument("--apply", action="store_true", help="apply eligible packages after saving inventory")
    parser.add_argument("--protect", help="additional package list to preserve (OEM dependencies)")
    parser.add_argument("--reviewed-system", help="list of system packages you have independently reviewed")
    parser.add_argument("--output", default="backup/compatibility", help="parent for timestamped reports")
    parser.add_argument("--restore", metavar="REPORT_DIR", help="restore only changes in this journal")
    parser.add_argument("--check", metavar="REPORT_DIR", help="compare protected packages after changes")
    args = parser.parse_args()
    if sum((args.apply, bool(args.restore), bool(args.check))) > 1:
        parser.error("choose one of --apply, --restore or --check")
    if not (args.restore or args.check or args.list):
        parser.error("a debloat list is required")
    candidates = read_list(args.list) if args.list else []
    extra = read_list(args.protect) if args.protect else []
    reviewed = read_list(args.reviewed_system) if args.reviewed_system else []
    # Import only for device operations, so policy tests need no ADB dependency or TV_HOST.
    from tv import connect
    dev = connect()
    try:
        identity = checked_shell(dev, "getprop ro.serialno").strip()
        if not identity:
            raise RuntimeError("Device serial unavailable; cannot bind rollback to this TV")
        if args.restore or args.check:
            folder = Path(args.restore or args.check)
            journal = json.loads((folder / "journal.json").read_text(encoding="utf-8"))
            if journal["serial"] != identity:
                raise RuntimeError("Report belongs to a different TV; refusing to continue")
            if args.restore:
                restore(dev, journal, lambda: write_json(folder / "journal.json", journal))
                print("Restored original package states from", folder)
            else:
                installed, _, disabled, _ = inventory(dev)
                verify_protected(journal, installed, disabled)
                print("Protected package states preserved. Now perform the real-device checklist in docs/COMPATIBILITY.md.")
            return
        installed, system, disabled, dump = inventory(dev)
        plan, protected = make_plan(candidates, installed, system, disabled, dump, extra, reviewed)
        folder = Path(args.output) / datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
        folder.mkdir(parents=True, exist_ok=False)
        (folder / "packages.txt").write_text(dump, encoding="utf-8")
        (folder / "device.txt").write_text(checked_shell(dev, "getprop"), encoding="utf-8")
        journal = {"serial": identity, "disabled_before": sorted(disabled), "protected": protected,
                   "plan": plan, "changes": []}
        write_json(folder / "journal.json", journal)
        for item in plan:
            print(f'{item["action"].upper():8} {item["package"]}: {item["reason"]}')
        print("Report / rollback journal:", folder)
        if args.apply:
            try:
                apply_plan(dev, plan, journal, lambda: write_json(folder / "journal.json", journal))
                after_installed, _, after_disabled, _ = inventory(dev)
                verify_protected(journal, after_installed, after_disabled)
            except Exception:
                print("Apply/check failed. Restore this run with: compatibility.py --restore", folder)
                raise
            print("Applied; protected package states verified. Now perform the real-TV feature checklist.")
        else:
            print("Preview only. Run again with --apply to make these changes.")
    finally:
        dev.close()


def write_json(path, data):
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


if __name__ == "__main__":
    main()
