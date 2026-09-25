"""Capture a native Android screen, restoring temporary display overrides.

Example:
    python tools/capture_android_ui.py --serial emulator-5554 --name home-light
    python tools/capture_android_ui.py --serial emulator-5554 --name home-dark-large \
        --night yes --font-scale 1.3 --size 360x720

Navigate the app to the desired screen before running the command. The script
does not install apps, insert data, or modify application storage.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import time
from pathlib import Path


def adb_call(adb: str, serial: str, *args: str) -> str:
    result = subprocess.run(
        [adb, "-s", serial, *args],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    return result.stdout.strip()


def capture(adb: str, serial: str, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open("wb") as output:
        subprocess.run(
            [adb, "-s", serial, "exec-out", "screencap", "-p"],
            check=True,
            stdout=output,
            stderr=subprocess.PIPE,
        )
    if destination.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
        destination.unlink(missing_ok=True)
        raise RuntimeError("Android did not return a PNG screenshot")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="ADB device serial")
    parser.add_argument("--name", required=True, help="PNG base name")
    parser.add_argument("--adb", default="adb", help="ADB executable path")
    parser.add_argument(
        "--output-dir", default="docs/ui-redesign-2026-09-25/device-captures"
    )
    parser.add_argument("--night", choices=("yes", "no"))
    parser.add_argument("--font-scale", type=float)
    parser.add_argument("--size", help="Temporary resolution WIDTHxHEIGHT, e.g. 360x720")
    parser.add_argument("--density", type=int, help="Temporary logical display density in dpi")
    parser.add_argument("--tap", nargs=2, type=int, metavar=("X", "Y"),
                        help="Tap after applying temporary display settings, before capture")
    parser.add_argument("--settle-seconds", type=float, default=1.0)
    args = parser.parse_args()

    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*", args.name):
        parser.error("--name must contain only letters, digits, dot, underscore or dash")
    if args.font_scale is not None and not 0.5 <= args.font_scale <= 3:
        parser.error("--font-scale must be between 0.5 and 3")
    if args.size is not None and not re.fullmatch(r"\d{3,4}x\d{3,4}", args.size):
        parser.error("--size must be WIDTHxHEIGHT")
    if args.density is not None and not 120 <= args.density <= 800:
        parser.error("--density must be between 120 and 800 dpi")

    old_night = None
    old_font = None
    old_size = None
    old_density = None
    destination = Path(args.output_dir) / f"{args.name}.png"
    try:
        if args.night is not None:
            mode = adb_call(args.adb, args.serial, "shell", "cmd", "uimode", "night")
            old_night = re.search(r"Night mode:\s*(yes|no|auto|custom)", mode, re.I)
            if not old_night:
                raise RuntimeError(f"Could not read prior night mode: {mode}")
            adb_call(args.adb, args.serial, "shell", "cmd", "uimode", "night", args.night)
        if args.font_scale is not None:
            old_font = adb_call(
                args.adb, args.serial, "shell", "settings", "get", "system", "font_scale"
            )
            adb_call(
                args.adb,
                args.serial,
                "shell",
                "settings",
                "put",
                "system",
                "font_scale",
                str(args.font_scale),
            )
        if args.size is not None:
            old_size = adb_call(args.adb, args.serial, "shell", "wm", "size")
            adb_call(args.adb, args.serial, "shell", "wm", "size", args.size)
        if args.density is not None:
            old_density = adb_call(args.adb, args.serial, "shell", "wm", "density")
            adb_call(args.adb, args.serial, "shell", "wm", "density", str(args.density))
        if args.tap is not None:
            adb_call(args.adb, args.serial, "shell", "input", "tap", *(str(x) for x in args.tap))
        time.sleep(max(0, args.settle_seconds))
        capture(args.adb, args.serial, destination)
        print(destination.resolve())
    finally:
        if old_density is not None:
            override = re.search(r"Override density:\s*(\d+)", old_density)
            adb_call(
                args.adb,
                args.serial,
                "shell",
                "wm",
                "density",
                override.group(1) if override else "reset",
            )
        if old_size is not None:
            override = re.search(r"Override size:\s*(\d+x\d+)", old_size)
            adb_call(
                args.adb,
                args.serial,
                "shell",
                "wm",
                "size",
                override.group(1) if override else "reset",
            )
        if old_font is not None:
            if old_font in ("", "null"):
                adb_call(
                    args.adb, args.serial, "shell", "settings", "delete", "system", "font_scale"
                )
            else:
                adb_call(
                    args.adb,
                    args.serial,
                    "shell",
                    "settings",
                    "put",
                    "system",
                    "font_scale",
                    old_font,
                )
        if old_night is not None:
            adb_call(
                args.adb, args.serial, "shell", "cmd", "uimode", "night", old_night.group(1)
            )


if __name__ == "__main__":
    main()
