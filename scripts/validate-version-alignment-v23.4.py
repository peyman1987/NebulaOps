#!/usr/bin/env python3
"""NebulaOps v23.4 release-alignment guard.

This script verifies that project-owned identifiers, release paths and runtime
entry points are aligned to the public v23.4 / 23.4.0 release. Dependency
versions and Go toolchain versions are intentionally treated separately.
"""
from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PUBLIC_RELEASE = "23.4"
PUBLIC_VERSION = "23.4.0"
NAME_MINOR = "v23-4"
DOT_MINOR = "v23.4"
UNDERSCORE_MINOR = "V23_4"

# Build old identifiers without embedding them as contiguous literals in this guard.
old_dot = "23" + "." + "3"
old_dash = "23" + "-" + "3"
old_under = "V23" + "_" + "3"
old_version = old_dot + ".0"
old_markers = [
    old_version,
    "v" + old_dot,
    "V" + old_dot,
    "v" + old_dash,
    "nebulaops-v" + old_dash,
    "nebulaops-v" + old_dot,
    old_under,
]

errors: list[str] = []

SKIP_PARTS = {".git", "node_modules", "target", ".angular", ".cache"}
TEXT_SKIP_SUFFIXES = {
    "package-lock.json",  # third-party dependency versions can legitimately contain similar numbers.
    "go.mod",             # Go toolchain version is not a NebulaOps release identifier.
    "go.work",
}
TEXT_SKIP_REL = {
    "scripts/validate-version-alignment-v23.4.py",  # this guard constructs stale markers dynamically.
}

required_release_files = [
    "README.md",
    "START_HERE.md",
    "PROJECT_METADATA.md",
    "RELEASE_FINAL_INDEX.md",
    "RELEASE_NOTES_v23.4.md",
    "docker-compose.yml",
    "docker-compose.native-ui.yml",
    "docker-compose.prometheus-ui.yml",
    "frontend/package.json",
    "backend/pom.xml",
    "scripts/wsl/preflight-v23.4.sh",
    "scripts/wsl/repair-v23.4-frontend-remotes.sh",
    "scripts/wsl/repair-v23.4-docker-context.sh",
    "scripts/wsl/sync-v23.4-frontend-runtime.sh",
    "scripts/wsl/reset-v23.4-frontend-runtime.sh",
    "scripts/hotfix-v23.4-nginx-remote-routing.sh",
    "reports/preflight-v23.4.json",
    "frontend/src/assets/nebulaops-v23-4-shell-compat.js",
    "frontend/dist/nebulaops/browser/assets/nebulaops-v23-4-shell-compat.js",
    "docs/diagrams/nebulaops-v23-4-reverse-proxy-runtime.svg",
    "infrastructure/observability/V23_4_OBSERVABILITY_NOTE.md",
]

for rel in required_release_files:
    if not (ROOT / rel).exists():
        errors.append(f"missing v23.4 release file: {rel}")

# Stale path guard for project-owned release identifiers.
for path in ROOT.rglob("*"):
    rel = path.relative_to(ROOT).as_posix()
    if any(part in SKIP_PARTS for part in path.parts):
        continue
    for marker in old_markers:
        if marker in rel:
            errors.append(f"stale release identifier in path: {rel}")

# Stale text guard, excluding dependency/toolchain files.
for path in ROOT.rglob("*"):
    if not path.is_file() or any(part in SKIP_PARTS for part in path.parts):
        continue
    rel = path.relative_to(ROOT).as_posix()
    if rel in TEXT_SKIP_REL or path.name in TEXT_SKIP_SUFFIXES:
        continue
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    for marker in old_markers:
        if marker in text:
            errors.append(f"stale release identifier {marker!r} in {rel}")

