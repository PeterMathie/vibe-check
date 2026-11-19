import json
import os
from pathlib import Path
import re
import selectors
import signal
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timezone


CLASS = "com.petermathie.vibecheck.EndUserControlsTest"
CASES = (
    "homeDayModalKeepsRowsCompactBlursBackdropAndGuardsDate",
    "homeMuscleDoubleTapShowsWeeklyRowsWithSynchronizedSofterGlass",
)
ADB = ["adb", "-s", "emulator-5554"]
FILTERS = [
    "WindowManager:V", "InputDispatcher:V", "ActivityManager:I",
    "ActivityTaskManager:I", "TestRunner:I", "am_anr:I", "input_focus:I",
    "wm_task_focus:I", "boot_progress_start:I", "boot_progress_ams_ready:I",
    "boot_progress_enable_screen:I", "*:S",
]
LOGCAT = ADB + ["logcat", "-b", "events", "-b", "main", "-b", "system", "-v", "epoch"]
LOG_LIMIT = 2 * 1024 * 1024
WINDOW_LIMIT = 256 * 1024
COMMAND_SECONDS = 5
OUTPUT = Path("app/build/home-native-focus")


def utc():
    return datetime.now(timezone.utc).isoformat()


class DiagnosticError(RuntimeError):
    pass


def stop_owned(process):
    record = {"pid": process.pid, "terminate": False, "kill": False}
    if process.poll() is None:
        record["terminate"] = True
        process.terminate()
    try:
        process.wait(timeout=2)
    except subprocess.TimeoutExpired:
        record["kill"] = True
        process.kill()
        try:
            process.wait(timeout=2)
        except subprocess.TimeoutExpired:
            record["unreaped"] = True
    record["returncode"] = process.poll()
    return record


def server_present():
    return any(len(fields := line.split()) > 3 and fields[1].endswith(":13AD") and fields[3] == "0A"
               for name in ["/proc/net/tcp", "/proc/net/tcp6"]
               for line in Path(name).read_text().splitlines()[1:])


def window_problem(text):
    section = ""
    system_error = on_screen = focused = windows = False
    for line in text.splitlines() + ["WINDOW MANAGER END"]:
        if line.startswith("WINDOW MANAGER ") or re.match(r"  Window #[0-9]+ Window[{]", line):
            if system_error and on_screen:
                return "On-screen SYSTEM_ERROR window"
            system_error = on_screen = False
        if line.startswith("WINDOW MANAGER "):
            section = line
            windows |= line.startswith("WINDOW MANAGER WINDOWS ")
        if section.startswith("WINDOW MANAGER LAST ANR ") and "Application at fault:" in line:
            return "Retained ANR in this disposable boot; not accepted as healthy startup"
        if section.startswith("WINDOW MANAGER WINDOWS "):
            system_error |= "SYSTEM_ERROR" in line
            on_screen |= "isOnScreen=true" in line
        if section.startswith("WINDOW MANAGER DISPLAY CONTENTS "):
            focused |= "mCurrentFocus=Window{" in line
    if not windows:
        return "Unrecognized native window dump"
    if not focused:
        return "No focused native window"
    return None


def has_anr(text):
    return bool(re.search(r"\b[VDIWEF]\s+am_anr\s*:", text))


