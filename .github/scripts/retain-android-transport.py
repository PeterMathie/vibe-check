import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
from datetime import datetime, timezone


REVISION = "a421e43855164a8197daf9d8d40fe71c6996bb0d"
MODULE_SHA256 = "a63db8fe9ffb4fb3651e9a2c97f8bac5ca4ce0078a7bbe61ef2e02019082c577"
SMALL = 65536
TAIL = 2 * 1024 * 1024


def utc():
    return datetime.now(timezone.utc).isoformat()


def identity(runner):
    def git(*args):
        return subprocess.check_output(["git", "-C", str(runner), *args], text=True).strip()
    if git("rev-parse", "HEAD") != REVISION or git("status", "--porcelain"):
        raise ValueError("Original pinned runner must be clean and unmodified")
    module = runner / "lib/emulator-manager.js"
    if hashlib.sha256(module.read_bytes()).hexdigest() != MODULE_SHA256:
        raise ValueError("Unexpected original startup executable")
    return {"commit": REVISION, "tree": git("rev-parse", "HEAD^{tree}"),
            "node_modules_tree": git("rev-parse", "HEAD:node_modules"),
            "module_sha256": MODULE_SHA256, "instrumentation_patch": None}


def listening_5037(tables):
    return any(len(fields := line.split()) > 3 and fields[1].endswith(":13AD") and fields[3] == "0A"
               for table in tables for line in table.splitlines()[1:])


def prepare(runner, output):
    original = identity(runner)
    if any(os.environ.get(key) for key in ["ADB_SERVER_SOCKET", "ANDROID_ADB_SERVER_ADDRESS", "ANDROID_ADB_SERVER_PORT"]):
        raise ValueError("Unexpected ADB server endpoint override; no recovery")
    if os.environ.get("ADB_TRACE") != "adb,transport":
        raise ValueError("Expected only native adb,transport trace categories")
    logfile = Path(os.environ["ANDROID_ADB_LOG_PATH"])
    if logfile != Path(os.environ["RUNNER_TEMP"]) / "home-adb-transport-native.log" or logfile.exists():
        raise ValueError("Expected a fresh job-owned native log path")
    if listening_5037([Path(name).read_text() for name in ["/proc/net/tcp", "/proc/net/tcp6"]]):
        raise ValueError("Pre-existing server/listener on5037; do not stop/restart it")
    for name in ["action.yml", "lib/emulator-manager.js", "lib/sdk-installer.js"]:
        data = (runner / name).read_bytes()
        if len(data) > SMALL:
            raise ValueError("Unexpected source size")
        (output / ("original-" + Path(name).name)).write_bytes(data)
    (output / "prepared.json").write_text(json.dumps({
        "utc": utc(), "runner": original, "trace": "adb,transport", "native_log": str(logfile),
        "existing_listener": False, "added_adb_calls": 0, "added_collectors": 0,
    }, indent=2) + "\n")


def retain(source, output, label, head=SMALL, tail=0):
    with source.open("rb") as stream:
        size = os.fstat(stream.fileno()).st_size
        for suffix, start, count in [("head", 0, min(head, size)), ("tail", max(0, size - tail), min(tail, size))]:
            if count:
                stream.seek(start)
                data = stream.read(count)
                if len(data) != count:
                    raise OSError("Native evidence changed/truncated during bounded read; no retry")
                (output / f"{label}.{suffix}").write_bytes(data)
    return {"source": str(source), "observed_bytes": size, "head_limit": head, "tail_limit": tail,
            "tail_start": max(0, size - tail), "captured_at": utc(),
            "omitted_middle": size > head + tail}


def collect(runner, output):
    errors = []
    records = {}
    if not (output / "prepared.json").is_file():
        errors.append("No completed original-runner/pre-existing-server preflight; capture provenance incomplete")
    for label, source, head, tail in [
        ("adb-server", Path(os.environ["ANDROID_ADB_LOG_PATH"]), SMALL, TAIL),
        ("platform-tools", Path(os.environ["ANDROID_HOME"]) / "platform-tools/source.properties", SMALL, 0),
        ("emulator", Path(os.environ["ANDROID_HOME"]) / "emulator/source.properties", SMALL, 0),
        ("image", Path(os.environ["ANDROID_HOME"]) / "system-images/android-35/default/x86_64/source.properties", SMALL, 0),
    ]:
        try:
            records[label] = retain(source, output, label, head, tail)
            if records[label]["observed_bytes"] == 0 or (tail == 0 and records[label]["omitted_middle"]):
                errors.append(f"{label}: empty or oversized metadata/native log")
        except OSError as error:
            errors.append(f"{label}: {error}")
    try:
        records["runner_after"] = identity(runner)
        binary = Path(os.environ["ANDROID_HOME"]) / "platform-tools/adb"
        resolved = shutil.which("adb")
        if not resolved or not os.path.samefile(resolved, binary) or binary.stat().st_size > 64 * 1024 * 1024:
            raise ValueError("Unexpected native adb executable resolution/size")
        records["native_adb"] = {"path": str(binary.resolve()), "sha256": hashlib.sha256(binary.read_bytes()).hexdigest()}
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        errors.append(str(error))
    files = {file.name: {"bytes": file.stat().st_size, "sha256": hashlib.sha256(file.read_bytes()).hexdigest()}
             for file in sorted(output.iterdir()) if file.is_file() and file.name != "capture.json"}
    (output / "capture.json").write_text(json.dumps({
        "utc": utc(), "original_action_outcome": os.environ.get("ORIGINAL_ACTION_OUTCOME", "not-recorded"),
        "retention_succeeded": not errors, "errors": errors, "records": records, "files": files,
        "startup_health": "NOT ASSESSED; no application tests or guest probes",
        "cleanup": "No added background processes or adb calls; original runner owns normal emulator shutdown. Serial/process absence not independently verified.",
        "limits": "Retained prefix/tail are byte-bounded; original native trace file is only job-time-bounded. No guest death/crash stream; server tail ends at this read, before final hosted-job cleanup. Preserve the complete GitHub job log for client stderr and original emulator console.",
    }, indent=2) + "\n")
    for error in errors:
        print(error, file=sys.stderr)
    return bool(errors)


if __name__ == "__main__":
    if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("RUNNER_OS") != "Linux":
        raise SystemExit("CI artifact helper only; local validation must use fixtures")
    output = Path(os.environ["TRANSPORT_OUTPUT_DIR"])
    output.mkdir(parents=True, exist_ok=True)
    if sys.argv[1] == "prepare":
        prepare(Path(sys.argv[2]), output)
    elif sys.argv[1] == "collect":
        raise SystemExit(collect(Path(sys.argv[2]), output))
    else:
        raise SystemExit("Expected prepare or collect")
