import re
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

from check_device_results import ACCESSIBILITY, WORKFLOW, verify


class DeviceResultsTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)

    def write(self, cases, status=None):
        suite = ET.Element("testsuite")
        for cls, name in cases:
            case = ET.SubElement(suite, "testcase", classname=cls, name=name)
            if status:
                ET.SubElement(case, status)
        ET.ElementTree(suite).write(self.directory / "TEST-device.xml")

    def test_minimum_sdk_and_accessibility_sdk_have_different_requirements(self):
        self.write({WORKFLOW})
        self.assertEqual(1, verify(self.directory, 31))
        with self.assertRaises(ValueError):
            verify(self.directory, 35)
        self.write({WORKFLOW} | ACCESSIBILITY)
        self.assertEqual(3, verify(self.directory, 35))

    def test_empty_directory_cannot_pass(self):
        with self.assertRaises(ValueError):
            verify(self.directory, 31)

    def test_skipped_or_failed_required_test_cannot_pass(self):
        for status in ["skipped", "failure", "error"]:
            self.write({WORKFLOW}, status)
            with self.subTest(status=status), self.assertRaises(ValueError):
                verify(self.directory, 31)

    def test_duplicate_stale_reports_cannot_pass(self):
        self.write({WORKFLOW})
        (self.directory / "TEST-stale.xml").write_bytes((self.directory / "TEST-device.xml").read_bytes())
        with self.assertRaises(ValueError):
            verify(self.directory, 31)

    def test_skipped_stale_duplicate_cannot_be_hidden_by_a_passing_report(self):
        self.write({WORKFLOW}, "skipped")
        (self.directory / "TEST-stale.xml").write_bytes((self.directory / "TEST-device.xml").read_bytes())
        self.write({WORKFLOW})
        with self.assertRaisesRegex(ValueError, "Duplicate result"):
            verify(self.directory, 31)

    def test_required_names_match_the_current_instrumentation_sources(self):
        source_root = Path(__file__).resolve().parents[2] / "app/src/androidTest/java"
        for cls, name in {WORKFLOW} | ACCESSIBILITY:
            source = source_root / (cls.replace(".", "/") + ".kt")
            with self.subTest(test=f"{cls}.{name}"):
                self.assertTrue(source.is_file())
                self.assertRegex(source.read_text(), rf"@Test\s+fun\s+{re.escape(name)}\s*\(")


if __name__ == "__main__":
    unittest.main()
