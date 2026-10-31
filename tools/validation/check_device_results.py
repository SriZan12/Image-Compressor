#!/usr/bin/env python3
"""Fail CI if filtering or SDK skips silently removed a required device test."""

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "com.example.imagecompressor.validation."
WORKFLOW = (PACKAGE + "NativeWorkflowTest", "automaticPreviewExportsExactBytesOnceAndKeepsEditorSavedAfterRecreation")
ACCESSIBILITY = {
    (PACKAGE + "AccessibilitySmokeTest", "lightScreensPassAccessibilityChecks"),
    (PACKAGE + "AccessibilitySmokeTest", "darkScreensPassAccessibilityChecks"),
}


def verify(directory, api):
    expected = {WORKFLOW} | (ACCESSIBILITY if api >= 34 else set())
    passed = set()
    seen = set()
    for file in sorted(directory.rglob("*.xml")):
        for test in ET.parse(file).getroot().iter("testcase"):
            identity = (test.get("classname"), test.get("name"))
            if identity in seen:
                raise ValueError(f"Duplicate result: {identity}; use a clean report directory")
            seen.add(identity)
            if test.find("failure") is not None or test.find("error") is not None:
                raise ValueError(f"Failed device test: {identity}")
            if test.find("skipped") is None:
                passed.add(identity)
    if expected - passed:
        raise ValueError(f"Required tests missing or skipped on API {api}: {sorted(expected - passed)}")
    return len(expected)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--api", type=int, required=True)
    args = parser.parse_args()
    if args.api < 31:
        parser.error("The supported device matrix starts at API 31")
    print(f"Confirmed {verify(args.directory, args.api)} required device tests on API {args.api}")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, ET.ParseError) as error:
        print(f"Device validation failed: {error}", file=sys.stderr)
        sys.exit(1)
