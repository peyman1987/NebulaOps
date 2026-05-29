#!/usr/bin/env python3
"""NebulaOps v24.1 live-only guard.

The goal is not to reject configuration metadata, empty-state text, docs, or test mocks.
It rejects bundled operational records, known demo endpoints, placeholder users, and
unsafe process-runner patterns that can make live endpoints block until gateway timeout.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []

TEXT_EXTENSIONS = {
    ".java", ".ts", ".js", ".html", ".css", ".scss", ".json", ".yml", ".yaml",
    ".properties", ".sh", ".md", ".xml", ".ftl", ".py", ".cjs", ".mjs"
}

EXCLUDED_PARTS = {
    ".git", "node_modules", "dist", "target", "build", ".angular", ".runtime-tools"
}
EXCLUDED_FILES = {
    "frontend/package-lock.json",
    "scripts/verify-live-only-runtime.py",
    "scripts/wsl/verify-extensions-live-only.sh",  # intentionally contains forbidden-pattern literals
    "scripts/validate-package.py",                # may contain stale-pattern literals
}
EXCLUDED_PREFIXES = (
    "docs/",
    "reports/",
)

FORBIDDEN_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    ("public demo API endpoint", re.compile(r"jsonplaceholder|mockable|reqres\.in|dummyjson|api\.example\.com|staging\.api\.example\.com|prod\.api\.example\.com", re.I)),
    ("legacy seeded runtime record", re.compile(r"run-0094|eks-cluster|\bdefault-org\b|k8s-snapshot\.json|portfolio", re.I)),
    ("personal placeholder account", re.compile(r"peyman@nebulaops\.local|admin@nebulaops\.dev", re.I)),
    ("hard-coded local admin shortcut", re.compile(r"isDevAdmin\s*\(|\"local-admin\"|admin@nebulaops\.dev|\"peyman\"", re.I)),
    ("frontend generated mock rows", re.compile(r"(?:const|let|var)\s+(?:rows|items|records|incidents|tasks|containers|pods)\s*=\s*\[\s*\{", re.I | re.S)),
]

ITALIAN_UI = re.compile(
    r"\b(Titolo|Descrizione|Applicazione|pagine|metriche|Preparare deploy|team o persona|"
    r"Note operative|Aggiornato|Elemento eliminato|Stato aggiornato|Nessun elemento|"
    r"Avvio consigliato|Non eseguiti|Funzionalità incluse|Persistenza dati)\b",
    re.I,
)

BLOCKING_PROCESS_PATTERNS = [
    ("stdout/stderr collected before timeout", re.compile(r"out\.lines\(\)\.reduce|err\.lines\(\)\.reduce")),
    ("direct process stream readAllBytes without async drain", re.compile(r"process\.get(Input|Error)Stream\(\)\.readAllBytes\(\)")),
]


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def should_scan(path: Path) -> bool:
    r = rel(path)
    if r in EXCLUDED_FILES or any(r.startswith(p) for p in EXCLUDED_PREFIXES) or path.name.startswith("V24.1_"):
        return False
    if any(part in EXCLUDED_PARTS for part in path.parts):
        return False
    if path.suffix not in TEXT_EXTENSIONS:
        return False
    if path.name.endswith(".zip"):
        return False
    return True


def read_text(path: Path) -> str | None:
    try:
        return path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return None


for path in ROOT.rglob("*"):
    if not path.is_file() or not should_scan(path):
        continue
    text = read_text(path)
    if text is None:
        continue
    r = rel(path)
    for label, pattern in FORBIDDEN_PATTERNS:
        match = pattern.search(text)
        if match:
            errors.append(f"{label} in {r}: {match.group(0)[:80]!r}")
    if r.startswith(("frontend/", "backend/spring-mvc-service/", "extensions/apiforge/")):
        match = ITALIAN_UI.search(text)
        if match:
            errors.append(f"non-English UI/documentation string in {r}: {match.group(0)!r}")
    if r.startswith(("backend/", "extensions/")):
        for label, pattern in BLOCKING_PROCESS_PATTERNS:
            if pattern.search(text):
                # Allow the explicit async helper that is invoked from a CompletableFuture.
                if label.startswith("direct") and "readProcessOutput(Process process)" in text and "CompletableFuture" in text:
                    continue
                errors.append(f"unsafe command runner pattern ({label}) in {r}")

# APIForge must start empty; operator-created/imported data can be mounted later.
for data_file in [
    ROOT / "extensions/apiforge/data/collections.json",
    ROOT / "extensions/apiforge/data/environments.json",
    ROOT / "extensions/apiforge/data/history.json",
]:
    if data_file.exists():
        try:
            data = json.loads(data_file.read_text(encoding="utf-8"))
        except Exception as exc:
            errors.append(f"invalid APIForge runtime data file {rel(data_file)}: {exc}")
            continue
        if data != []:
            errors.append(f"APIForge runtime data must start empty: {rel(data_file)}")

# Keycloak realm may keep an admin bootstrap account, but must not include personal/operator placeholders.
realm = ROOT / "infrastructure/keycloak/realm-nebulaops.json"
if realm.exists():
    try:
        realm_json = json.loads(realm.read_text(encoding="utf-8"))
        for user in realm_json.get("users", []):
            username = str(user.get("username", ""))
            email = str(user.get("email", ""))
            if username.lower() == "peyman" or email.lower() == "peyman@nebulaops.local":
                errors.append("Keycloak realm contains personal placeholder user peyman")
    except Exception as exc:
        errors.append(f"invalid Keycloak realm JSON: {exc}")

# Frontend source assets must not carry offline snapshot JSON files.
asset_snapshots = [p for p in (ROOT / "frontend/src/assets").glob("*.json") if "snapshot" in p.name.lower()]
for p in asset_snapshots:
    errors.append(f"frontend offline snapshot asset is not allowed: {rel(p)}")

if errors:
    print("Live-only runtime verification FAILED")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("Live-only runtime verification OK")
print("No bundled mock/static operational records, demo endpoints, personal placeholders, or unsafe process runners detected")
