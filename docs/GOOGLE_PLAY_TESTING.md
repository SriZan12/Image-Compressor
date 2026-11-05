# Google Play updates and reviews

## SDK versions and official references

Google Maven metadata was checked on **2026-11-04**. It reported these stable releases:

| Feature | Dependency | Latest stable release |
| --- | --- | --- |
| In-app updates | `com.google.android.play:app-update` | `2.1.0` |
| In-app review | `com.google.android.play:review` | `2.0.2` |

The app uses the standalone libraries, not the retired monolithic `com.google.android.play:core` library. Their Tasks dependencies are resolved together by Gradle. Kotlin extensions are not required to use the callback APIs.

Version evidence:

- [App Update Maven metadata](https://dl.google.com/dl/android/maven2/com/google/android/play/app-update/maven-metadata.xml)
- [Review Maven metadata](https://dl.google.com/dl/android/maven2/com/google/android/play/review/maven-metadata.xml)

Official integration and testing references:

- [Implement in-app updates](https://developer.android.com/guide/playcore/in-app-updates/kotlin-java)
- [Test in-app updates](https://developer.android.com/guide/playcore/in-app-updates/test)
- [Review guidelines and quotas](https://developer.android.com/guide/playcore/in-app-review)
- [Implement in-app review](https://developer.android.com/guide/playcore/in-app-review/kotlin-java)
- [Test in-app review](https://developer.android.com/guide/playcore/in-app-review/test)

The Maven metadata and dependency POMs were fetched successfully during implementation. The documentation pages returned HTTP 403 in the development environment; the links above are provided for release/device testing, rather than represented as successfully retrieved pages.

## Product policies

Flexible updates are the normal flow. Downloading must leave compression usable. Installing a downloaded flexible update requires an explicit **Restart to update** action at a safe pause. Never restart automatically, or while loading photos, compressing, saving, sharing, or holding unsaved work.

Immediate updates are disabled by default. Enable them deliberately only for releases classified as critical, with Play update priority **5** and at least **7 days** of staleness. Immediate consent/resumption must also wait until the user's task is safe to interrupt. An update's priority alone does not justify silently restarting. Keep these thresholds in the update policy, rather than scattered through UI callbacks.

Automatic review eligibility defaults to **5 successful photo saves across 3 sessions**, at least **7 days** since local eligibility tracking began, and a **90-day cooldown** between attempts. The cooldown is persisted before requesting Play's review flow, including attempts that fail or do not display UI. Count actual saves, not compression attempts, button taps, existing historical records, or a review-flow completion. A batch counts its successfully saved photos but still represents one successful session. A session is one app-process lifetime. Rotation does not create a session; a new process counts only after an actual successful save. Counters reset after each review attempt, so subsequent attempts require fresh successful use as well as the cooldown.

Request a review at a natural pause after the task is complete, without a preparatory satisfaction question. Never offer incentives, request a particular rating, or claim a review was submitted. Play determines whether the dialog appears and exposes no reliable submitted-review result. **Rate this app** always opens the store listing directly; it must not invoke the quota-limited in-app review API.

### Where to configure the policies

- `play/update/UpdateCoordinator.kt`: `UpdatePolicy` defines critical-update opt-in, priority, and staleness thresholds. `AppContainer` constructs the process-scoped coordinator; pass an explicit `UpdatePolicy(criticalUpdatesEnabled = true, ...)` there only when the product requires this policy.
- `play/review/ReviewTracker.kt`: `ReviewPolicy` defines saves, successful sessions, first-tracked-use age, and cooldown. `AppContainer` constructs the tracker on the IO dispatcher; pass a policy there for controlled test builds. Defaults remain conservative for existing installations whose review tracking starts with this release.
- `play/PlayWorkSafety.kt` and `ui/PlayEngagementHost.kt`: the review pause is two seconds on Home after initialization, live-preview computation, loading, compression, saving, sharing, picker UI, update UI, task messages, and export errors have finished. A retained selection qualifies only when every current photo is durably saved with matching source metadata and settings. Returning Home keeps those receipts and selections; clearing them is not required. Restoring a saved editor/batch session must finish before any Play UI is considered.
- Review metadata uses `review_` keys in the existing `image_compressor_preferences` file. The synchronous durable commit runs on IO before any Play review request. Storage errors or clock rollback suppress requests; API failures still consume the attempt cooldown.

Updates re-query Play on foreground entry to recover download and installation state. Consent is limited to one automatic attempt per process session, with deliberate Settings retries allowed. Flexible installation requires ending the current photo-editing task and saving or explicitly discarding unsaved results. A fully saved selection permits restart from Home or Settings; its editor receipt or completed batch must match the current sources and settings. Retained history remains on disk; reopening a previous result does not authorize an update restart during editing. Only an explicit restart click calls `completeUpdate()`; a synchronous command guard prevents queued photo actions from starting while installation is pending. A terminal Play failure or cancellation releases that guard. Checks and review preparation time out after 15 seconds; installation completion has no local timeout that could accidentally unlock work before a late Play restart. A foreground reconciliation query runs 30 seconds after installation acknowledgment if the process is still alive; review preparation canceled by backgrounding consumes its reserved cooldown without surfacing an error.

### Integration with the production editor

The production app has an automatically computed, temporary single-photo preview, an explicit durable export step, and a separate recoverable batch workflow. An update restart must respect all three: preview work, retaining/saving/sharing the measured output, and batch processing or recovery. Preserve the exact measured image and its durable history receipt when integrating save callbacks; do not re-encode a photo merely to count a review event.

Both live-editor **Save photo** and successful batch/gallery saves feed the review tracker. Count a newly confirmed gallery save once; retries that reuse an already published gallery copy, restored history, preview generation, retention without a gallery save, and sharing do not qualify. A partial batch failure still counts its confirmed successful saves, without counting the failed items. Review counts and the attempted-review cooldown are local durable metadata, independent of the Room history list; clearing or reloading that list must not manufacture qualifying usage or reset the cooldown.

The current product supports Android 12+ (API 31), targeting API 37. `com.srizan12.shrink` is the permanent installed production application ID; `com.example.imagecompressor` remains the Kotlin namespace. The optimized device-test application is `com.srizan12.shrink.qa`, with separate storage and development signing. Its results cannot establish production Play ownership or update eligibility. Store links use the installed package name, so do not expect the unpublished QA ID to have a production store listing.

The Play libraries do not add Android Internet or broad-media permissions. Their cached manifests add an unexported Play dialog activity; Google Play performs its own service/network interactions. Keep the merged-package privacy tests, FileProvider restrictions, backup exclusions, optimized build configuration, and release artifact checks intact. An SDK update must pass those checks again rather than receive a privacy-test exemption.

## Local validation

With the Android SDK and the project's Java toolchains configured:

```bash
python3 -m unittest discover -s tools/benchmark -p 'test_*.py' -v
python3 -m unittest discover -s tools/validation -p 'test_*.py' -v
python3 -m unittest discover -s tools/release -p 'test_*.py' -v
bash ./gradlew testDebugUnitTest testReleaseUnitTest lintDebug lintRelease \
  assembleDebug assembleDebugAndroidTest assembleRelease bundleRelease
bash ./gradlew -PandroidTestBuildType=qa assembleQa assembleQaAndroidTest
```

In the prepared cloud environment, use its wrapper helper to keep tracked Windows SDK settings and Gradle caches unchanged:

```bash
bash /workspace/.image-compressor/gradle.sh testDebugUnitTest testReleaseUnitTest \
  lintDebug lintRelease assembleDebug assembleDebugAndroidTest assembleRelease bundleRelease
bash /workspace/.image-compressor/gradle.sh -PandroidTestBuildType=qa \
  assembleQa assembleQaAndroidTest
```

Inspect the optimized APK/AAB and R8 mapping with the [release artifact procedure](release/README.md), including the forbidden-permission guard, 16 KB native/ZIP alignment, and pinned bundletool validation. The current cloud helper writes outputs to `app/build`; confirm the actual output directory when using a different environment. Release host tests exercise host bytecode, not the installed R8-optimized binary.

Policy and coordinator tests should cover cooldown boundaries, future timestamps/clock rollback, successful-session accounting, durable reservation failure, duplicate requests, canceled update consent, download progress, downloaded-update recovery, stale query responses, and safe restart gates. These checks validate application logic; they do not establish that Google Play will offer an update or show a review dialog.

For SDK-focused device tests, Google's `FakeAppUpdateManager` can simulate update availability, consent, progress, failure, cancellation, and completion. `FakeReviewManager` completes the API flow without displaying review UI or submitting a review. Supply fakes through test-only dependencies/factories; do not add production eligibility shortcuts. Fake-manager success is not a replacement for Play-channel validation.

## Updates through Google Play

Use a real Android device with a current Play Store and an eligible tester account. The app must be owned by that account and installed through the relevant Google Play testing channel. Keep the application ID, signing identity, and installation channel consistent between the installed and available builds. The available build must have a higher `versionCode`.

The production application ID is `com.srizan12.shrink`; the release configuration defaults to `versionCode = 1` and `versionName = "1.0.0"`. Build the two test versions with increasing `-PreleaseVersionCode` values, retaining the permanent ID and the correct Play signing lineage. Do not use the `.qa` build or change the published application ID to make update discovery pass. A locally debug-signed installation is not interchangeable with a Play-delivered release signed by the production app-signing key.

### Internal or closed testing track

1. Publish a baseline build to an internal or closed test track and enroll the test account. Follow the opt-in link and install the baseline from Play.
2. Publish a higher-version build on a track available to the same account. Allow Play distribution and device caching time; a successful publication is not immediate proof that an update is available on that device.
3. Launch the installed baseline. Verify flexible consent appears once automatically, and canceling it does not repeatedly prompt on navigation, rotation, or foregrounding. A deliberate Settings check may retry and must explain the result.
4. Accept the update. Exercise automatic live previews, editor Save/Share, and batch compression/saving while it downloads; verify useful progress feedback and no loss or replacement of the measured image, durable result receipt, or recoverable batch.
5. Rotate, background, and reopen the app while downloading. After download, verify **Restart to update** is recovered even after activity/process recreation. Verify the restart action remains blocked while work is in progress or unsaved, and only explicit user action installs it.
6. Test immediate mode separately with the explicit critical policy enabled and an appropriately configured release priority. Set `inAppUpdatePriority` when creating the release through the Google Play Developer API; priority is a release attribute, not a client-side override. Account for the policy's staleness threshold. Confirm that an in-progress immediate flow resumes safely after returning to the app, without launching a second consent flow concurrently.

### Internal app sharing

Internal app sharing can shorten flexible/immediate flow testing:

1. Upload and install a baseline via its internal app sharing link using the tester account.
2. Upload a higher-version build with the same application ID through internal app sharing.
3. Open the higher-version sharing link on the device, but **do not install it** from that page. Return to the already installed baseline and trigger its update check.
4. Test cancellation, progress, downloaded recovery, and explicit completion as above.

`inAppUpdatePriority` is not supported by internal app sharing. Validate the production critical-update policy on a real Play testing track. A sideloaded development APK and an unconfigured emulator are not adequate evidence that update discovery works.

## Reviews through Google Play

1. Publish the app on an internal test track, enroll the Google account, and install through Play. Use the Play Store's primary eligible account, with the app in its library. Existing reviews, account restrictions, distribution state, and Play's own conditions can affect availability.
2. Perform real successful saves across the required sessions through both the live editor and batch workflow, then finish the task and reach the chosen natural pause. Verify duplicate saves reuse their durable receipts without increasing eligibility, and partial batch failures count only newly published copies. Test eligibility through a test-only policy configuration when necessary; preserve the production defaults in release builds.
3. Verify that a review API attempt is made only once for the eligible pause/session. Recreate the activity or background the app while the request is pending. Verify no duplicate dialogs or stale-activity launches.
4. Verify the persisted cooldown after success, request failure, API-flow failure, process death, and an attempt in which Play shows no dialog. Never label API completion as a submitted review.
5. Internal test tracks allow review-flow testing without normal quota limitations. Still test the application's own cooldown, and do not rely on any particular visible dialog outcome in production.
6. Internal app sharing can exercise the review UI, but reviews cannot be submitted through that channel. `FakeReviewManager` does not display UI at all.
7. Verify **Rate this app** opens the package's Play Store listing. On a device without a usable Play Store activity, verify the HTTPS listing opens in a browser. If neither is available, the app should give a plain-language failure message and continue working.

Do not use a fake review dialog, a rating selector, or a button that promises to show Google's review sheet. Do not gate useful app functionality on accepting, completing, or submitting a review.

## Required release/device matrix

Record the device, Android version, installed/available version codes, test account, distribution track, and observed outcome for each check:

- Play-installed baseline with no available update; higher available version; ineligible/sideloaded installation.
- Missing or disabled Play Store, network unavailable, network interruption, cancellation, SDK failure, and rapid repeated Settings taps.
- Rotation and navigation during consent/download/review preparation, background/resume, and process recreation with a downloaded update.
- Initialization/session restoration, automatic live-preview replacement, editor save/share retention, batch compression, partial batch save failure, photo picker, and share sheet while an update becomes ready. API 31+ scoped saving requires no storage-permission prompt.
- Safe explicit restart and critical immediate-update recovery; never automatic completion of a flexible update.
- Review eligibility/cooldown persistence, task-completion pause, suppression during any active update UI, and best-effort behavior when Play displays nothing.
- TalkBack labels, progress announcements, large font settings, current light/dark themes, and reachable update/review Settings actions.
- Offline compression and saving continue to work regardless of Play SDK availability.

Run native workflow/accessibility checks on a dedicated empty optimized QA installation on API 31 and API 35:

```bash
bash ./gradlew -PandroidTestBuildType=qa connectedQaAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.example.imagecompressor.validation
python3 tools/validation/check_device_results.py app/build/outputs/androidTest-results --api 35
```

Use `--api 31` for the API 31 run. That API executes the native workflow; API 35 also executes the accessibility tests. Preserve current-run XML and the required-test guard result. Compile-only QA APK results, an empty test run, or intentional accessibility skips on API 31 are not equivalent to executed API 35 checks.

Then repeat the relevant update/review matrix on the signed production candidate delivered through Play, including API 37 and at least one non-Google OEM device. Verify TalkBack, large fonts, safe restart, real picker/share destinations, and offline photo use. QA's distinct ID/signature prevents it from validating production Play flows. No local JVM test, successful APK build, or emulator without Play services proves this matrix.

## Validation evidence and scope

### Historical feature-branch checks

The following checks ran in the prepared Linux cloud environment on 2026-11-04, **before** integrating the production-readiness branch and its live editor, storage model, application ID, and release hardening. They describe the original Play feature revision, not the merged candidate:

- Local JUnit suite: 62 tests (36 update/coordinator, 18 review, 6 save-flow, 2 existing model tests); no failures, errors, or skipped tests.
- Debug APK assembly, unsigned release APK assembly, and Android lint all passed. Lint reported four existing warnings and one hint, with no errors. The release resource compiler exposed an existing JPEG stored with a `.png` extension; renaming it to `figma_empty_home.jpg` preserved every image byte and the existing `R.drawable.figma_empty_home` identifier.
- Production Play dialogs, store-delivered updates/restarts, UI accessibility, and offline/missing-Play behavior on a real device were not exercised here. The cloud emulator lacks hardware acceleration and has no usable Play testing setup. No fake-manager instrumentation/device tests were run.
- That revision's release artifact was unsigned. No build was uploaded or published.

### Production integration validation

See the [5 November merge validation record](validation/google-play-merge-2026-11-05.md) for the combined production/Play checks, test counts (including skips), artifact identity, and remaining device gates. The historical 62-test total and earlier screenshot/benchmark records are not evidence for the current merge. Keep the final merge revision, artifact hashes, R8 mapping, and executed device/Play results with each release candidate.

Signing, Play ownership, tester enrollment, the two-version distribution setup, and store/device acceptance remain release-owner gates. A successful unsigned build or saved configuration does not establish a signed or published release.
