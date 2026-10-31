# Editor Step 6 — production validation

Date: 31 October 2026. Source: the final-step working tree based on `848418f`.
Application ID: **com.srizan12.shrink**; version **1.0.0 (1)**; minimum API 31,
target API 37.

This record distinguishes an engineered release candidate from permission to
publish it. Owner-controlled signing and physical acceptance are required before
public release. No Play publication or remote CI execution occurred in this task.

## Issues closed

1. **Embedded metadata allocation:** streaming container validation now caps the
   combined metadata in a source at 4 MiB before EXIF/color-profile readers can
   allocate whole chunks. JPEG APP/comment, PNG EXIF/text/ICC and WebP EXIF/XMP/ICC
   are covered. Pixel data retains the separate 100 MiB/50 MP/16,384-edge policy.
   The message directs the user to save a new copy. Regressions cover inspection,
   compression, aggregate limits, unchanged originals and cleanup.
2. **Revoked pending saves:** an inaccessible pending MediaStore URI no longer
   blocks restoration of all retained photos. Reuse/deletion verifies the image
   URI and app ownership. Tests protect another app's pending photo.
3. **Device transfer:** photos, Room history and preferences are excluded in all
   nine Android storage domains, for cloud backup and device transfer. The app
   still has no network or broad-media permission.
4. **Release configuration:** R8/resource shrinking, non-debuggable release,
   configurable versions and owner-supplied signing are implemented. A separate
   optimized `.qa` app allows testing without production credentials or data.
5. **Validation correctness:** required instrumentation names now match source;
   duplicate or skipped-required reports cannot pass. First launch asserts Home,
   theme choices are exercised, and CI device checks target optimized QA code.
6. **Release host testing:** Compose's host activity is registered only in
   Robolectric's per-test package manager. Shipping release manifests do not
   acquire a test-only activity. Debug/release captures have separate directories.
7. **Optimized test dependencies:** updated the accessibility framework's old
   transitive Material dependency to avoid a removed AppCompat class, supplied its
   AutoValue annotations, and scoped the JDK-only compiler annotation exception to
   the instrumentation shrinker. Shipping release rules retain no blanket keeps.
8. **Bundled notices:** the Material Symbols licence and attribution now ship in
   app assets alongside Poppins' existing licence.

## Executed checks

| Check | Executed result |
| --- | --- |
| Debug host suite | 221 pass, 1 optional measurement benchmark skipped (222 discovered) |
| Release host suite | 221 pass, 1 optional measurement benchmark skipped (222 discovered) |
| Python tooling | 14 benchmark/report + 6 device-report + 17 artifact-guard tests pass |
| Final Android lint | Zero errors; 36 debug / 37 release warnings (unused resources, available dependency versions and two version-catalog suggestions) |
| CI workflow static validation | actionlint passes |
| Release packaging | R8/resource-shrunk APK and AAB build; mapping files generated |
| Actual APK manifest | Correct application ID/version/SDKs, backup disabled, no network/broad-media permission, scoped unexported providers; no test host activity |
| Android App Bundle | Official bundletool 1.18.3 validation passes |
| 16 KiB compatibility | All four packaged native ABI libraries have 16,384-byte load alignment; Android zipalign and the artifact guard pass |
| Optimized QA | Non-debuggable app and instrumentation APK build; QA app signature verifies with the development certificate and separate `.qa` ID |
| Production signing | APK and AAB are intentionally unsigned; no owner keystore was supplied |
| Connected tests / physical performance / TalkBack | Not run; no connected device or hardware acceleration |

Final artifact hashes and test/lint counts are recorded in the
[candidate evidence](release-candidate-2026-10-31.json). The release host suite runs application classes
under Robolectric; it does not execute the obfuscated APK on an Android device.
The optimized QA instrumentation suite is built for that separate runtime check.
Its required native-workflow and accessibility test names survive shrinking, and
the QA app and test APK signatures verify against the same development certificate.

The production APK is 3,457,718 bytes and the App Bundle is 4,702,495 bytes.
The downloadable handoff contains the installable QA app, its device-test APK,
unsigned production APK/AAB, R8 mappings, build/check logs, host result XML,
licence notices, review captures and checksums. It contains no signing keys.

Current native host captures: [Home](../design/screenshots/release-home-light.png)
and [Settings in dark mode](../design/screenshots/release-settings-dark.png).
They are actual Compose renders, not physical-device screenshots.

The command set and signing instructions are in the
[release guide](../release/README.md). Artifacts and packaging checks cannot
establish physical latency, accessibility, visual quality, or OEM behavior.

## Remaining release gates

| Gate | Evidence still required |
| --- | --- |
| Signing | Configure the owner's upload keystore through the documented environment variables; build and verify certificate fingerprints on the signed APK/AAB. |
| Native device workflow | Execute optimized QA checks on API 31/35 and the release candidate on API 37; keep required-test guard output and XML. |
| Physical performance/quality | Representative 12 MP photos and documents; 20+ runs, real peak memory, 30-photo stress, full-resolution comparisons, alpha/orientation, HDR/HEIC evaluation before making support claims. |
| Lifecycle and integration | System picker cancel/revocation/cloud providers, actual share receivers, force-stop/reopen during work/save, low storage and permission loss. |
| Accessibility/OEM | TalkBack, large text, reduced motion, split-screen/landscape/tablet, dynamic colors, at least one non-Google OEM and actual launcher/splash transitions. |
| Publication | Host the supplied privacy policy, complete owner declarations/listing, run internal beta and review the Play pre-launch report. |

`adb devices -l` returned no devices and `/dev/kvm` is absent. The earlier software
emulator failed before producing valid acceptance evidence. No physical/device
performance or accessibility pass is claimed. The offline design intentionally
has no remote analytics or crash-reporting SDK; use Play's reports and user support
after the controlled rollout.
