# NebulaOps v21.2.1 — Patch Release

Targeted fixes on top of v21.2 for two user-visible gaps.

## Fixed

### 1. SECURITY section now shows real data

**Problem in v21.2**: `/api/platform/devsecops` returned `scans` as
`{target, count, live}` and `cves` as `{id, severity, pkg, title}`, but the
Angular frontend expects `{tool, target, status, critical, high, medium, duration}`
for scans and `{cve, packageName, severity, image, fixVersion, exploit}` for CVEs.
The mismatch caused the entire SECURITY / COMPLIANCE / VULNERABILITIES section
to render empty even when trivy returned valid output.

**Fix**:
- `PlatformLiveController.devsecopsEnriched()` rewritten to emit the exact
  shape Angular consumes, including derived counts (critical/high/medium per
  scan), synthesized compliance controls (CIS / NIST / SOC2) computed from
  real scan data, and a threat radar derived from actual CVE positions.
- When trivy is unreachable, a clear "queued" placeholder scan is returned
  so the panel is never empty.
- Added `loadDevSecOps()` trigger on tab switch (SECURITY / COMPLIANCE /
  VULNERABILITIES) — previously only loaded on app boot.
- Real-time risk score now computed client-side from live scan counts
  (was hard-coded to 87).
- Live/offline pill in the panel header so the state is always obvious.

### 2. AI OPS connection state surfaced

The AI OPS cockpit shows a `connected / offline · fallback` status pill in
its header. When `/api/ai-ops/analyze` is unreachable, the panel still
renders meaningful content via the existing `fallbackAiOps()` synthetic
analysis — now it's clear *why*. (The tab was never removed; it lives in
the home launchers, top tabs, sidebar, and hero quick-actions.)

## Changed

- Visual: connection-status pills (`.conn-status.live` / `.offline`) now
  appear in Security and AI OPS panels for instant feedback.
- All version strings bumped from 21.2.0 → 21.2.1.
