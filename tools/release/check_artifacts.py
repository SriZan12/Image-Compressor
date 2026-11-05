#!/usr/bin/env python3
"""Check release packaging, privacy metadata, and native 16 KB compatibility.

This is not signature verification or device acceptance. Pass a manifest decoded
from the inspected APK (for example, `apkanalyzer manifest print app.apk`).
"""

import argparse
import hashlib
import json
import re
import struct
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
PAGE_SIZE = 16 * 1024
FORBIDDEN_PERMISSIONS = {
    "android.permission.INTERNET",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.READ_MEDIA_IMAGES",
    "android.permission.READ_MEDIA_VIDEO",
    "android.permission.READ_MEDIA_AUDIO",
    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
    "android.permission.ACCESS_MEDIA_LOCATION",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def inspect_manifest(path, min_sdk=31, target_sdk=37):
    root = ET.parse(path).getroot()
    require(root.tag == "manifest", "Expected decoded APK manifest XML")
    package = root.get("package")
    require(bool(package), "Manifest has no application ID")
    version_code = root.get(ANDROID + "versionCode", "")
    require(version_code.isdigit() and int(version_code) > 0, "versionCode must be a positive integer")
    require(bool(root.get(ANDROID + "versionName")), "versionName is missing")
    sdk = root.find("uses-sdk")
    require(sdk is not None, "Manifest is missing uses-sdk")
    require(sdk.get(ANDROID + "minSdkVersion") == str(min_sdk), f"Expected minSdkVersion {min_sdk}")
    require(sdk.get(ANDROID + "targetSdkVersion") == str(target_sdk), f"Expected targetSdkVersion {target_sdk}")
    apps = root.findall("application")
    require(len(apps) == 1, "Manifest must contain exactly one application")
    app = apps[0]
    require(app.get(ANDROID + "debuggable", "false") == "false", "Release must not be debuggable")
    require(app.get(ANDROID + "testOnly", "false") == "false", "Release must not be testOnly")
    require(app.get(ANDROID + "allowBackup") == "false", "Private images must not be backed up")
    require(app.get(ANDROID + "fullBackupContent") == "false", "fullBackupContent must be false")
    require(bool(app.get(ANDROID + "dataExtractionRules")), "Device-transfer exclusion rules are missing")
    for attr in ("name", "label", "icon", "roundIcon", "theme"):
        require(bool(app.get(ANDROID + attr)), f"Application {attr} is missing")
    permissions = sorted({item.get(ANDROID + "name", "") for item in root if item.tag.startswith("uses-permission")})
    require(not FORBIDDEN_PERMISSIONS.intersection(permissions),
            f"Forbidden permissions: {sorted(FORBIDDEN_PERMISSIONS.intersection(permissions))}")
    providers = app.findall("provider")
    require(bool(providers), "Expected a scoped FileProvider")
    for provider in providers:
        require(provider.get(ANDROID + "exported") == "false", "Every content provider must be explicitly unexported")
    file_providers = [p for p in providers if p.get(ANDROID + "authorities") == package + ".fileprovider"]
    require(len(file_providers) == 1, "Expected exactly one app-owned FileProvider authority")
    file_provider = file_providers[0]
    require(file_provider.get(ANDROID + "grantUriPermissions") == "true", "FileProvider must grant explicit temporary URI access")
    require(any(m.get(ANDROID + "name") == "android.support.FILE_PROVIDER_PATHS" and m.get(ANDROID + "resource")
                for m in file_provider.findall("meta-data")), "FileProvider paths metadata is missing")
    launchers = []
    for activity in app.findall("activity") + app.findall("activity-alias"):
        for intent in activity.findall("intent-filter"):
            actions = {n.get(ANDROID + "name") for n in intent.findall("action")}
            categories = {n.get(ANDROID + "name") for n in intent.findall("category")}
            if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in categories:
                require(activity.get(ANDROID + "exported") == "true", "Launcher must be explicitly exported")
                launchers.append(activity.get(ANDROID + "name"))
    require(len(launchers) == 1 and bool(launchers[0]), "Expected exactly one launcher entry point")
    return {"applicationId": package, "versionCode": int(version_code),
            "versionName": root.get(ANDROID + "versionName"), "minSdk": min_sdk,
            "targetSdk": target_sdk, "debuggable": False, "allowBackup": False,
            "permissions": permissions, "providerCount": len(providers), "launcher": launchers[0],
            "extractNativeLibs": app.get(ANDROID + "extractNativeLibs", "platform default")}


def inspect_elf(data, name):
    require(len(data) >= 16 and data[:4] == b"\x7fELF", f"Not an ELF native library: {name}")
    elf_class, encoding, version = data[4:7]
    require(elf_class in (1, 2) and encoding in (1, 2) and version == 1, f"Unsupported ELF header: {name}")
    endian = "<" if encoding == 1 else ">"
    bits = 64 if elf_class == 2 else 32
    header_size = 64 if bits == 64 else 52
    require(len(data) >= header_size, f"Truncated ELF header: {name}")
    elf_type = struct.unpack_from(endian + "H", data, 16)[0]
    require(elf_type == 3, f"Expected a shared-object ELF: {name}")
    if bits == 64:
        phoff = struct.unpack_from(endian + "Q", data, 32)[0]
        phentsize, phnum = struct.unpack_from(endian + "HH", data, 54)
        expected_entry_size = 56
    else:
        phoff = struct.unpack_from(endian + "I", data, 28)[0]
        phentsize, phnum = struct.unpack_from(endian + "HH", data, 42)
        expected_entry_size = 32
    require(phnum > 0 and phnum != 0xffff, f"Missing or unsupported ELF program-header count: {name}")
    require(phentsize == expected_entry_size, f"Invalid ELF program-header size: {name}")
    require(phoff >= header_size and phoff + phnum * phentsize <= len(data), f"Truncated ELF program headers: {name}")
    alignments = []
    for index in range(phnum):
        offset = phoff + index * phentsize
        p_type = struct.unpack_from(endian + "I", data, offset)[0]
        if p_type != 1:  # PT_LOAD only; PT_NOTE/RELRO have different alignment rules.
            continue
        if bits == 64:
            p_offset, p_vaddr, _, filesz, memsz, align = struct.unpack_from(endian + "QQQQQQ", data, offset + 8)
        else:
            p_offset, p_vaddr, _, filesz, memsz, _, align = struct.unpack_from(endian + "IIIIIII", data, offset + 4)
        require(filesz <= memsz and p_offset + filesz <= len(data), f"Truncated ELF load segment: {name}")
        require(align > 0 and align & (align - 1) == 0, f"Invalid ELF segment alignment: {name}")
        require(p_offset % align == p_vaddr % align, f"ELF offset/address alignment mismatch: {name}")
        if bits == 64:
            require(align >= PAGE_SIZE, f"64-bit ELF PT_LOAD is not 16 KB aligned: {name} ({align})")
        alignments.append(align)
    require(bool(alignments), f"ELF contains no loadable segments: {name}")
    return {"bits": bits, "loadSegmentAlignments": alignments,
            "pageSizeCheck": "16 KB compatible load alignment" if bits == 64 else "32-bit ABI; 16 KB ELF requirement not applied"}


def zip_data_offset(stream, entry):
    stream.seek(entry.header_offset)
    header = stream.read(30)
    require(len(header) == 30 and header[:4] == b"PK\x03\x04", f"Invalid ZIP local header: {entry.filename}")
    filename_size, extra_size = struct.unpack_from("<HH", header, 26)
    return entry.header_offset + 30 + filename_size + extra_size


def inspect_archive(path, kind, extract_native_libs):
    required = {"AndroidManifest.xml", "classes.dex", "resources.arsc"} if kind == "apk" else {
        "BundleConfig.pb", "base/manifest/AndroidManifest.xml", "base/dex/classes.dex"}
    native = []
    with zipfile.ZipFile(path) as archive, Path(path).open("rb") as raw:
        entries = archive.infolist()
        names = [entry.filename for entry in entries]
        require(len(names) == len(set(names)), f"Duplicate ZIP entries in {kind}")
        require(required.issubset(names), f"Missing {kind} entries: {sorted(required - set(names))}")
        for name in required:
            require(archive.getinfo(name).file_size > 0, f"Empty {kind} entry: {name}")
        require(archive.testzip() is None, f"ZIP CRC failure in {kind}")
        native_pattern = r"lib/[^/]+/[^/]+\.so" if kind == "apk" else r"[^/]+/lib/[^/]+/[^/]+\.so"
        for entry in entries:
            if re.fullmatch(native_pattern, entry.filename) is None:
                continue
            record = {"file": entry.filename, **inspect_elf(archive.read(entry), entry.filename)}
            if kind == "apk":
                uncompressed = entry.compress_type == zipfile.ZIP_STORED
                require(uncompressed or extract_native_libs != "false",
                        f"Compressed native library with extractNativeLibs=false: {entry.filename}")
                record["zipStorage"] = "uncompressed" if uncompressed else "compressed; extracted by installer"
                if uncompressed:
                    data_offset = zip_data_offset(raw, entry)
                    require(data_offset % PAGE_SIZE == 0, f"APK native data is not 16 KB ZIP aligned: {entry.filename}")
                    record["zipDataOffset"] = data_offset
            native.append(record)
    return {"file": str(path), "bytes": Path(path).stat().st_size, "sha256": sha256(path),
            "nativeLibraries": native, "nativeStatus": "checked" if native else "not present",
            "manifestFormat": "binary Android XML" if kind == "apk" else "protobuf (not decoded by this guard)"}


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify(apk, aab, manifest, mapping, min_sdk=31, target_sdk=37):
    metadata = inspect_manifest(manifest, min_sdk, target_sdk)
    mapping_text = Path(mapping).read_text(encoding="utf-8")
    require(re.search(r"^[^#\s].+ -> [^:\s]+:$", mapping_text, re.MULTILINE) is not None,
            "R8 mapping must contain actual class mappings")
    return {"packagingChecksPassed": True, "releaseApproved": False,
            "signatureVerification": "not performed; verify APK with apksigner and AAB signing separately",
            "deviceAcceptance": "not performed", "manifest": {"file": str(manifest), "sha256": sha256(manifest), **metadata},
            "mapping": {"file": str(mapping), "sha256": sha256(mapping), "bytes": Path(mapping).stat().st_size},
            "apk": inspect_archive(apk, "apk", metadata["extractNativeLibs"]),
            "aab": inspect_archive(aab, "aab", metadata["extractNativeLibs"])}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("apk", "aab", "manifest", "mapping", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--min-sdk", type=int, default=31)
    parser.add_argument("--target-sdk", type=int, default=37)
    args = parser.parse_args()
    try:
        report = verify(args.apk, args.aab, args.manifest, args.mapping, args.min_sdk, args.target_sdk)
        status = 0
    except (OSError, ValueError, ET.ParseError, zipfile.BadZipFile, RuntimeError) as error:
        report = {"packagingChecksPassed": False, "releaseApproved": False, "error": str(error)}
        status = 1
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("Release packaging checks passed; signature and device acceptance remain separate." if status == 0
          else f"Release packaging validation failed: {report['error']}")
    return status


if __name__ == "__main__":
    sys.exit(main())
