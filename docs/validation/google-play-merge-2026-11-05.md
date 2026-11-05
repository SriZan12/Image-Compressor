# Google Play integration merge validation — 5 November 2026

This record accompanies the merge of `codex/production-readiness` at
`c986a94e470561545b1df0dded6c429b5da60542` into
`feat/google-play-updates-reviews` at
`7824054e646905322f6e8fb4c9b53f68b603bd08`. Both histories are retained.

## Integration review

The production compression/storage pipeline, Room migration, permanent application
ID, privacy restrictions, optimized release/QA configuration, adaptive navigation,
live editor and session recovery are preserved. Play integration uses the newer
durable export receipts and theme/icon components.

New integration coverage checks actual preview cancellation drain, asynchronous
share preparation, duplicate/retried gallery saves, restored saved receipts,
matching source/settings at a completed-task pause, and queued navigation/settings
actions after update installation is reserved. Returning Home with fully saved
work permits review and explicit restart without deleting the selection. Unsaved
edits, incomplete batches, native work and external flows continue to block restart.

CI now requests the published SDK package `platforms;android-37.0` rather than an
unlisted `platforms;android-37` alias. No privacy assertions, release optimizations,
tests or third-party license text were removed to make the merge pass.

## Executed host checks

- Python validation tools: 37 passed (benchmark 14, device-result guard 6, release
  artifact guard 17).
- Debug JVM/Robolectric suite: 301 discovered, 300 passed, 1 skipped, no failures
  or errors.
- Release JVM/Robolectric suite: 301 discovered, 300 passed, 1 skipped, no failures
  or errors.
- The skipped test in each variant is the existing opt-in
  `PhotoOpeningMeasurementTest`; no performance claim is made from that skip.
- Native Compose Settings render inspected: the new Play controls retain the
  production typography, theme and component styling.
- No unresolved Git conflicts; whitespace validation passes for the integration
  diff against production-readiness. Its imported OFL license copies retain their
  original whitespace.

- Debug/release lint: zero errors; 36 debug and 37 release warnings (unused
  resources, dependency update notices and version-catalog suggestions). No lint
  baseline or suppression was added.
- Debug APK, debug instrumentation APK, optimized release APK and release AAB:
  built successfully with the repository's R8/resource shrinking settings.
- Optimized QA APK and QA instrumentation APK: built successfully with
  `-PandroidTestBuildType=qa assembleQa assembleQaAndroidTest`; device tests were
  compiled, not executed.
- Release artifact guard: passed manifest/privacy, real R8 mappings, archive
  integrity, native ELF alignment and native ZIP alignment checks.
- `zipalign -c -P 16 -v 4`: passed. Official bundletool 1.18.3 validation: passed;
  its pinned SHA-256 was verified before execution.

## Artifact identity

The inspected unsigned candidate is `com.srizan12.shrink`, version `1.0.0` (code 1),
minSdk 31 / targetSdk 37, non-debuggable with backup disabled. The packaged
manifest has no Internet or broad-media permissions. Both APK and AAB include
checked native libraries. Production signature verification is not claimed.
Artifacts were built from the resolved merge working tree before the merge commit;
the tested source changes are included in that commit.

| Artifact | SHA-256 |
| --- | --- |
| Release APK (3,556,288 bytes) | `05bdf266acf3a3ee102bf833c18b3d69d4870a6e28d50f9b0cc9d0551bbba852` |
| Release AAB (4,827,427 bytes) | `95ecebc9056bf29778fe8379597e9952bb1c4575074b9013b27492f5210f1d4a` |
| R8 mapping | `69c254f8cec50a503497d8be6ddc7af8d42232119bc546a16abb2fd372f6101f` |

Local evidence is under `app/build/test-results`, `app/build/reports`,
`app/build/design`, and `app/build/release-verification/report.json`. Build logs
are under `/workspace/.image-compressor/logs/merge-*`. Generated binaries, reports
and R8 mapping are not committed. Remote CI execution is not asserted here.

## Reproduction and remaining device gates

Use the commands in [the release guide](../release/README.md) and
[Google Play testing guide](../GOOGLE_PLAY_TESTING.md). The current cloud helper is
`/workspace/.image-compressor/gradle.sh` and writes outputs to `app/build`.

Host tests and compiled instrumentation do not establish device acceptance.
The API 31/35 optimized QA workflow/accessibility tests must execute on devices or
hardware-accelerated emulators. Production signing, actual Play update delivery,
review quota/dialog behavior, lifecycle recovery through Play, TalkBack and real
picker/share destinations still require the documented device/Play testing matrix.
No Play Console upload or public rollout is performed by this merge.
