# Poppins typography

Shrink bundles unmodified Poppins Regular (400), Medium (500), and SemiBold (600)
from [Google Fonts](https://github.com/google/fonts/tree/6157dfaa640ca5738b4d1a7070fe389c3036d7fc/ofl/poppins).
The source revision is `6157dfaa640ca5738b4d1a7070fe389c3036d7fc`.

The three font files total 476,204 bytes before APK compression. They are local
Android font resources, so text does not depend on a network font request or change
its geometry after a downloaded font arrives. All text sizes use scalable `sp`
units; Android's font-size setting remains effective.

| Role | Size / line height | Weight |
| --- | --- | --- |
| Screen title | 22sp / 28sp | SemiBold |
| Main result size | 32sp / 40sp | SemiBold |
| Body text | 16sp / 24sp | Regular |
| Supporting text | 13sp / 20sp | Regular |
| Action labels | 14sp / 20sp | Medium |

The complete role mapping is in `theme/Type.kt`. No product label style is smaller
than 13sp before user scaling. Layouts must wrap and scroll rather than disable
font scaling or shrink text to fit.

Poppins is distributed under the SIL Open Font License 1.1. Its original copyright
notice and full licence are preserved in [OFL.txt](OFL.txt) and packaged with the
application in `app/src/main/assets/licenses/poppins_ofl.txt`. The asset remains
available when Android resource shrinking is later enabled.

[provenance.json](provenance.json) records the exact upstream URLs, byte sizes,
font-name records, source revision, and SHA-256 hashes. Download validation checks
the TrueType signature, table bounds, OS/2 weights (400/500/600), Poppins family
name, and complete sfnt checksum. These checks verify asset integrity and metadata;
actual rendering and layout validation belongs to the native Compose tests.
