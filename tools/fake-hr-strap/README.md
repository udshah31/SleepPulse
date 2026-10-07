# fake-hr-strap

A macOS stand-in for a BLE heart-rate strap, for testing SleepPulse's BLE mode without hardware.
It advertises as **SleepPulse-Test-HR** with the standard Heart Rate Service (`0x180D`) and
notifies the Heart Rate Measurement characteristic (`0x2A37`) once a second with a heart rate
and an RR-interval. Beats alternate ≈980.5 / ≈1019.5 ms, so the app's RMSSD should read **39 ms**
(39.0625 in Health Connect) — a known value to check the HRV path end to end.

```sh
tools/fake-hr-strap/run.sh          # build + start (macOS asks for Bluetooth once: Allow)
tail -f "${TMPDIR%/}/sleeppulse-fakestrap/fakestrap.log"
pkill -f FakeStrap.app              # stop
```

On the phone: Settings → Sensor source **BLE** → Scan → pick **SleepPulse-Test-HR** (it shows
the Mac's Bluetooth address), then Connect on the Dashboard. The log prints `subscribed` when the
phone connects and a line every 10 beats. Needs Swift (the Xcode Command Line Tools are enough).

The app bundle and log are built under `$TMPDIR`, not in the repo: launched with its log on an
external volume (where this repo lives), `open` fails with LaunchServices error -10810.
