#!/usr/bin/env python3
"""Run the Android compression baseline and retrieve a validated, uniquely named report."""

import argparse
import hashlib
import json
import re
import subprocess
import sys
import time
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = "com.srizan12.shrink"
TEST = APP + ".test/androidx.test.runner.AndroidJUnitRunner"
CLASS = "com.example.imagecompressor.benchmark.CompressionBaselineTest"
SMOKE_CASES = {
    "photo-jpeg-q80", "photo-webp-q80", "chart-png", "alpha-png", "alpha-jpeg-white",
    "photo-target-50kib", "noise-target-10kib", "impossible-png-target", "corrupt-input",
    "truncated-input", *(f"exif-{i}" for i in range(1, 9)),
}
PROFILE_CASES = {
    "smoke": SMOKE_CASES,
    "full": SMOKE_CASES | {"large-12mp-jpeg", "large-48mp-jpeg"},
    "performance": {"large-12mp-jpeg", "large-12mp-target-1mb"},
}
MEASUREMENTS_PER_ITERATION = {name: len(cases) for name, cases in PROFILE_CASES.items()}


def valid_report(report, run_id, expected, returncode, runner_output, expected_identities=None):
    """Reject stale, empty, incomplete, duplicate, and failed runs independently of ADB's exit code."""
    rows = report.get("results")
    if not (expected > 0 and returncode == 0
            and re.search(r"^OK \(1 test\)\s*$", runner_output, re.MULTILINE)
            and report.get("schemaVersion") == 1 and report.get("complete") is True
            and report.get("runId") == run_id
            and report.get("expectedMeasurements") == expected
            and isinstance(rows, list) and len(rows) == expected
            and report.get("unexpectedFailures") == []):
        return False
    identities = set()
    for row in rows:
        if not isinstance(row, dict) or not isinstance(row.get("id"), str) or type(row.get("iteration")) is not int:
            return False
        identity = (row["id"], row["iteration"])
        if identity in identities or row.get("unexpectedFailure") is not False or row.get("sourceUnchanged") is not True:
            return False
        identities.add(identity)
        if row.get("status") == "encoded":
            if row.get("decodable") is not True or row.get("metadataMatches") is not True:
                return False
        elif row.get("status") != "expected_rejection" or row.get("expectedRejection") is not True:
            return False
    return expected_identities is None or identities == expected_identities


def expected_identities(profile, iterations):
    return {(case, iteration) for case in PROFILE_CASES[profile] for iteration in range(1, iterations + 1)}


def source_tree_hash(root):
    """Include policy/search/models/resources, not just the engine entry point."""
    digest = hashlib.sha256()
    for path in sorted(root.rglob("*")):
        if path.is_file():
            content = path.read_bytes()
            digest.update(path.relative_to(root).as_posix().encode() + b"\0")
            digest.update(len(content).to_bytes(8, "big"))
            digest.update(content)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Explicitly select a test device; never clears its data")
    parser.add_argument("--apk-dir", type=Path, default=ROOT / "app/build/outputs/apk")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--profile", choices=PROFILE_CASES, default="smoke")
    parser.add_argument("--iterations", type=int, default=1, help="1–100; use at least 20 for timing targets")
    parser.add_argument("--timeout-seconds", type=int, default=1800)
    args = parser.parse_args()
    if args.timeout_seconds <= 0:
        parser.error("--timeout-seconds must be positive")
    if not 1 <= args.iterations <= 100:
        parser.error("--iterations must be between 1 and 100")
    app_apk = args.apk_dir / "debug/app-debug.apk"
    test_apk = args.apk_dir / "androidTest/debug/app-debug-androidTest.apk"
    for apk in (app_apk, test_apk):
        if not apk.is_file():
            parser.error(f"Missing {apk}; build assembleDebug assembleDebugAndroidTest first")
    adb = ["adb", "-s", args.serial]

    def call(arguments, timeout=120, check=True):
        return subprocess.run(adb + arguments, check=check, text=True,
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)

    if call(["shell", "getprop", "sys.boot_completed"]).stdout.strip() != "1":
        raise RuntimeError("Wait for full Android boot before installing or benchmarking")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    run_id = time.strftime("baseline-%Y%m%dT%H%M%SZ", time.gmtime()) + "-" + uuid.uuid4().hex[:8]
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    source = ROOT / "app/src/main/java/com/example/imagecompressor/util/ImageCompressor.kt"
    source_hash = hashlib.sha256(source.read_bytes()).hexdigest()
    tree_hash = source_tree_hash(ROOT / "app/src/main")
    for apk in (app_apk, test_apk):
        result = call(["install", "-r", str(apk)], timeout=300)
        print(result.stdout, end="", flush=True)
        if "Success" not in result.stdout:
            raise RuntimeError(f"Installation not confirmed: {apk.name}")
    command = ["shell", "am", "instrument", "-w", "-r", "-e", "class", CLASS,
               "-e", "baselineProfile", args.profile, "-e", "baselineRunId", run_id,
               "-e", "baselineIterations", str(args.iterations),
               "-e", "baselineRevision", revision,
               "-e", "baselineCompressorSha256", source_hash, TEST]
    print(f"Running {args.profile} baseline ({args.iterations} iteration(s)); run ID: {run_id}", flush=True)
    log = args.output_dir / f"{run_id}.log"
    # Preserve full runner output even on timeout or failure; am instrument can exit zero
    # despite failed tests, so both the JUnit summary and report completeness are checked.
    with log.open("x") as output:
        result = subprocess.run(adb + command, stdout=output, stderr=subprocess.STDOUT,
                                timeout=args.timeout_seconds)
    text = log.read_text()
    report_result = call(["exec-out", "run-as", APP, "cat", f"files/compression-benchmark/{run_id}.json"], check=False)
    if report_result.returncode or not report_result.stdout.lstrip().startswith("{"):
        raise RuntimeError(f"No JSON report from this run; instrumentation may have crashed before tests started. Inspect {log}")
    report = json.loads(report_result.stdout)
    report["host"] = {
        "appApkSha256": hashlib.sha256(app_apk.read_bytes()).hexdigest(),
        "testApkSha256": hashlib.sha256(test_apk.read_bytes()).hexdigest(),
        "workingTreeDirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT)),
        "sourceTreeSha256": tree_hash,
        "instrumentationSucceeded": result.returncode == 0 and bool(re.search(r"^OK \(1 test\)\s*$", text, re.MULTILINE)),
    }
    destination = args.output_dir / f"{run_id}.json"
    with destination.open("x") as output:
        json.dump(report, output, indent=2)
        output.write("\n")
    expected = MEASUREMENTS_PER_ITERATION[args.profile] * args.iterations
    valid = valid_report(report, run_id, expected, result.returncode, text,
                         expected_identities(args.profile, args.iterations))
    valid = valid and report.get("profile") == args.profile and report.get("iterations") == args.iterations
    if not valid:
        raise RuntimeError(f"Incomplete or failed baseline; inspect {log} and {destination}")
    print(f"Validated {len(report['results'])} measurements: {destination}")
    findings = [(row['id'], 'target not met') for row in report['results'] if row.get('targetMet') is False]
    findings += [(row['id'], 'orientation mismatch') for row in report['results']
                 if row.get('quality', {}).get('orientationPixelsCorrect') is False]
    for case, finding in findings:
        print(f"Baseline finding: {case}: {finding}")
    print("A completed baseline is not production acceptance; compare the findings and device limitations.")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.SubprocessError, ValueError, OSError) as error:
        print(f"Baseline failed: {error}", file=sys.stderr)
        sys.exit(1)
