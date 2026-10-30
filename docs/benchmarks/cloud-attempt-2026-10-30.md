# Initial cloud attempt — no valid device measurements

## Source and scope

- Source revision: `1e6f58d5d80353543ccafdc06b88aa44bd6225a6` with the Step 1
  benchmark/documentation additions in the working tree.
- Shipping `ImageCompressor.kt` SHA-256:
  `e961c1858f3f26e2a830d391cb3d56feb32c33eeb0a4f355ade2b00207805caf`.
  Its contents still match the source revision exactly.
- Attempted run: `baseline-20261030T051132Z-4c861d87`.
- Requested profile: `smoke`, one iteration, 18 expected measurements.
- Device: AOSP Android 15 / API 35, x86_64, Android Emulator 37.2.13,
  software CPU emulation without KVM. This is not representative phone hardware.

## Outcome

The Android instrumentation process was killed during startup, before JUnit
reported a test start and before the benchmark created a report. No compression
timing, size, memory, orientation or quality result was obtained. Do not interpret
this as 18 passing or failing compression cases; the cases did not execute.

The runner output was:

```text
INSTRUMENTATION_RESULT: shortMsg=Process crashed.
INSTRUMENTATION_CODE: 0
```

Android's process-exit diagnostics recorded reason `6 (ANR)` with description
`failed to complete startup`. System logs also recorded ANRs and process deaths
for the launcher, media provider, phone, contacts, and keychain services. The
system had already restarted during boot. This is environment-wide instability;
the run does not diagnose a compressor defect or establish an app performance
budget. The emulator acknowledged shutdown after diagnostics, then exited with
signal 11, matching the earlier environment-onboarding shutdown failure.

ADB's apparent success status was not accepted as a test pass. The host runner
failed, and now reports a missing JSON report explicitly. Its validation tests
cover process crashes with zero exit status, zero tests, stale/incomplete/empty
reports, duplicate measurements, source mutation and invalid outputs.

## Verification completed independently

- `assembleDebug`, `assembleDebugAndroidTest`, and `lintDebug` succeeded.
- The two existing JVM tests remained passing/up-to-date; these only cover
  formatting and reduction math, not image compression.
- Lint: no errors; five existing warnings and one hint.
- Four host tests for benchmark-result validation passed.
- All eight corpus files matched their manifest hashes. Regenerating the synthetic
  fixtures reproduced the hashes already packaged in the test APK exactly.
- Production compression/UI source was unchanged.

## Next required evidence

Run the documented benchmark on an Android 12+ physical test device or a
hardware-accelerated emulator. First obtain a complete smoke report, then run
the full profile to exercise 12MP/48MP inputs. Physical devices are required to
evaluate speed and memory budgets; accelerated emulators can establish functional
results but are not substitutes for phone performance measurements.

The prepared suite is compiled, not device-validated. Any harness issue found on
the first successful device run must be resolved before treating its data as a
baseline. The anticipated mirrored-EXIF and unmet-target issues remain findings
from code inspection until reproduced by an actual completed run.
