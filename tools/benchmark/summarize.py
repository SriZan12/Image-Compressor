#!/usr/bin/env python3
"""Summarize one validated device report; timing targets alone never approve a release."""

import argparse
import json
import math
import statistics
import sys
from pathlib import Path

from run import PROFILE_CASES, expected_identities, valid_report


def summarize(report):
    if not isinstance(report, dict):
        raise ValueError("Report must be an object")
    profile, iterations = report.get("profile"), report.get("iterations")
    if profile not in PROFILE_CASES or type(iterations) is not int or not 1 <= iterations <= 100:
        raise ValueError("Unknown profile or invalid iteration count")
    identities = expected_identities(profile, iterations)
    if (not isinstance(report.get("runId"), str) or not report["runId"]
            or not isinstance(report.get("host"), dict)
            or report["host"].get("instrumentationSucceeded") is not True
            or not valid_report(report, report["runId"], len(identities), 0, "OK (1 test)\n", identities)):
        raise ValueError("Incomplete, duplicate, unexpected, or unconfirmed measurements")
    groups = {}
    findings = []
    for row in report["results"]:
        for key in ("elapsedMs", "sampledPeakPssKiB"):
            value = row.get(key)
            if type(value) not in (int, float) or not math.isfinite(value) or value <= 0:
                raise ValueError(f"Invalid {key} in {row['id']}")
        groups.setdefault(row["id"], []).append(row)
        if row.get("targetMet") is False:
            findings.append(f"{row['id']} iteration {row['iteration']}: target not met")
        quality = row.get("quality", {})
        if not isinstance(quality, dict):
            raise ValueError(f"Invalid quality data in {row['id']}")
        if quality.get("orientationPixelsCorrect") is False:
            findings.append(f"{row['id']} iteration {row['iteration']}: orientation mismatch")
    cases = {}
    for name, rows in sorted(groups.items()):
        times = sorted(row["elapsedMs"] for row in rows)
        cases[name] = {
            "samples": len(times), "medianMs": statistics.median(times),
            "p95Ms": times[math.ceil(.95 * len(times)) - 1], "maxMs": max(times),
            "maxSampledPssMiB": max(row["sampledPeakPssKiB"] for row in rows) / 1024,
        }
    device = report.get("device", {})
    if profile != "performance":
        status = "diagnostic_profile"
    elif not isinstance(device, dict) or device.get("isEmulator") is not False:
        status = "physical_device_required"
    elif iterations < 20:
        status = "insufficient_samples"
    elif findings:
        status = "correctness_findings"
    elif any(row.get("status") != "encoded" for row in report["results"]):
        status = "missing_encoded_outputs"
    elif any(row.get("targetBytes") != 1_000_000 or row.get("targetMet") is not True
             or type(row.get("outputBytes")) is not int or not 0 < row["outputBytes"] <= 1_000_000
             for row in groups["large-12mp-target-1mb"]):
        status = "target_not_verified"
    else:
        balanced, target = cases["large-12mp-jpeg"], cases["large-12mp-target-1mb"]
        status = "met" if balanced["medianMs"] <= 2000 and balanced["p95Ms"] <= 5000 and target["p95Ms"] <= 8000 else "exceeded"
    return {
        "runId": report["runId"], "profile": profile, "cases": cases,
        "findings": findings, "timingTargets": status, "releaseApproved": False,
        "limits": ["Debug build; generated 12MP input; one small warmup; nearest-rank p95.",
                   "Sampled PSS can miss peaks and cannot pass the 256 MiB peak-memory gate.",
                   "Full-resolution quality, physical device matrix, release build and accessibility checks remain required."],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("--require-timing-targets", action="store_true",
                        help="Exit nonzero unless 20+ physical-device performance samples meet timing targets")
    args = parser.parse_args()
    result = summarize(json.loads(args.report.read_text()))
    print(json.dumps(result, indent=2, allow_nan=False))
    return 1 if args.require_timing_targets and result["timingTargets"] != "met" else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, OSError, TypeError) as error:
        print(f"Cannot summarize baseline: {error}", file=sys.stderr)
        sys.exit(1)
