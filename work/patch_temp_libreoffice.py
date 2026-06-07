from pathlib import Path
import os
import shutil
import subprocess


SOURCE_APP = Path(
    "/Users/admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/native/"
    "libreoffice-headless/libreoffice/LibreOfficeDev.app"
)
TARGET_APP = Path("/private/tmp/image-compressor-libreoffice/LibreOfficeDev.app")
FRAMEWORKS = TARGET_APP / "Contents" / "Frameworks"
SEARCH_ROOTS = [
    Path(
        "/Users/admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/native/"
        "poppler/poppler/lib"
    ),
    Path(
        "/Users/admin/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/"
        "lib/python3.12/site-packages/PIL/.dylibs"
    ),
]


def dependencies(path: Path) -> list[str]:
    result = subprocess.run(["otool", "-L", str(path)], capture_output=True, text=True)
    if result.returncode != 0:
        return []
    values = []
    for line in result.stdout.splitlines()[1:]:
        value = line.strip().split(" (compatibility", 1)[0]
        if value:
            values.append(value)
    return values


def replacement_for(binary: Path, leaf: str) -> str:
    if FRAMEWORKS in binary.parents:
        return f"@loader_path/{leaf}"
    return f"@executable_path/../Frameworks/{leaf}"


def find_library(leaf: str) -> Path | None:
    local = FRAMEWORKS / leaf
    if local.exists():
        return local
    for root in SEARCH_ROOTS:
        candidate = root / leaf
        if candidate.exists():
            return candidate
    return None


def main():
    if TARGET_APP.exists():
        shutil.rmtree(TARGET_APP.parent)
    TARGET_APP.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(SOURCE_APP, TARGET_APP, symlinks=True)

    queue = [
        path
        for root in (TARGET_APP / "Contents" / "MacOS", FRAMEWORKS)
        for path in root.iterdir()
        if path.is_file() and os.access(path, os.X_OK)
    ]
    seen: set[Path] = set()
    missing: set[str] = set()

    while queue:
        binary = queue.pop(0)
        if binary in seen:
            continue
        seen.add(binary)
        for dependency in dependencies(binary):
            is_homebrew = dependency.startswith("/opt/homebrew/")
            is_rpath = dependency.startswith("@rpath/")
            if not is_homebrew and not is_rpath:
                continue
            leaf = Path(dependency).name
            if is_rpath and leaf == binary.name:
                continue
            source = find_library(leaf)
            if source is None:
                if is_homebrew:
                    missing.add(dependency)
                continue
            target = FRAMEWORKS / leaf
            if not target.exists():
                shutil.copy2(source, target)
                target.chmod(0o755)
                queue.append(target)
            subprocess.run(
                [
                    "install_name_tool",
                    "-change",
                    dependency,
                    replacement_for(binary, leaf),
                    str(binary),
                ],
                check=True,
            )

    if missing:
        print("Missing libraries:")
        for value in sorted(missing):
            print(value)
        raise SystemExit(1)

    print(TARGET_APP / "Contents" / "MacOS" / "soffice")


if __name__ == "__main__":
    main()
