"use strict";
const { execFile } = require("node:child_process");
const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");

const MiB = 1024 * 1024;
const output = () => {
    if (!process.env.STARTUP_OUTPUT_DIR) throw new Error("STARTUP_OUTPUT_DIR is required");
    const directory = path.resolve(process.env.STARTUP_OUTPUT_DIR);
    fs.mkdirSync(directory, { recursive: true });
    return directory;
};
const stamp = () => ({ utc: new Date().toISOString(), monotonicNs: process.hrtime.bigint().toString() });
const write = (name, value) => fs.writeFileSync(path.join(output(), name), JSON.stringify(value, null, 2) + "\n");
let menuStart;

function command(phase, name, args, limit = MiB) {
    return new Promise((resolve, reject) => {
        const start = stamp();
        const child = execFile("adb", ["-s", "emulator-5554", ...args], {
            timeout: 2000, killSignal: "SIGKILL", maxBuffer: limit,
        }, (error, stdout, stderr) => {
            try {
                const out = Buffer.from(stdout);
                const err = Buffer.from(stderr);
                fs.writeFileSync(path.join(output(), `${phase}-${name}.stdout`), out.subarray(0, limit));
                fs.writeFileSync(path.join(output(), `${phase}-${name}.stderr`), err.subarray(0, 65536));
                const result = {
                    name, argv: ["adb", "-s", "emulator-5554", ...args],
                    pid: child.pid ?? null, start, end: stamp(),
                    status: error ? error.code : 0,
                    signal: error?.signal ?? null, killed: error?.killed ?? false,
                    error: error ? error.message.slice(0, 512) : null,
                    stdoutBytes: out.length, stderrBytes: err.length,
                    stdoutLimit: limit, stderrLimit: 65536,
                    truncated: out.length > limit || err.length > 65536 ||
                        error?.code === "ERR_CHILD_PROCESS_STDIO_MAXBUFFER",
                    childClosed: true,
                };
                if (error) console.error(`Diagnostic ${phase}/${name} incomplete: ${result.status}/${result.signal}`);
                resolve(result);
            } catch (error) {
                reject(error);
            }
        });
    });
}

async function snapshot(phase, port) {
    if (process.env.GITHUB_ACTIONS !== "true" || process.env.RUNNER_OS !== "Linux" || port !== 5554) {
        throw new Error("Probe is restricted to the disposable Linux CI emulator-5554");
    }
    const start = stamp();
    const probes = [
        command(phase, "input-service", ["shell", "service", "check", "input"], 65536),
        command(phase, "input", ["shell", "dumpsys", "input"]),
        command(phase, "window", ["shell", "dumpsys", "window"]),
        command(phase, "anr", ["shell", "dumpsys", "activity", "lastanr"]),
        command(phase, "logcat", ["logcat", "-d", "-b", "events", "-b", "system", "-b", "main",
            "-v", "epoch", "*:I", "WindowManager:V", "InputDispatcher:V"], 4 * MiB),
    ];
    if (phase === "after-menu") {
        probes.push(command(phase, "sdk", ["shell", "getprop", "ro.build.version.sdk"], 65536));
        probes.push(command(phase, "fingerprint", ["shell", "getprop", "ro.build.fingerprint"], 65536));
    }
    const commands = await Promise.all(probes);
    write(`${phase}.json`, { phase, start, end: stamp(), commands, persistentCollectors: false });
}

async function beforeMenu(port) {
    await snapshot("before-menu", port);
    menuStart = stamp();
    write("menu-start.json", menuStart);
}

async function afterMenu(port) {
    const end = stamp();
    write("menu-end.json", {
        start: menuStart, end,
        elapsedMs: menuStart ? Number(BigInt(end.monotonicNs) - BigInt(menuStart.monotonicNs)) / 1e6 : null,
        outcome: "Original runner owns MENU exit/error handling; see action outcome and log.",
    });
    await snapshot("after-menu", port);
    const sdk = process.env.ANDROID_HOME;
    for (const [name, relative] of [
        ["image-source.properties", "system-images/android-35/default/x86_64/source.properties"],
        ["image-package.xml", "system-images/android-35/default/x86_64/package.xml"],
        ["emulator-source.properties", "emulator/source.properties"],
    ]) {
        try {
            if (!sdk) throw new Error("ANDROID_HOME is required for SDK identity capture");
            const data = fs.readFileSync(path.join(sdk, relative));
            if (data.length > 65536) throw new Error("SDK identity file exceeds 64 KiB");
            fs.writeFileSync(path.join(output(), name), data);
        } catch (error) {
            write(`${name}.error.json`, { error: error.message });
            console.error(`SDK identity ${name} unavailable: ${error.message}`);
        }
    }
}

function finalize() {
    const files = {};
    for (const name of fs.readdirSync(output()).sort()) {
        if (name === "final-receipt.json") continue;
        const file = path.join(output(), name);
        if (fs.statSync(file).isFile()) {
            const data = fs.readFileSync(file);
            files[name] = { bytes: data.length, sha256: crypto.createHash("sha256").update(data).digest("hex") };
        }
    }
    const phases = {};
    for (const phase of ["before-menu", "after-menu"]) {
        const file = path.join(output(), `${phase}.json`);
        phases[phase] = fs.existsSync(file) ? JSON.parse(fs.readFileSync(file)) : null;
    }
    const complete = Object.values(phases).every(phase =>
        phase && phase.commands.every(result => result.status === 0 && !result.truncated && result.childClosed));
    const sdkFile = path.join(output(), "after-menu-sdk.stdout");
    const correctApi = fs.existsSync(sdkFile) && fs.readFileSync(sdkFile, "utf8").trim() === "35";
    const identities = ["image-source.properties", "image-package.xml", "emulator-source.properties"];
    const identityComplete = identities.every(name => files[name] && !files[`${name}.error.json`]);
    write("final-receipt.json", {
        diagnosticOnly: true, applicationBuiltOrTested: false,
        runnerOutcome: process.env.STARTUP_RUNNER_OUTCOME ?? "not-recorded",
        shutdownSerialCheckOutcome: process.env.STARTUP_CLEANUP_OUTCOME ?? "not-recorded",
        phases, complete, correctApi, identityComplete, files,
        limits: "Non-atomic parallel snapshots, each child capped at 2s; read-only probes delay MENU and can change the race. A successful observed boot does not refute previous ANRs or validate Home.",
        cleanup: "No persistent collectors. Each probe child is awaited; timed-out children receive SIGKILL. Original pinned runner still owns its normal emulator shutdown.",
    });
    if (!complete || !correctApi || !identityComplete) {
        console.error("Startup evidence is incomplete or actual API is not 35; available artifacts retained.");
        process.exitCode = 1;
    }
}

module.exports = { beforeMenu, afterMenu, finalize };
if (require.main === module) {
    if (process.argv[2] !== "finalize") throw new Error("Only finalize is a standalone command");
    finalize();
}
