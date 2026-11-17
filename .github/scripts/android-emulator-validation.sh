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

# Exclude compilation from the runtime watchdog. In CI, 155 of 175 tests took
# 11 minutes while still progressing; allow the full suite to finish and report.
set +e
timeout --signal=TERM --kill-after=30s 16m \
  gradle :app:connectedDebugAndroidTest --stacktrace
test_status=$?
set -e
if [[ "$test_status" -ne 0 ]]; then
  capture_diagnostics
  exit "$test_status"
fi

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.petermathie.vibecheck/.MainActivity
sleep 5
adb shell pidof com.petermathie.vibecheck
adb shell screencap -p /sdcard/vibe-check-launch.png
adb pull /sdcard/vibe-check-launch.png "$output_dir/home.png"
adb logcat -d -s AndroidRuntime:E > "$output_dir/runtime-errors.txt"
