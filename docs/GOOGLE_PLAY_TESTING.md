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
- `ui/PlayEngagementHost.kt`: the review pause is two seconds on HOME after selections/results are cleared and loading, compression, saving, sharing, permission/picker UI, update UI, and task messages have finished.
- Review metadata uses `review_` keys in the existing `image_compressor_preferences` file. The synchronous durable commit runs on IO before any Play review request. Storage errors or clock rollback suppress requests; API failures still consume the attempt cooldown.

Updates re-query Play on foreground entry to recover download and installation state. Consent is limited to one automatic attempt per process session, with deliberate Settings retries allowed. Flexible installation requires clearing the current photo selection (for example, **Compress More**) and saving or discarding unsaved results. Only an explicit restart click calls `completeUpdate()`; a synchronous command guard prevents queued photo actions from starting while installation is pending. A terminal Play failure or cancellation releases that guard. Checks and review preparation time out after 15 seconds; installation completion has no local timeout that could accidentally unlock work before a late Play restart. A foreground reconciliation query runs 30 seconds after installation acknowledgment if the process is still alive; review preparation canceled by backgrounding consumes its reserved cooldown without surfacing an error.

## Local validation

With the Android SDK and the project's Java toolchains configured:

```bash
bash ./gradlew testDebugUnitTest assembleDebug lintDebug assembleRelease
```

In the prepared cloud environment, use its wrapper helper to keep tracked Windows SDK settings and Gradle caches unchanged:

```bash
bash /workspace/.image-compressor/gradle.sh testDebugUnitTest assembleDebug lintDebug assembleRelease
```

Policy and coordinator tests should cover cooldown boundaries, future timestamps/clock rollback, successful-session accounting, durable reservation failure, duplicate requests, canceled update consent, download progress, downloaded-update recovery, stale query responses, and safe restart gates. These checks validate application logic; they do not establish that Google Play will offer an update or show a review dialog.

For SDK-focused device tests, Google's `FakeAppUpdateManager` can simulate update availability, consent, progress, failure, cancellation, and completion. `FakeReviewManager` completes the API flow without displaying review UI or submitting a review. Supply fakes through test-only dependencies/factories; do not add production eligibility shortcuts. Fake-manager success is not a replacement for Play-channel validation.

## Updates through Google Play

Use a real Android device with a current Play Store and an eligible tester account. The app must be owned by that account and installed through the relevant Google Play testing channel. Keep the application ID, signing identity, and installation channel consistent between the installed and available builds. The available build must have a higher `versionCode`.

The repository currently declares `com.example.imagecompressor` and `versionCode = 1`; the release owner must use the actual registered Play application and increment version codes for the two test builds. Do not replace an existing published application ID merely to test this integration.

### Internal or closed testing track

1. Publish a baseline build to an internal or closed test track and enroll the test account. Follow the opt-in link and install the baseline from Play.
2. Publish a higher-version build on a track available to the same account. Allow Play distribution and device caching time; a successful publication is not immediate proof that an update is available on that device.
3. Launch the installed baseline. Verify flexible consent appears once automatically, and canceling it does not repeatedly prompt on navigation, rotation, or foregrounding. A deliberate Settings check may retry and must explain the result.
4. Accept the update. Exercise compression and saving while it downloads; verify useful progress feedback and no loss of the active task.
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
2. Perform real successful saves across the required sessions, then finish the task and reach the chosen natural pause. Test eligibility through a test-only policy configuration when necessary; preserve the production defaults in release builds.
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
- Compression, single save, partial batch save failure, permission prompt, photo picker, and share sheet while an update becomes ready.
- Safe explicit restart and critical immediate-update recovery; never automatic completion of a flexible update.
- Review eligibility/cooldown persistence, task-completion pause, suppression during any active update UI, and best-effort behavior when Play displays nothing.
- TalkBack labels, progress announcements, large font settings, current light/dark themes, and reachable update/review Settings actions.
- Offline compression and saving continue to work regardless of Play SDK availability.

No local JVM test, successful APK build, or emulator without Play services proves this matrix. Complete it through Google Play before release.

## Implementation validation record

Validated in the prepared Linux cloud environment on 2026-11-04:

- Local JUnit suite: 62 tests (36 update/coordinator, 18 review, 6 save-flow, 2 existing model tests); no failures, errors, or skipped tests.
- Debug APK assembly, unsigned release APK assembly, and Android lint all passed. Lint reported four existing warnings and one hint, with no errors. The release resource compiler exposed an existing JPEG stored with a `.png` extension; renaming it to `figma_empty_home.jpg` preserved every image byte and the existing `R.drawable.figma_empty_home` identifier.
- Production Play dialogs, store-delivered updates/restarts, UI accessibility, and offline/missing-Play behavior on a real device were not exercised here. The cloud emulator lacks hardware acceleration and has no usable Play testing setup. No fake-manager instrumentation/device tests were run.
- The repository's release artifact is unsigned; publication, signing, Play ownership, tester enrollment, and the two-version distribution setup remain the release owner's responsibilities. No build was uploaded or published.
