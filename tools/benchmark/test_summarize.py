"""Do not turn incomplete, emulated, or undersampled measurements into acceptance."""

import tempfile
import unittest
from pathlib import Path

from run import expected_identities, source_tree_hash, valid_report
from summarize import summarize


def report(iterations=20):
    rows = []
    for case, iteration in sorted(expected_identities("performance", iterations)):
        rows.append({"id": case, "iteration": iteration, "elapsedMs": 1000 + iteration,
                     "sampledPeakPssKiB": 128 * 1024, "sourceUnchanged": True,
                     "unexpectedFailure": False, "status": "encoded", "decodable": True,
                     "metadataMatches": True, "outputBytes": 750_000,
                     "targetBytes": 1_000_000 if "target" in case else None,
                     "targetMet": True if "target" in case else None})
    return {"schemaVersion": 1, "runId": "new", "profile": "performance", "iterations": iterations,
            "expectedMeasurements": len(rows), "complete": True, "unexpectedFailures": [],
            "host": {"instrumentationSucceeded": True}, "device": {"isEmulator": False}, "results": rows}


class SummaryTest(unittest.TestCase):
    def test_exact_median_nearest_rank_p95_and_sampled_memory(self):
        result = summarize(report())
        stats = result["cases"]["large-12mp-jpeg"]
        self.assertEqual((1010.5, 1019, 1020, 128),
                         (stats["medianMs"], stats["p95Ms"], stats["maxMs"], stats["maxSampledPssMiB"]))
        self.assertEqual("met", result["timingTargets"])
        self.assertFalse(result["releaseApproved"])

    def test_emulator_unknown_hardware_and_few_samples_cannot_pass(self):
        for device in [{"isEmulator": True}, {}, {"isEmulator": "false"}]:
            self.assertEqual("physical_device_required", summarize(report() | {"device": device})["timingTargets"])
        self.assertEqual("insufficient_samples", summarize(report(19))["timingTargets"])

    def test_slow_tail_fails_even_when_median_passes(self):
        data = report()
        for row in data["results"]:
            if row["id"] == "large-12mp-jpeg" and row["iteration"] >= 19:
                row["elapsedMs"] = 6000
        self.assertEqual("exceeded", summarize(data)["timingTargets"])

    def test_unmet_target_or_orientation_is_not_timing_acceptance(self):
        for change in [{"targetMet": False}, {"quality": {"orientationPixelsCorrect": False}}]:
            data = report()
            data["results"][0].update(change)
            self.assertEqual("correctness_findings", summarize(data)["timingTargets"])

    def test_target_must_be_measured_not_assumed(self):
        for change in [{"targetMet": None}, {"outputBytes": 1_000_001}, {"outputBytes": None}]:
            data = report()
            for row in data["results"]:
                if "target" in row["id"]:
                    row.update(change)
            self.assertEqual("target_not_verified", summarize(data)["timingTargets"])

    def test_missing_duplicate_wrong_case_and_wrong_iteration_fail(self):
        for mutation in [lambda rows: rows.pop(), lambda rows: rows.__setitem__(0, rows[1]),
                         lambda rows: rows[0].update(id="invented"),
                         lambda rows: rows[0].update(iteration=99),
                         lambda rows: rows[0].update(iteration=True)]:
            data = report()
            mutation(data["results"])
            with self.assertRaises(ValueError):
                summarize(data)

    def test_invalid_metrics_fail_instead_of_appearing_fast(self):
        for value in [0, -1, True, None, "1", float("nan"), float("inf")]:
            for key in ["elapsedMs", "sampledPeakPssKiB"]:
                data = report()
                data["results"][0][key] = value
                with self.subTest(value=value, key=key), self.assertRaises(ValueError):
                    summarize(data)

    def test_runner_must_have_confirmed_completion(self):
        for change in [{"host": {}}, {"complete": False}, {"unexpectedFailures": ["crash"]}]:
            with self.assertRaises(ValueError):
                summarize(report() | change)

    def test_known_case_set_is_checked_even_if_counts_match(self):
        data = report(1)
        self.assertTrue(valid_report(data, "new", 2, 0, "OK (1 test)\n", expected_identities("performance", 1)))
        data["results"][0]["id"] = "wrong"
        self.assertFalse(valid_report(data, "new", 2, 0, "OK (1 test)\n", expected_identities("performance", 1)))

    def test_source_identity_includes_policy_content_and_paths(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "Engine.kt").write_text("engine")
            policy = root / "Policy.kt"
            policy.write_text("old")
            before = source_tree_hash(root)
            policy.write_text("new")
            self.assertNotEqual(before, source_tree_hash(root))
            before = source_tree_hash(root)
            policy.rename(root / "Renamed.kt")
            self.assertNotEqual(before, source_tree_hash(root))


if __name__ == "__main__":
    unittest.main()
