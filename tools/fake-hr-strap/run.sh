#!/bin/sh
# Builds FakeStrap.app and starts it. Bundle and log live under $TMPDIR: launching it with
# stdout on an external volume (e.g. this repo on /Volumes/...) fails with LaunchServices -10810.
# Packaged as an .app and launched with `open` so macOS attributes Bluetooth access to it
# (a bare binary run from a terminal or agent is killed by TCC for lacking a usage string).
set -e
cd "$(dirname "$0")"
TMP="${TMPDIR:-/tmp}"
APP="${TMP%/}/sleeppulse-fakestrap/FakeStrap.app"  # build output, kept out of the repo
LOG="${TMP%/}/sleeppulse-fakestrap/fakestrap.log"
mkdir -p "$APP/Contents/MacOS"
swiftc -O main.swift -o "$APP/Contents/MacOS/fakestrap" \
    -Xlinker -sectcreate -Xlinker __TEXT -Xlinker __info_plist -Xlinker Info.plist
cp Info.plist "$APP/Contents/Info.plist"
codesign --force -s - "$APP"
: > "$LOG"
open -n "$APP" --stdout "$LOG" --stderr "$LOG"
echo "Started. Log: tail -f $LOG   Stop: pkill -f FakeStrap.app"
