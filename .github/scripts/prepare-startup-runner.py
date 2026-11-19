import difflib
import hashlib
import json
from pathlib import Path
import subprocess
import sys


REVISION = "a421e43855164a8197daf9d8d40fe71c6996bb0d"
BASE_SHA256 = "a63db8fe9ffb4fb3651e9a2c97f8bac5ca4ce0078a7bbe61ef2e02019082c577"
ANCHOR = "        await adb(port, `shell input keyevent 82`);\n"
REPLACEMENT = """        const startupProbe = require("./startup-probe.cjs");
        await startupProbe.beforeMenu(port);
        try {
            await adb(port, `shell input keyevent 82`);
        } finally {
            await startupProbe.afterMenu(port);
        }
"""


def instrument(original):
    if hashlib.sha256(original).hexdigest() != BASE_SHA256:
        raise ValueError("Unexpected original runner executable; refusing instrumentation")
    text = original.decode()
    if text.count(ANCHOR) != 1:
        raise ValueError("Original MENU anchor is not unique")
    return text.replace(ANCHOR, REPLACEMENT, 1).encode()


def prepare(runner, output):
    def git(*args):
        return subprocess.check_output(["git", "-C", str(runner), *args], text=True).strip()

    if git("rev-parse", "HEAD") != REVISION or git("status", "--porcelain"):
        raise ValueError("Expected a clean checkout of the exact pinned runner")
    target = runner / "lib/emulator-manager.js"
    original = target.read_bytes()
    modified = instrument(original)
    probe = Path(__file__).with_name("startup-probe.cjs").read_bytes()
    if (runner / "lib/startup-probe.cjs").exists():
        raise ValueError("Refusing to overwrite an existing runner helper")
    output.mkdir(parents=True, exist_ok=True)
    identity = {
        "commit": REVISION,
        "tree": git("rev-parse", "HEAD^{tree}"),
        "node_modules_tree": git("rev-parse", "HEAD:node_modules"),
        "action_blob": git("rev-parse", "HEAD:action.yml"),
        "original_executable_sha256": hashlib.sha256(original).hexdigest(),
        "instrumented_executable_sha256": hashlib.sha256(modified).hexdigest(),
        "probe_sha256": hashlib.sha256(probe).hexdigest(),
        "changes": "Only lib/emulator-manager.js plus new lib/startup-probe.cjs; no npm/build/install.",
    }
    (output / "runner-identity.json").write_text(json.dumps(identity, indent=2) + "\n")
    (output / "base-emulator-manager.js").write_bytes(original)
    (output / "instrumented-emulator-manager.js").write_bytes(modified)
    (output / "startup-probe.cjs").write_bytes(probe)
    (output / "original-action.yml").write_bytes((runner / "action.yml").read_bytes())
    patch = "".join(difflib.unified_diff(
        original.decode().splitlines(keepends=True), modified.decode().splitlines(keepends=True),
        fromfile="a/lib/emulator-manager.js", tofile="b/lib/emulator-manager.js",
    ))
    patch += "".join(difflib.unified_diff(
        [], probe.decode().splitlines(keepends=True),
        fromfile="/dev/null", tofile="b/lib/startup-probe.cjs",
    ))
    (output / "runner-instrumentation.patch").write_text(patch)
    target.write_bytes(modified)
    (runner / "lib/startup-probe.cjs").write_bytes(probe)


if __name__ == "__main__":
    prepare(Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve())
