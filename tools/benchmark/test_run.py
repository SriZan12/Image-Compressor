"""Ensure a device/runner failure cannot be mistaken for a completed baseline."""

import copy
import unittest

from run import valid_report


class ReportValidationTest(unittest.TestCase):
    def setUp(self):
        self.report = {
            "schemaVersion": 1, "runId": "current", "complete": True,
            "expectedMeasurements": 2, "unexpectedFailures": [],
            "results": [
                {"id": "target", "iteration": 1, "unexpectedFailure": False,
                 "sourceUnchanged": True, "status": "encoded", "decodable": True,
                 "metadataMatches": True, "targetMet": False},
                {"id": "broken", "iteration": 1, "unexpectedFailure": False,
                 "sourceUnchanged": True, "status": "expected_rejection", "expectedRejection": True},
            ],
        }

    def validate(self, report=None, output="OK (1 test)\n", code=0):
        return valid_report(self.report if report is None else report, "current", 2, code, output)

    def test_completed_characterization_keeps_quality_findings(self):
        self.assertTrue(self.validate())  # An unmet target is recorded, never hidden as production acceptance.

    def test_adb_zero_exit_does_not_make_process_crash_or_zero_tests_pass(self):
        for output in ["INSTRUMENTATION_RESULT: shortMsg=Process crashed.\nINSTRUMENTATION_CODE: 0\n",
                       "OK (0 tests)\n", "FAILURES!!!\n"]:
            with self.subTest(output=output):
                self.assertFalse(self.validate(output=output))
        self.assertFalse(self.validate(code=1))

    def test_stale_incomplete_or_empty_report_fails(self):
        self.assertFalse(self.validate({}))
        for change in [{"runId": "old"}, {"complete": False}, {"results": []},
                       {"schemaVersion": 2}, {"expectedMeasurements": 0}]:
            with self.subTest(change=change):
                self.assertFalse(self.validate(self.report | change))

    def test_duplicate_cases_or_unexpected_errors_fail(self):
        duplicate = self.report | {"results": [self.report["results"][0]] * 2}
        self.assertFalse(self.validate(duplicate))
        self.assertFalse(self.validate(self.report | {"unexpectedFailures": ["target"]}))
        for key in ["sourceUnchanged", "decodable", "metadataMatches"]:
            report = copy.deepcopy(self.report)
            report["results"][0][key] = False
            with self.subTest(key=key):
                self.assertFalse(self.validate(report))


if __name__ == "__main__":
    unittest.main()
