import importlib.util
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile

SPEC = importlib.util.spec_from_file_location("release_guard", Path(__file__).with_name("check_artifacts.py"))
guard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(guard)

MANIFEST = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
 package="com.example.imagecompressor" android:versionCode="1" android:versionName="1.0">
 <uses-sdk android:minSdkVersion="31" android:targetSdkVersion="37" />
 <application android:name=".ImageCompressorApplication" android:label="Shrink"
 android:icon="@mipmap/ic_launcher" android:roundIcon="@mipmap/ic_launcher_round" android:theme="@style/AppTheme"
 android:allowBackup="false" android:fullBackupContent="false" android:dataExtractionRules="@xml/data_extraction_rules"
 android:extractNativeLibs="false">
  <provider android:name="androidx.core.content.FileProvider" android:authorities="com.example.imagecompressor.fileprovider"
   android:exported="false" android:grantUriPermissions="true">
   <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths" />
  </provider>
  <activity android:name=".MainActivity" android:exported="true"><intent-filter>
   <action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" />
  </intent-filter></activity>
 </application>
</manifest>'''


def elf(bits=64, align=16384):
    data = bytearray(256)
    data[:7] = b"\x7fELF" + bytes([2 if bits == 64 else 1, 1, 1])
    struct.pack_into("<H", data, 16, 3)
    if bits == 64:
        struct.pack_into("<Q", data, 32, 64)
        struct.pack_into("<HHH", data, 52, 64, 56, 1)
        struct.pack_into("<IIQQQQQQ", data, 64, 1, 4, 0, 0, 0, len(data), len(data), align)
    else:
        struct.pack_into("<I", data, 28, 52)
        struct.pack_into("<HHH", data, 40, 52, 32, 1)
        struct.pack_into("<IIIIIIII", data, 52, 1, 0, 0, 0, len(data), len(data), 4, align)
    return bytes(data)


def create_archive(path, kind="apk", native=None, aligned=True, compressed=False, omitted=None):
    entries = ["AndroidManifest.xml", "classes.dex", "resources.arsc"] if kind == "apk" else [
        "BundleConfig.pb", "base/manifest/AndroidManifest.xml", "base/dex/classes.dex"]
    with zipfile.ZipFile(path, "w") as z:
        for name in entries:
            if name != omitted:
                z.writestr(name, b"fixture")
        if native is not None:
            name = "lib/arm64-v8a/libexample.so" if kind == "apk" else "base/lib/arm64-v8a/libexample.so"
            entry = zipfile.ZipInfo(name)
            entry.compress_type = zipfile.ZIP_DEFLATED if compressed else zipfile.ZIP_STORED
            if aligned and not compressed:
                padding = -(z.fp.tell() + 30 + len(name)) % 16384
                if padding < 4:
                    padding += 16384
                entry.extra = struct.pack("<HH", 0xcafe, padding - 4) + bytes(padding - 4)
            z.writestr(entry, native)


class ReleaseArtifactTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.manifest = self.root / "manifest.xml"
        self.manifest.write_text(MANIFEST)
        self.apk = self.root / "release.apk"
        self.aab = self.root / "release.aab"
        self.mapping = self.root / "mapping.txt"
        self.mapping.write_text("# compiler: R8\ncom.example.App -> a.b:\n    int value -> a\n")
        create_archive(self.apk)
        create_archive(self.aab, "aab")

    def verify(self):
        return guard.verify(self.apk, self.aab, self.manifest, self.mapping)

    def mutate_manifest(self, attribute, value, element="application"):
        tree = ET.parse(self.manifest)
        tree.getroot().find(element).set(guard.ANDROID + attribute, value)
        tree.write(self.manifest)

    def test_unsigned_package_is_not_claimed_as_release_approved(self):
        report = self.verify()
        self.assertTrue(report["packagingChecksPassed"])
        self.assertFalse(report["releaseApproved"])
        self.assertIn("not performed", report["signatureVerification"])
        self.assertEqual("not present", report["apk"]["nativeStatus"])
        self.assertEqual("not performed", report["deviceAcceptance"])
        self.assertEqual(64, len(report["apk"]["sha256"]))

    def test_debuggable_testonly_and_backup_enabled_fail(self):
        for attribute in ("debuggable", "testOnly", "allowBackup"):
            with self.subTest(attribute=attribute):
                self.manifest.write_text(MANIFEST)
                self.mutate_manifest(attribute, "true")
                with self.assertRaises(ValueError):
                    self.verify()

    def test_missing_backup_exclusions_fail(self):
        self.manifest.write_text(MANIFEST.replace(' android:fullBackupContent="false"', ""))
        with self.assertRaisesRegex(ValueError, "fullBackupContent"):
            self.verify()

    def test_permission_variants_cannot_bypass_offline_contract(self):
        for tag in ("uses-permission", "uses-permission-sdk-23"):
            for permission in ("INTERNET", "READ_MEDIA_IMAGES", "MANAGE_EXTERNAL_STORAGE"):
                with self.subTest(tag=tag, permission=permission):
                    self.manifest.write_text(MANIFEST.replace("<uses-sdk", f'<{tag} android:name="android.permission.{permission}" /><uses-sdk'))
                    with self.assertRaisesRegex(ValueError, "Forbidden permissions"):
                        self.verify()

    def test_exported_provider_and_missing_grant_fail(self):
        self.mutate_manifest("exported", "true", "application/provider")
        with self.assertRaisesRegex(ValueError, "unexported"):
            self.verify()
        self.manifest.write_text(MANIFEST)
        self.mutate_manifest("grantUriPermissions", "false", "application/provider")
        with self.assertRaisesRegex(ValueError, "temporary URI"):
            self.verify()

    def test_duplicate_or_missing_launcher_fails(self):
        tree = ET.parse(self.manifest)
        app = tree.getroot().find("application")
        app.remove(app.find("activity"))
        tree.write(self.manifest)
        with self.assertRaisesRegex(ValueError, "launcher entry"):
            self.verify()

    def test_wrong_target_version_or_empty_mapping_fails(self):
        self.mutate_manifest("targetSdkVersion", "35", "uses-sdk")
        with self.assertRaisesRegex(ValueError, "targetSdkVersion"):
            self.verify()
        self.manifest.write_text(MANIFEST)
        self.mapping.write_text("# R8 mapping metadata only\n")
        with self.assertRaisesRegex(ValueError, "actual class mappings"):
            self.verify()

    def test_required_bundle_content_cannot_be_empty_or_missing(self):
        create_archive(self.aab, "aab", omitted="base/dex/classes.dex")
        with self.assertRaisesRegex(ValueError, "Missing aab entries"):
            self.verify()
        create_archive(self.aab, "aab")
        with zipfile.ZipFile(self.aab, "a") as archive, self.assertWarns(UserWarning):
            archive.writestr("BundleConfig.pb", b"")
        with self.assertRaisesRegex(ValueError, "Duplicate ZIP"):
            self.verify()

    def test_valid_native_libraries_are_checked_in_apk_and_bundle(self):
        create_archive(self.apk, native=elf())
        create_archive(self.aab, "aab", native=elf(), aligned=False)
        result = self.verify()
        self.assertEqual(0, result["apk"]["nativeLibraries"][0]["zipDataOffset"] % 16384)
        self.assertEqual([16384], result["aab"]["nativeLibraries"][0]["loadSegmentAlignments"])
        self.assertNotIn("zipDataOffset", result["aab"]["nativeLibraries"][0])

    def test_four_kilobyte_64bit_load_segment_fails(self):
        create_archive(self.apk, native=elf(align=4096))
        with self.assertRaisesRegex(ValueError, "ELF PT_LOAD is not 16 KB"):
            self.verify()

    def test_bundle_native_alignment_is_independently_checked(self):
        create_archive(self.aab, "aab", native=elf(align=4096))
        with self.assertRaisesRegex(ValueError, "ELF PT_LOAD is not 16 KB"):
            self.verify()

    def test_feature_module_native_libraries_are_not_skipped(self):
        with zipfile.ZipFile(self.aab, "a") as archive:
            archive.writestr("editor/lib/arm64-v8a/libfeature.so", elf(align=4096))
        with self.assertRaisesRegex(ValueError, "ELF PT_LOAD is not 16 KB"):
            self.verify()

    def test_32bit_load_alignment_is_not_falsely_subject_to_64bit_requirement(self):
        report = guard.inspect_elf(elf(bits=32, align=4096), "lib/armeabi-v7a/example.so")
        self.assertEqual(32, report["bits"])
        self.assertIn("not applied", report["pageSizeCheck"])

    def test_unaligned_apk_zip_entry_fails_even_with_aligned_elf(self):
        create_archive(self.apk, native=elf(), aligned=False)
        with self.assertRaisesRegex(ValueError, "16 KB ZIP aligned"):
            self.verify()

    def test_compressed_native_library_fails_when_extraction_disabled(self):
        create_archive(self.apk, native=elf(), compressed=True)
        with self.assertRaisesRegex(ValueError, "Compressed native"):
            self.verify()
        self.mutate_manifest("extractNativeLibs", "true")
        self.assertTrue(self.verify()["packagingChecksPassed"])

    def test_malformed_elf_and_truncated_load_table_fail(self):
        for data in (b"not an ELF file!", elf()[:80]):
            with self.subTest(length=len(data)):
                with self.assertRaises(ValueError):
                    guard.inspect_elf(data, "broken.so")
        data = bytearray(elf())
        struct.pack_into("<I", data, 64, 4)  # PT_NOTE is not loadable.
        with self.assertRaisesRegex(ValueError, "no loadable segments"):
            guard.inspect_elf(data, "note-only.so")

    def test_cli_writes_failure_report_and_exits_nonzero(self):
        self.mutate_manifest("debuggable", "true")
        output = self.root / "report" / "result.json"
        result = subprocess.run([sys.executable, str(Path(guard.__file__)), "--apk", str(self.apk),
            "--aab", str(self.aab), "--manifest", str(self.manifest), "--mapping", str(self.mapping),
            "--output", str(output)], capture_output=True, text=True)
        self.assertEqual(1, result.returncode)
        report = json.loads(output.read_text())
        self.assertFalse(report["packagingChecksPassed"])
        self.assertFalse(report["releaseApproved"])
        self.assertIn("debuggable", report["error"])


if __name__ == "__main__":
    unittest.main()
