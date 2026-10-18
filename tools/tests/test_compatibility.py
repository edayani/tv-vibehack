import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("compatibility", Path(__file__).parents[1] / "compatibility.py")
compat = importlib.util.module_from_spec(spec)
spec.loader.exec_module(compat)


def dump(records):
    return "Packages:\n" + "".join(f"  Package [{pkg}] (abc):\n    userId={uid}\n{body}\n"
                                      for pkg, uid, body in records)


class CompatibilityTests(unittest.TestCase):
    def test_protects_receivers_bluetooth_updates_and_unknown_oem(self):
        names = ["com.sony.airplay", "com.android.bluetooth", "com.mitv.milinkservice",
                 "com.xiaomi.mitv.smartshare", "com.vendor.updateservice", "com.vendor.mystery",
                 "com.miui.tv.analytics", "org.user.game"]
        data = dump([(pkg, index + 10000, "") for index, pkg in enumerate(names)])
        plan, _ = compat.make_plan(names, set(names), set(names[:-1]), set(), data)
        self.assertEqual([p["action"] for p in plan], ["protect"] * 6 + ["disable", "disable"])

    def test_explicit_review_cannot_override_known_connectivity(self):
        names = {"org.vendor.airplay", "org.vendor.unknown"}
        plan, _ = compat.make_plan(sorted(names), names, names, set(),
                                  dump([(pkg, i, "") for i, pkg in enumerate(sorted(names))]), reviewed=names)
        self.assertEqual([p["action"] for p in plan], ["protect", "disable"])

    def test_play_store_and_voice_assistant_remain_protected(self):
        names = {"com.android.vending", "com.google.android.katniss"}
        plan, _ = compat.make_plan(sorted(names), names, names, set(),
                                  dump([(pkg, i + 10000, "") for i, pkg in enumerate(sorted(names))]), reviewed=names)
        self.assertTrue(all(item["action"] == "protect" for item in plan))

    def test_dependency_closure_and_sensitive_permissions(self):
        rows = [("com.vendor.airplay", 1000, "    usesLibraries:\n      oem.receiver\n    requested permissions:\n      vendor.permission.RECEIVER: granted=true"),
                ("com.vendor.bridge", 1000, ""),
                ("com.vendor.library", 10001, "    libraryNames:\n      oem.receiver\n"),
                ("com.vendor.permissions", 10002, "    declared permissions:\n      vendor.permission.RECEIVER: prot=signature"),
                ("com.vendor.radio", 10003, "      android.permission.BLUETOOTH_CONNECT: granted=true")]
        names = {row[0] for row in rows}
        plan, _ = compat.make_plan(sorted(names), names, set(), set(), dump(rows))
        self.assertTrue(all(p["action"] == "protect" for p in plan))

    def test_missing_metadata_disabled_and_absent_packages(self):
        installed = {"org.user.game", "org.user.tool", "org.user.disabled"}
        plan, _ = compat.make_plan(["org.user.game", "org.user.tool", "org.user.disabled", "org.user.absent"],
                                  installed, set(), {"org.user.disabled"},
                                  dump([("org.user.game", 10000, ""), ("org.user.disabled", 10001, "")]))
        self.assertEqual([p["action"] for p in plan], ["disable", "protect", "skip", "skip"])

    def test_common_library_consumers_are_not_dependencies(self):
        data = """Packages:
  Package [com.vendor.airplay] (abc):
    appId=10001
    usesLibraries:
      android.test.base
  Package [org.user.game] (def):
    appId=10002
    usesLibraries:
      android.test.base
"""
        plan, _ = compat.make_plan(["org.user.game"], {"org.user.game", "com.vendor.airplay"}, set(), set(), data)
        self.assertEqual(plan[0]["action"], "disable")

    def test_one_line_library_provider_is_protected(self):
        rows = [("com.vendor.airplay", 10001, "    usesLibraries=[oem.one, oem.two]"),
                ("com.vendor.provider", 10002, "    libraryNames=[oem.two]")]
        names = {row[0] for row in rows}
        plan, _ = compat.make_plan(["com.vendor.provider"], names, set(), set(), dump(rows))
        self.assertEqual(plan[0]["action"], "protect")

    def test_list_comments_deduplication_and_shell_injection(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "list.txt"
            path.write_text("# hello\norg.user.app # comment\norg.user.app\n", encoding="utf-8")
            self.assertEqual(compat.read_list(path), ["org.user.app"])
            for bad in ["org.app; reboot", "org.app --user 10", "$(reboot)", "org.app\torg.other"]:
                path.write_text(bad, encoding="utf-8")
                with self.assertRaises(ValueError): compat.read_list(path)

    def test_journal_written_before_disable_and_original_state_restored(self):
        class Device:
            def __init__(self): self.commands = []; self.disabled = False
            def shell(self, command, **kwargs):
                self.commands.append(command)
                if command.startswith("dumpsys"): result = "  User 0: installed=true enabled=0\n"
                elif command.startswith("pm disable"):
                    self.disabled = True; result = "new state: disabled-user\n"
                    if not saved: raise AssertionError("journal must be saved first")
                elif command.startswith("pm list"): result = "package:org.user.app\n" if self.disabled else ""
                else: result = "ok\n"
                return result + "\n__TVHOME_STATUS:0\n"
        dev = Device(); saved = []; journal = {"changes": []}
        compat.apply_plan(dev, [{"package": "org.user.app", "action": "disable"}], journal,
                          lambda: saved.append(dict(journal["changes"][-1])))
        self.assertEqual(saved[0]["enabled"], 0)
        compat.restore(dev, journal, lambda: None)
        self.assertTrue(any(command.startswith("pm default-state --user 0 org.user.app;") for command in dev.commands))
        before = len(dev.commands)
        compat.restore(dev, journal, lambda: None)
        self.assertEqual(len(dev.commands), before)

    def test_command_failure_stops_changes(self):
        class Device:
            def shell(self, *args, **kwargs): return "SecurityException\n__TVHOME_STATUS:1\n"
        with self.assertRaises(RuntimeError): compat.checked_shell(Device(), "pm list packages")

    def test_postcheck_detects_regressions_without_changing_preexisting_disabled(self):
        journal = {"protected": {"org.vendor.airplay": "receiver", "org.vendor.bluetooth": "radio"},
                   "disabled_before": ["org.vendor.bluetooth"]}
        compat.verify_protected(journal, {"org.vendor.airplay", "org.vendor.bluetooth"}, {"org.vendor.bluetooth"})
        with self.assertRaises(RuntimeError): compat.verify_protected(journal, set(), set())
        with self.assertRaises(RuntimeError):
            compat.verify_protected(journal, {"org.vendor.airplay"}, {"org.vendor.airplay"})


if __name__ == "__main__": unittest.main()