# package.json and package-lock.json must agree on package identity and version.
for package_json in ROOT.glob("frontend/**/package.json"):
    if any(part in SKIP_PARTS for part in package_json.parts):
        continue
    rel = package_json.relative_to(ROOT).as_posix()
    data = json.loads(package_json.read_text(encoding="utf-8"))
    name = data.get("name", "")
    version = data.get("version", "")
    if version != PUBLIC_VERSION:
        errors.append(f"{rel} version is {version!r}, expected {PUBLIC_VERSION}")
    if "nebulaops-" in name and NAME_MINOR not in name:
        errors.append(f"{rel} package name is not v23-4 aligned: {name}")
    lock = package_json.with_name("package-lock.json")
    if lock.exists():
        lock_data = json.loads(lock.read_text(encoding="utf-8"))
        lock_root = lock_data.get("packages", {}).get("", {})
        for source, source_name, source_version in [
            ("lock root", lock_data.get("name"), lock_data.get("version")),
            ("packages['']", lock_root.get("name"), lock_root.get("version")),
        ]:
            if source_name != name or source_version != version:
                errors.append(
                    f"{lock.relative_to(ROOT).as_posix()} {source} mismatch: "
                    f"{source_name!r} {source_version!r}, expected {name!r} {version!r}"
                )

# Maven release versions must not retain the previous application version.
for pom in list((ROOT / "backend").glob("**/pom.xml")) + list((ROOT / "extensions").glob("**/pom.xml")):
    rel = pom.relative_to(ROOT).as_posix()
    text = pom.read_text(encoding="utf-8")
    if old_version in text or ("<version>" + old_dot) in text:
        errors.append(f"stale Maven application version in {rel}")
    if "<version>23.4.0</version>" not in text:
        errors.append(f"{rel} does not expose 23.4.0 in Maven coordinates or parent coordinates")

# Docker Compose release identity.
for compose_name in ["docker-compose.yml", "docker-compose.native-ui.yml", "docker-compose.prometheus-ui.yml"]:
    compose = ROOT / compose_name
    if compose.exists():
        text = compose.read_text(encoding="utf-8")
        if "name: nebulaops-v23-4" not in text.splitlines()[0:3]:
            errors.append(f"{compose_name} compose project name is not nebulaops-v23-4")
        if ("nebulaops-v" + old_dash) in text or ("v" + old_dash) in text:
            errors.append(f"{compose_name} contains stale Docker image/project identifier")

# Core docs and runtime entry points must advertise the aligned version.
for rel in ["README.md", "START_HERE.md", "PROJECT_METADATA.md", "RELEASE_FINAL_INDEX.md"]:
    text = (ROOT / rel).read_text(encoding="utf-8")
    if DOT_MINOR not in text and PUBLIC_VERSION not in text:
        errors.append(f"{rel} does not mention v23.4 / 23.4.0")

index_htmls = [
    ROOT / "frontend/src/index.html",
    ROOT / "frontend/dist/nebulaops/browser/index.html",
]
for index in index_htmls:
    text = index.read_text(encoding="utf-8")
    if "assets/nebulaops-v23-4-shell-compat.js" not in text:
        errors.append(f"{index.relative_to(ROOT)} does not load v23-4 shell compatibility asset")
    if "NebulaOps v23.4" not in text:
        errors.append(f"{index.relative_to(ROOT)} title/release marker is not v23.4")

# Runtime build constants and manifest versions.
api_config = ROOT / "frontend/src/app/api.config.ts"
if api_config.exists() and "APP_RELEASE = 'v23.4'" not in api_config.read_text(encoding="utf-8"):
    errors.append("frontend/src/app/api.config.ts APP_RELEASE is not v23.4")

for manifest in ROOT.glob("frontend/remotes/**/manifest.json"):
    if any(part in SKIP_PARTS for part in manifest.parts):
        continue
    data = json.loads(manifest.read_text(encoding="utf-8"))
    if data.get("version") != PUBLIC_VERSION:
        errors.append(f"{manifest.relative_to(ROOT)} manifest version is not {PUBLIC_VERSION}")

if errors:
    print("NebulaOps v23.4 version alignment FAILED")
    for err in errors:
        print(" -", err)
    sys.exit(1)

print("NebulaOps v23.4 version alignment OK")
print("project identifiers: v23.4 / 23.4.0 / nebulaops-v23-4")
