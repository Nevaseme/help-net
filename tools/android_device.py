"""Resolve local Android tooling without storing a developer path or device ID."""
import os
from pathlib import Path
import shutil


def adb_executable():
    if os.environ.get("ADB"):
        return os.environ["ADB"]
    if shutil.which("adb"):
        return shutil.which("adb")
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk and os.name == "nt":
        sdk = Path.home() / "AppData" / "Local" / "Android" / "Sdk"
    if sdk:
        return str(Path(sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb"))
    return "adb"


def adb_command(serial=None):
    serial = serial or os.environ.get("ANDROID_SERIAL")
    if not serial:
        raise RuntimeError("Set ANDROID_SERIAL to the test device selected from adb devices.")
    return [adb_executable(), "-s", serial]
