#!/usr/bin/env python3
"""Compare complete, matching host photo-opening runs; never infer device speed."""
import json
import math
import statistics
import sys
from collections import defaultdict
from pathlib import Path


def read(path):
    report = json.loads(Path(path).read_text())
    if report.get("complete") is not True or not report.get("measurements"):
        raise ValueError(f"Incomplete measurement report: {path}")
    groups = defaultdict(list)
    for row in report["measurements"]:
        if row["coldSource"] != "DISK" or row["warmSource"] != "MEMORY_CACHE":
            raise ValueError("Unexpected cache result")
        for field in ("inspectMs", "coldCoilMs", "warmCoilMs", "inspectAndColdCoilMs"):
            if not math.isfinite(row[field]) or row[field] < 0:
                raise ValueError(f"Invalid measurement: {field}")
        groups[row["file"]].append(row)
    for rows in groups.values():
        if sorted(row["iteration"] for row in rows) != list(range(1, len(rows) + 1)):
            raise ValueError("Missing or duplicate iterations")
    return report, groups


def compare(before_path, after_path):
    before, old = read(before_path)
    after, new = read(after_path)
    for field in ("environment", "request", "coldDefinition", "clock"):
        if before.get(field) != after.get(field):
            raise ValueError(f"Measurement conditions differ: {field}")
    if old.keys() != new.keys():
        raise ValueError("Fixture sets differ")
    print("Host medians in ms; these are not phone latency measurements.\n")
    print("| Input | Inspect before | Inspect after | Cold Coil after | Warm Coil after | Decoded pixels |")
    print("| --- | ---: | ---: | ---: | ---: | --- |")
    for name in sorted(old):
        a, b = old[name], new[name]
        if len(a) != len(b):
            raise ValueError(f"Iteration counts differ: {name}")
        for field in ("sha256", "sourceBytes", "sourceWidth", "sourceHeight"):
            if len({row[field] for row in a + b}) != 1:
                raise ValueError(f"Input changed: {name} {field}")
        median = lambda rows, field: statistics.median(row[field] for row in rows)
        print(f"| {name} | {median(a, 'inspectMs'):.2f} | {median(b, 'inspectMs'):.2f} | "
              f"{median(b, 'coldCoilMs'):.2f} | {median(b, 'warmCoilMs'):.2f} | "
              f"{b[0]['decodedWidth']} × {b[0]['decodedHeight']} |")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: summarize_photo_opening.py BEFORE.json AFTER.json")
    compare(*sys.argv[1:])
