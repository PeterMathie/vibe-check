#!/usr/bin/env python3
"""Temporary exact-selector accounting for the approved PR31 diagnostic slice."""

import argparse
import collections
import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

SELECTORS = [
    "com.petermathie.vibecheck.EndUserControlsTest#homeReclaimsSliderSpaceExtendsKeyAndUsesConstantInsetSelection",
    "com.petermathie.vibecheck.EndUserControlsTest#homeDayModalKeepsRowsCompactBlursBackdropAndGuardsDate",
    "com.petermathie.vibecheck.EndUserControlsTest#homeMuscleDoubleTapShowsWeeklyRowsWithSynchronizedSofterGlass",
    "com.petermathie.vibecheck.HabitUiTest#measurementDropdownsPreserveDraftsAndShowOnlyRelevantFields",
    "com.petermathie.vibecheck.HabitUiTest#modalScrollbarOnlyAppearsForOverflowAndSupportsDraggingInBothDirections",
    "com.petermathie.vibecheck.HabitUiTest#goalPanelAnimatesBothGrowthAndShrinkWithinOneModal",
    "com.petermathie.vibecheck.HabitUiTest#compactHabitsKeepNumericAndBooleanInputsInlineAndTwoCalendarsVisible",
    "com.petermathie.vibecheck.HabitUiTest#creationUsesFullEditorAndSingleModalDraftUntilAtomicSave",
    "com.petermathie.vibecheck.HabitUiTest#habitIconOpensSettingsWhileNameTogglesAndLongPressReorders",
    "com.petermathie.vibecheck.HabitUiTest#entryLabelsUseUnitsAndKeepNamesForUnitlessMeasurements",
    "com.petermathie.vibecheck.HabitUiTest#calendarInspectionKeepsDatesLeftAndMeasurementsRightWithoutMovingTheNextHabit",
]

BOUNDARIES = {
    "homeDayModalKeepsRowsCompactBlursBackdropAndGuardsDate": "home-day-native-back",
    "homeMuscleDoubleTapShowsWeeklyRowsWithSynchronizedSofterGlass": "home-muscle-native-back",
    "measurementDropdownsPreserveDraftsAndShowOnlyRelevantFields": "habits-measurement-native-back",
    "compactHabitsKeepNumericAndBooleanInputsInlineAndTwoCalendarsVisible": "habits-two-calendar-viewport",
    "habitIconOpensSettingsWhileNameTogglesAndLongPressReorders": "habits-icon-touch-target",
    "calendarInspectionKeepsDatesLeftAndMeasurementsRightWithoutMovingTheNextHabit": "habits-readout-",
}


def account_results(report_dir):
    issues = []
    results = []
    starts = finishes = failures = 0
    files = list(report_dir.glob("TEST-*.xml"))
    if len(files) != 1:
        return {"results": [], "issues": [f"Expected one device XML, found {len(files)}"]}
    root = ET.parse(files[0]).getroot()
    for case in root.findall(".//testcase"):
        cls, name = case.attrib["classname"], case.attrib["name"]
        failed = case.find("failure") is not None or case.find("error") is not None
        skipped = case.find("skipped") is not None
        selector = f"{cls}#{name}"
        results.append({
            "selector": selector,
            "outcome": "skipped" if skipped else "failed" if failed else "passed",
            "seconds": float(case.attrib["time"]),
        })
        logs = list(report_dir.glob(f"*/logcat-{cls}-{name}.txt"))
        if len(logs) != 1:
            issues.append(f"{selector}: expected one logcat, found {len(logs)}")
            continue
        text = logs[0].read_text()
        counts = {}
        for event in ("started", "finished", "failed"):
            pattern = re.escape(f"TestRunner: {event}: {name}({cls})") + r"\s*$"
            counts[event] = len(re.findall(pattern, text, re.MULTILINE))
        starts += counts["started"]
        finishes += counts["finished"]
        failures += counts["failed"]
        if counts != {"started": 1, "finished": 1, "failed": int(failed)}:
            issues.append(f"{selector}: invalid runner accounting {counts}")
        if skipped:
            issues.append(f"{selector}: skipping is not permitted")
    actual = collections.Counter(result["selector"] for result in results)
    if actual != collections.Counter(SELECTORS):
        issues.append(f"Selector mismatch: {dict(actual)}")
    if int(root.attrib["tests"]) != len(results):
        issues.append("XML declared count differs from actual testcase count")
    if int(root.attrib["failures"]) + int(root.attrib["errors"]) != sum(
        result["outcome"] == "failed" for result in results
    ):
        issues.append("XML failure/error count differs from testcase outcomes")
    if int(root.attrib["skipped"]) != 0:
        issues.append("XML declares skipped tests")
    return {
        "expected": SELECTORS, "results": results, "starts": starts,
        "finishes": finishes, "failure_terminals": failures, "issues": issues,
    }


def verify_diagnostics(output_dir, run_id, results):
    issues = []
    captures = []
    for path in sorted((output_dir / "ci-failure-diagnostics").glob("*.json")):
        data = json.loads(path.read_text())
        captures.append(data)
        if data.get("runId") != run_id or set(data.get("instrumentationSelectors", "").split(",")) != set(SELECTORS):
            issues.append(f"{path.name}: wrong run or instrumentation selector identity")
        for suffix in (".png", "-window.txt", "-activity.txt", "-input.txt"):
            artifact = path.with_name(path.stem + suffix)
            if not artifact.is_file() or artifact.stat().st_size == 0:
                issues.append(f"{path.name}: missing/empty {artifact.name}")
        if not data.get("nativeRoots"):
            issues.append(f"{path.name}: no native root metadata captured")
        for name, node in data.get("nodes", {}).items():
            if "nodeUnavailable" in node:
                issues.append(f"{path.name}: unavailable node {name}: {node['nodeUnavailable']}")
    labels = [capture["label"] for capture in captures]
    failed_methods = {
        result["selector"].partition("#")[2]
        for result in results if result["outcome"] == "failed"
    }
    for method, boundary in BOUNDARIES.items():
        if not any(label.startswith(boundary) and label.endswith("-before") for label in labels):
            issues.append(f"Missing before-boundary capture: {boundary}")
        if method in failed_methods and not any(
            label.startswith(boundary) and label.endswith("-failure") for label in labels
        ):
            issues.append(f"Missing failure-time capture: {boundary}")
    return {"captures": labels, "issues": issues}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("selectors", "verify"))
    parser.add_argument("--output-dir", type=pathlib.Path, required=True)
    parser.add_argument("--report-dir", type=pathlib.Path)
    parser.add_argument("--run-id", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9]+-[0-9]+", args.run_id):
        parser.error("--run-id must identify a numeric GitHub run and attempt")
    if len(SELECTORS) != 11 or len(set(SELECTORS)) != 11:
        parser.error("The approved selector set must contain exactly eleven unique methods")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    if args.mode == "selectors":
        (args.output_dir / "focused-selectors.json").write_text(json.dumps({
            "runId": args.run_id, "selectors": SELECTORS,
        }, indent=2) + "\n")
        print(",".join(SELECTORS))
        return 0
    if args.report_dir is None:
        parser.error("--report-dir is required for verification")
    report = account_results(args.report_dir)
    report["diagnostics"] = verify_diagnostics(args.output_dir, args.run_id, report["results"])
    report["runId"] = args.run_id
    report["accounted"] = not report["issues"] and not report["diagnostics"]["issues"]
    (args.output_dir / "focused-accounting.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    return 0 if report["accounted"] else 1


if __name__ == "__main__":
    sys.exit(main())
