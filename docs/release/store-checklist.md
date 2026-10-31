# First Play release checklist

Prepared product values:

- Name: **Shrink**. Application ID: **com.srizan12.shrink**.
- Version: **1.0.0**, version code **1**; Android 12+ (API 31), target API 37.
- Core task: select a photo, choose a size limit, review the verified result,
  then save or share a separate copy.
- Offline processing, no accounts, ads, analytics, or in-app purchases.
- Do not advertise universal exam/visa/KYC compliance, lossless JPEG conversion,
  guaranteed HDR/HEIC support, or uninterrupted background compression.

Owner actions before publication:

1. Configure Play App Signing and the upload key; verify the candidate's signing
   fingerprint and retain the R8 mapping, hashes, version and build evidence.
2. Publish the supplied privacy policy at a stable public URL, confirm the support
   contact, and add the URL to the store listing.
3. Complete Data safety against the actual signed binary. The present build has
   on-device photo processing and user-initiated sharing, with no developer data
   collection or server transmission. Recheck any future SDK or permission change.
4. Complete content rating, target audience, app-access and ads declarations.
   Select countries and release track. These publisher decisions are not inferred
   from code or filled in automatically.
5. Add final phone/tablet screenshots captured from real installations, the store
   icon and feature graphic. Host-rendered design previews are review materials.
6. Upload to internal testing, complete the physical acceptance matrix and inspect
   Play's pre-launch report. Check API 31, 35 and 37 plus a non-Google OEM device.
7. Address blocking crashes, accessibility issues, privacy failures and quality
   defects before a staged public rollout. Keep rollback/version plans and monitor
   Play Android vitals after release.

No Play upload or publication has been performed by the preparation task.
