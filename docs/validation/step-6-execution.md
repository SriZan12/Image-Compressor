# Step 6 execution record

Date: 2026-10-30. Workspace: `/workspace/Image-Compressor`.

This record describes the current uncommitted working tree. It is not a signed
release artifact or proof that GitHub Actions ran.

| Check | Result |
| --- | --- |
| Android host regression suite | 90 tests across 15 suites; 0 failures, errors or skips, including 22 native Compose rendering/interaction tests and 2 new privacy tests |
| Benchmark/report tooling | 14 Python tests pass |
| Device-result completeness guard | 4 Python tests pass |
| Workflow static validation | actionlint 1.7.7 passes |
| Debug APK | Builds successfully |
| Instrumentation APK | Final rebuild succeeds, including the native workflow test and both accessibility smoke tests |
| Android lint | 0 errors; 2 dependency-update notices (Compose compiler and coroutines-test) |
| Release merged manifest | No network/broad media permissions; backup disabled; debuggable false; both providers unexported |
| Connected native workflow and ATF | Compiled tests only; no device execution |
| Physical benchmarks, TalkBack and OEM/tablet checks | Not run |
| GitHub Actions execution | Not triggered/observed |

The merged release manifest exports the launcher activity and AndroidX's
`ProfileInstallReceiver`, which requires `android.permission.DUMP`. The app's
FileProvider requires explicit URI grants. The only requested permission in that
manifest is the app's generated signature permission for non-exported receivers.
This manifest review is not a signed release-build or native-library compatibility test.

The coroutines-test notice is not resolved by upgrading only that artifact: the
test/runtime coroutine versions must stay aligned. Dependency upgrades and their
compatibility checks should be reviewed together. The existing production
comparison component also emits a deprecated-transformable-API compiler warning;
its tested behavior is unchanged in this stage.

Reproduction commands:

```bash
/workspace/.image-compressor/gradle.sh testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest processReleaseMainManifest
python3 -m unittest discover -s tools/benchmark -p 'test_*.py' -v
python3 -m unittest discover -s tools/validation -p 'test_*.py' -v
/workspace/.image-compressor/bin/actionlint
```

Host XML results are under
`/workspace/.image-compressor/builds/app/test-results/testDebugUnitTest`; lint reports
are under `/workspace/.image-compressor/builds/app/reports`. The full build log is
`/workspace/.image-compressor/logs/step6-validation.log`; the final instrumentation
rebuild log is `/workspace/.image-compressor/logs/step6-final-instrumentation.log`.

The environment has no attached device and no KVM. The previous software-emulator
failure is documented in [the initial attempt](../benchmarks/cloud-attempt-2026-10-30.md).
There is no valid physical or emulator baseline to compare against.

The remaining physical/manual matrix and exact commands are in
[the validation guide](README.md). Step 6 release gates remain open until those
results are recorded; Step 7 has not started.
