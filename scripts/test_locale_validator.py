"""Regression checks for optional Android translations and required format safety."""
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("validate-locale-resources.ps1").resolve()


class LocaleValidatorTest(unittest.TestCase):
    def run_validator(self, translated=None, strict=False):
        with tempfile.TemporaryDirectory(prefix="opencloud-locale-test-") as temporary:
            root = Path(temporary)
            resources = root / "app/src/main/res"
            defaults = resources / "values"
            defaults.mkdir(parents=True)
            (defaults / "strings.xml").write_text(
                '<resources><string name="count">Count %1$d</string>'
                '<string name="new_label">New label</string></resources>', encoding="utf-8")
            if translated is not None:
                target = resources / "values-de"
                target.mkdir()
                (target / "strings.xml").write_text(translated, encoding="utf-8")
            command = ["pwsh", "-NoProfile", "-File", str(SCRIPT), "-Repository", str(root)]
            if strict:
                command.append("-RequireComplete")
            return subprocess.run(command, capture_output=True, text=True).returncode

    def test_missing_locale_uses_defaults(self):
        self.assertEqual(0, self.run_validator())

    def test_partial_locale_uses_defaults(self):
        self.assertEqual(0, self.run_validator('<resources><string name="count">Anzahl %1$d</string></resources>'))

    def test_strict_completeness_is_opt_in(self):
        self.assertNotEqual(0, self.run_validator(strict=True))

    def test_wrong_format_type_is_rejected(self):
        self.assertNotEqual(0, self.run_validator('<resources><string name="count">Anzahl %1$s</string></resources>'))

    def test_malformed_xml_is_rejected(self):
        self.assertNotEqual(0, self.run_validator('<resources><string>'))


if __name__ == "__main__":
    unittest.main()
