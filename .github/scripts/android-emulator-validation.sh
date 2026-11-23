#!/usr/bin/env bash
set -euo pipefail

readonly output_dir="app/build/launch-check"
mkdir -p "$output_dir"

capture_diagnostics() {
  adb devices -l > "$output_dir/adb-devices.txt" || true
  adb logcat -d -t 2000 > "$output_dir/instrumentation-logcat.txt" || true
  adb shell dumpsys activity activities > "$output_dir/instrumentation-activities.txt" || true
  adb shell dumpsys window > "$output_dir/instrumentation-window.txt" || true
}

gradle :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace

instrumentation_args=()
if [[ "${HABITS_CI_DIAGNOSTICS:-false}" == "true" ]]; then
  if [[ "${GITHUB_ACTIONS:-}" != "true" ]]; then
    echo "Focused diagnostics are restricted to the approved GitHub CI emulator." >&2
    exit 1
  fi
  diagnostic_id="${GITHUB_RUN_ID:?}-${GITHUB_RUN_ATTEMPT:?}"
  selectors="$(python3 .github/scripts/habits-ci-diagnostics.py selectors --output-dir "$output_dir" --run-id "$diagnostic_id")"
  instrumentation_args=(
    "-Pandroid.testInstrumentationRunnerArguments.class=$selectors"
    "-Pandroid.testInstrumentationRunnerArguments.ciFailureDiagnostics=true"
    "-Pandroid.testInstrumentationRunnerArguments.ciDiagnosticRunId=$diagnostic_id"
  )
  printf 'head=%s\nrunner_arguments=%s\n' "$(git rev-parse HEAD)" "${instrumentation_args[*]}" \
    > "$output_dir/focused-dispatch.txt"
fi

# Exclude compilation from the runtime watchdog. In CI, 155 of 175 tests took
# 11 minutes while still progressing; allow the full suite to finish and report.
set +e
timeout --signal=TERM --kill-after=30s 16m \
  gradle :app:connectedDebugAndroidTest --stacktrace "${instrumentation_args[@]}"
test_status=$?
set -e
accounting_status=0
if [[ "${HABITS_CI_DIAGNOSTICS:-false}" == "true" ]]; then
  set +e
  adb pull "/sdcard/Download/vibe-ci-failure-diagnostics/$diagnostic_id" "$output_dir/ci-failure-diagnostics"
  pull_status=$?
  python3 .github/scripts/habits-ci-diagnostics.py verify \
    --output-dir "$output_dir" --run-id "$diagnostic_id" \
    --report-dir app/build/outputs/androidTest-results/connected/debug
  accounting_status=$?
  set -e
  if [[ "$pull_status" -ne 0 ]]; then
    echo "Failure-time diagnostic export failed with status $pull_status." >&2
    accounting_status=1
  fi
fi
if [[ "$test_status" -ne 0 ]]; then
  capture_diagnostics
  exit "$test_status"
fi
if [[ "$accounting_status" -ne 0 ]]; then
  capture_diagnostics
  exit "$accounting_status"
fi
if [[ "${HABITS_CI_DIAGNOSTICS:-false}" == "true" ]]; then
  exit 0
fi

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.petermathie.vibecheck/.MainActivity
sleep 5
adb shell pidof com.petermathie.vibecheck
adb shell screencap -p /sdcard/vibe-check-launch.png
adb pull /sdcard/vibe-check-launch.png "$output_dir/home.png"
adb logcat -d -s AndroidRuntime:E > "$output_dir/runtime-errors.txt"