class Observation:
    def __init__(self, output):
        self.output = output
        output.mkdir(parents=True, exist_ok=True)
        self.errors = []
        self.children = []
        self.cleanup = []
        self.stopping = threading.Event()
        self.ready = threading.Event()
        self.started = []
        self.finished = []
        self.events = []
        self.snapshots = set()
        self.stream_range = []
        self.buffered_range = []
        self.boot_marker = False
        self.lock = threading.RLock()
        self.test = None
        self.monitor = None
        self.thread = None

    def fail(self, reason):
        with self.lock:
            self.errors.append(reason)
            print("::error::" + reason, file=sys.stderr)
            if self.test is not None and self.test.poll() is None:
                self.test.terminate()

    def capture(self, args, label, limit):
        start = utc()
        process = subprocess.Popen(args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        self.children.append(process)
        deadline = time.monotonic() + COMMAND_SECONDS
        size = 0
        problem = None
        with selectors.DefaultSelector() as selector, (self.output / (label + ".txt")).open("wb") as file:
            selector.register(process.stdout, selectors.EVENT_READ)
            while selector.get_map():
                remaining = deadline - time.monotonic()
                if remaining <= 0 or not selector.select(remaining):
                    problem = "Command deadline exceeded; no retry"
                    break
                data = os.read(process.stdout.fileno(), min(65536, limit - size + 1))
                if not data:
                    selector.unregister(process.stdout)
                    break
                file.write(data[:limit - size])
                size += len(data)
                if size > limit:
                    problem = "Diagnostic byte cap exceeded; incomplete evidence"
                    break
        if problem is None:
            try:
                process.wait(timeout=max(0.001, deadline - time.monotonic()))
            except subprocess.TimeoutExpired:
                problem = "Command exit deadline exceeded; no retry"
        if process.poll() is None:
            self.cleanup.append(stop_owned(process))
        process.stdout.close()
        record = {"argv": args, "pid": process.pid, "start": start, "end": utc(),
                  "returncode": process.poll(), "bytes_read": size, "limit": limit, "problem": problem}
        (self.output / (label + ".json")).write_text(json.dumps(record, indent=2) + "\n")
        if problem or process.returncode:
            raise DiagnosticError(f"{label}: {problem or 'command failed'} (exit {process.returncode})")
        text = (self.output / (label + ".txt")).read_text(errors="replace")
        if "daemon not running" in text:
            raise DiagnosticError(f"{label}: unexpected adb server auto-start; not recovery evidence")
        return text

    def read_stream(self):
        size = 0
        try:
            with (self.output / "native-timeline.log").open("wb") as file:
                while line := self.monitor.stdout.readline(65537):
                    file.write(line[:max(0, LOG_LIMIT - size)])
                    size += len(line)
                    if size > LOG_LIMIT or (len(line) == 65537 and not line.endswith(b"\n")):
                        raise DiagnosticError("Native stream byte/line cap exceeded")
                    text = line.decode(errors="replace")
                    if stamp := re.match(r"^\s*(\d+\.\d+)\s", text):
                        if not self.stream_range:
                            self.stream_range.append(stamp[1])
                        self.stream_range[1:] = [stamp[1]]
                    if text.startswith("--------- beginning of ") or re.match(r"^\s*\d+\.\d+\s", text):
                        self.ready.set()
                    if has_anr(text) or "daemon not running" in text:
                        if self.test is not None:
                            self.events.append({"phase": "native-anr-or-server-loss",
                                                "guest_record": text.strip(), "host_received": utc()})
                            self.capture(ADB + ["shell", "dumpsys", "-t", "2", "window"],
                                         "case-time-native-error", WINDOW_LIMIT)
                        raise DiagnosticError("Native ANR/server-loss event during observation: " + text.strip())
                    match = re.search(r"\bTestRunner\s*:\s*(started|failed|finished):\s+(\w+)\(([^)]+)\)", text)
                    if not match:
                        continue
                    phase, method, classname = match.groups()
                    if classname != CLASS or method not in CASES:
                        raise DiagnosticError("Unexpected test started; refusing broader instrumentation: " + text.strip())
                    event = {"phase": phase, "selector": CLASS + "#" + method,
                             "guest_record": text.strip(), "host_received": utc()}
                    if len(self.events) >= 8:
                        raise DiagnosticError("Case-marker count cap exceeded")
                    self.events.append(event)
                    if phase in ["started", "finished"]:
                        seen = self.started if phase == "started" else self.finished
                        if method in seen:
                            raise DiagnosticError("Duplicate case execution: " + method)
                        seen.append(method)
                    if phase in ["started", "failed"] and (phase, method) not in self.snapshots:
                        self.snapshots.add((phase, method))
                        self.capture(ADB + ["shell", "dumpsys", "-t", "2", "window"],
                                     f"case-{CASES.index(method)}-{phase}", WINDOW_LIMIT)
            if not self.stopping.is_set():
                raise DiagnosticError("Native stream ended before requested cleanup")
        except (OSError, DiagnosticError) as error:
            self.fail(str(error))

    def close(self):
        self.stopping.set()
        if self.test is not None and self.test.poll() is None:
            self.cleanup.append(stop_owned(self.test))
        if self.monitor is not None:
            self.cleanup.append(stop_owned(self.monitor))
        if self.thread is not None:
            self.thread.join(timeout=5 * (COMMAND_SECONDS + 4) + 2)
            if self.thread.is_alive():
                self.fail("Native observer thread did not close within cleanup bound")
        for process in self.children:
            self.cleanup.append(stop_owned(process))
        if self.monitor is not None:
            self.monitor.stdout.close()
        if any(record.get("unreaped") for record in self.cleanup):
            self.fail("Owned diagnostic child could not be reaped")
        (self.output / "case-events.json").write_text(json.dumps(self.events, indent=2) + "\n")
        (self.output / "cleanup.json").write_text(json.dumps(self.cleanup, indent=2) + "\n")

    def execute(self):
        status = 0
        try:
            if not server_present():
                raise DiagnosticError("Original adb server not listening; no restart or recovery")
            devices = self.capture(["adb", "devices", "-l"], "devices", WINDOW_LIMIT)
            rows = [line.split()[:2] for line in devices.splitlines()[1:] if line.strip()]
            if rows != [["emulator-5554", "device"]]:
                raise DiagnosticError("Expected exactly the original CI emulator5554; no recovery")
            self.monitor = subprocess.Popen(LOGCAT + FILTERS, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            self.thread = threading.Thread(target=self.read_stream, daemon=True)
            self.thread.start()
            boot = self.capture(LOGCAT + ["-d"] + FILTERS, "buffered-startup", LOG_LIMIT)
            stamps = re.findall(r"^\s*(\d+\.\d+)\s", boot, flags=re.M)
            self.buffered_range = [stamps[0], stamps[-1]] if stamps else []
            self.boot_marker = "boot_progress_start:" in boot
            window = self.capture(ADB + ["shell", "dumpsys", "-t", "2", "window"],
                                  "preflight-window", WINDOW_LIMIT)
            if has_anr(boot):
                raise DiagnosticError("ANR retained in available startup buffers")
            if reason := window_problem(window):
                raise DiagnosticError(reason)
            if self.errors or not self.ready.is_set() or self.monitor.poll() is not None:
                raise DiagnosticError("Native stream has not demonstrated attachment at preflight; no readiness retry")
            args = ["timeout", "--signal=TERM", "--kill-after=30s", "16m", "gradle",
                    ":app:connectedDebugAndroidTest", "--stacktrace",
                    "-Pandroid.testInstrumentationRunnerArguments.class=" + CLASS,
                    "-Pandroid.testInstrumentationRunnerArguments.tests_regex='^" + CLASS.replace(".", "[.]") +
                    "#(" + "|".join(CASES) + ")$'"]
            (self.output / "test-command.json").write_text(json.dumps({"utc": utc(), "argv": args}, indent=2) + "\n")
            with self.lock:
                if self.errors:
                    raise DiagnosticError("Native observer failed before test invocation")
                self.test = subprocess.Popen(args)
            status = self.test.wait()
            if status < 0:
                status = 128 - status
        except (OSError, DiagnosticError) as error:
            self.fail(str(error))
            status = 1
        finally:
            self.close()
        if self.test is not None:
            try:
                cases = [case for file in Path("app/build/outputs/androidTest-results/connected/debug").rglob("TEST-*.xml")
                         for case in ET.parse(file).getroot().iter("testcase")]
                actual = [case.get("classname", "") + "#" + case.get("name", "") for case in cases]
                expected = sorted(CLASS + "#" + method for method in CASES)
                if sorted(actual) != expected or any(case.find("skipped") is not None for case in cases):
                    raise DiagnosticError("Exported XML does not contain exactly the two unskipped requested cases")
                if sorted(self.started) != sorted(CASES) or sorted(self.finished) != sorted(CASES):
                    raise DiagnosticError("Native case markers incomplete; case-time coverage not established")
            except (OSError, ET.ParseError, DiagnosticError) as error:
                self.fail(str(error))
        result = {
            "utc": utc(), "test_status": status, "errors": self.errors, "started": self.started,
            "finished": self.finished, "monitor_pid": self.monitor.pid if self.monitor else None,
            "buffered_guest_epoch_range": self.buffered_range, "live_guest_epoch_range": self.stream_range,
            "boot_progress_start_retained_in_snapshot": self.boot_marker, "filters": FILTERS,
            "coverage": "Available buffered startup records, then live filtered native records. No pre-MENU observer; boot rollover/disabled producer logs may leave gaps. Case dumps follow host receipt, not atomic failing-action samples.",
            "observer_load": "One live logcat; one sequential window dump at each start/failure, up to four, plus one if a live native-error event aborts instrumentation. Reader may pause up to5s per dump; pipe/backpressure/timing effects remain possible.",
            "cleanup_limit": "Only owned observer/adb-client PIDs are reaped. Original runner owns emulator shutdown; no emulator/serial absence claim.",
        }
        (self.output / "result.json").write_text(json.dumps(result, indent=2) + "\n")
        return status if status else int(bool(self.errors))


if __name__ == "__main__":
    if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_OS") != "Linux":
        raise SystemExit("CI-only diagnostic; local checks must use imported fake-command fixtures")
    observer = Observation(OUTPUT)

    def interrupted(signum, frame):
        observer.fail(f"Diagnostic interrupted by signal {signum}")
        raise SystemExit(128 + signum)

    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    raise SystemExit(observer.execute())
