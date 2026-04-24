import json
import os
import re
from datetime import datetime, timezone
from typing import Any

import httpx
import jwt
from fastapi import Depends, FastAPI, Header, HTTPException
from jwt import PyJWKClient
from pydantic import BaseModel, Field

APP_VERSION = "23.2"
ANTHROPIC_URL = "https://api.anthropic.com/v1/messages"
ANTHROPIC_VERSION = os.getenv("ANTHROPIC_VERSION", "2023-06-01")
ANTHROPIC_MODEL = os.getenv("ANTHROPIC_MODEL", "claude-3-5-sonnet-20241022")
ANTHROPIC_TIMEOUT_SECONDS = float(os.getenv("ANTHROPIC_TIMEOUT_SECONDS", "20"))
ANTHROPIC_MAX_TOKENS = int(os.getenv("ANTHROPIC_MAX_TOKENS", "1600"))

app = FastAPI(title="NebulaOps AI Engine", version=APP_VERSION)

KEYCLOAK_AUTH_ENABLED = os.getenv("KEYCLOAK_AUTH_ENABLED", "false").lower() == "true"
KEYCLOAK_JWKS_URI = os.getenv(
    "KEYCLOAK_JWKS_URI",
    "http://localhost:8180/realms/nebulaops/protocol/openid-connect/certs",
)
_jwks_client = PyJWKClient(KEYCLOAK_JWKS_URI)

class AnalyzeRequest(BaseModel):
    prompt: str = ""
    logs: list[dict[str, Any]] | list[str] = Field(default_factory=list)
    resources: list[dict[str, Any]] = Field(default_factory=list)
    signals: dict[str, Any] = Field(default_factory=dict)
    context: dict[str, Any] = Field(default_factory=dict)


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def require_keycloak_user(authorization: str | None = Header(default=None)) -> dict[str, Any]:
    if not KEYCLOAK_AUTH_ENABLED:
        return {}
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing Bearer token")
    token = authorization.removeprefix("Bearer ").strip()
    try:
        signing_key = _jwks_client.get_signing_key_from_jwt(token)
        return jwt.decode(
            token,
            signing_key.key,
            algorithms=["RS256"],
            options={"verify_aud": False},
        )
    except Exception as exc:
        raise HTTPException(status_code=401, detail="Invalid Bearer token") from exc


@app.get("/health")
def health():
    return {
        "status": "ok",
        "engine": "nebulaops-ai-engine",
        "version": APP_VERSION,
        "auth": KEYCLOAK_AUTH_ENABLED,
        "provider": "anthropic" if bool(os.getenv("ANTHROPIC_API_KEY")) else "fallback-local",
        "anthropicConfigured": bool(os.getenv("ANTHROPIC_API_KEY")),
        "generatedAt": now_iso(),
    }


@app.get("/readyz")
def readyz():
    # The service is ready even without ANTHROPIC_API_KEY: it will return explicit
    # fallback status instead of fabricated AI output.
    return {"status": "READY", "version": APP_VERSION, "anthropicConfigured": bool(os.getenv("ANTHROPIC_API_KEY"))}


@app.post("/analyze")
def analyze(req: AnalyzeRequest, user: dict[str, Any] = Depends(require_keycloak_user)):
    api_key = os.getenv("ANTHROPIC_API_KEY", "").strip()
    if api_key:
        try:
            llm = call_anthropic(req, api_key)
            llm.setdefault("provider", "anthropic")
            llm.setdefault("model", ANTHROPIC_MODEL)
            llm.setdefault("llmAvailable", True)
            llm.setdefault("live", True)
            llm.setdefault("createdAt", now_iso())
            llm.setdefault("authenticatedUser", user.get("preferred_username") or user.get("email") or user.get("sub"))
            return normalize_response(llm, req)
        except Exception as exc:
            fallback = fallback_analysis(req, f"ANTHROPIC_UNAVAILABLE: {type(exc).__name__}: {exc}")
            fallback["anthropicError"] = str(exc)
            fallback["authenticatedUser"] = user.get("preferred_username") or user.get("email") or user.get("sub")
            return fallback
    fallback = fallback_analysis(req, "ANTHROPIC_API_KEY is not configured")
    fallback["authenticatedUser"] = user.get("preferred_username") or user.get("email") or user.get("sub")
    return fallback


def call_anthropic(req: AnalyzeRequest, api_key: str) -> dict[str, Any]:
    payload = {
        "model": ANTHROPIC_MODEL,
        "max_tokens": ANTHROPIC_MAX_TOKENS,
        "temperature": 0.1,
        "system": (
            "You are NebulaOps v23.2 AI Engine. Analyze only the runtime evidence supplied by the caller. "
            "Do not invent services, pods, metrics, dates, users or incidents. Return strict JSON only with keys: "
            "incidentId, summary, rootCause, confidence, severity, blastRadius, fix, yaml, events, nodes, evidence, recommendations. "
            "If evidence is insufficient, say that explicitly and set confidence below 0.5."
        ),
        "messages": [
            {"role": "user", "content": json.dumps(request_payload(req), ensure_ascii=False, default=str)}
        ],
    }
    headers = {
        "x-api-key": api_key,
        "anthropic-version": ANTHROPIC_VERSION,
        "content-type": "application/json",
    }
    with httpx.Client(timeout=ANTHROPIC_TIMEOUT_SECONDS) as client:
        response = client.post(ANTHROPIC_URL, headers=headers, json=payload)
        response.raise_for_status()
        data = response.json()
    text = "\n".join(part.get("text", "") for part in data.get("content", []) if part.get("type") == "text")
    parsed = parse_json_object(text)
    parsed["rawProviderResponseId"] = data.get("id")
    parsed["usage"] = data.get("usage", {})
    return parsed


def request_payload(req: AnalyzeRequest) -> dict[str, Any]:
    return {
        "prompt": req.prompt,
        "logs": req.logs,
        "resources": req.resources,
        "signals": req.signals,
        "context": req.context,
        "schema": {
            "incidentId": "string",
            "summary": "string",
            "rootCause": "string",
            "confidence": "number 0..1",
            "severity": "INFO|WARN|HIGH|CRITICAL",
            "blastRadius": ["service names derived only from input"],
            "fix": "string",
            "yaml": "optional reviewed Kubernetes YAML; empty string if not enough evidence",
            "events": ["runtime evidence rows derived only from input"],
            "nodes": ["graph nodes derived only from input resources"],
            "evidence": ["strings"],
            "recommendations": ["strings"],
        },
    }


def parse_json_object(text: str) -> dict[str, Any]:
    candidate = text.strip()
    if candidate.startswith("```"):
        candidate = re.sub(r"^```(?:json)?\s*", "", candidate)
        candidate = re.sub(r"\s*```$", "", candidate)
    try:
        return json.loads(candidate)
    except json.JSONDecodeError:
        match = re.search(r"\{.*\}", candidate, re.DOTALL)
        if match:
            return json.loads(match.group(0))
        raise ValueError("Anthropic response did not contain a JSON object")


def fallback_analysis(req: AnalyzeRequest, reason: str) -> dict[str, Any]:
    evidence = input_evidence(req)
    affected = blast_radius_from_input(req)
    events = events_from_input(req)
    nodes = nodes_from_input(req)
    has_runtime_input = bool(evidence or events or nodes)
    return normalize_response({
        "incidentId": "AIOPS-23-2-" + datetime.now(timezone.utc).strftime("%H%M%S"),
        "summary": "LLM provider is unavailable; returned deterministic live-input review without fabricated conclusions.",
        "rootCause": "LLM_UNAVAILABLE: configure ANTHROPIC_API_KEY to enable model-backed RCA." if not has_runtime_input else "Needs operator review: runtime evidence is available but no LLM provider is configured.",
        "confidence": 0.0 if not has_runtime_input else 0.35,
        "severity": severity_from_text("\n".join(evidence)),
        "blastRadius": affected,
        "fix": "Configure ANTHROPIC_API_KEY and rerun analysis. Review the supplied runtime evidence before applying any action.",
        "yaml": "",
        "events": events,
        "nodes": nodes,
        "evidence": evidence,
        "recommendations": [
            "Configure ANTHROPIC_API_KEY for real LLM RCA.",
            "Use Loki, Prometheus and Kubernetes events to collect additional evidence.",
            "Do not apply autofix unless a concrete operator-reviewed manifest is supplied.",
        ],
        "provider": "fallback-local",
        "llmAvailable": False,
        "fallbackReason": reason,
        "live": has_runtime_input,
        "createdAt": now_iso(),
    }, req)


def normalize_response(payload: dict[str, Any], req: AnalyzeRequest) -> dict[str, Any]:
    payload = dict(payload)
    payload.setdefault("incidentId", "AIOPS-23-2-" + datetime.now(timezone.utc).strftime("%H%M%S"))
    payload.setdefault("summary", "Analysis completed.")
    payload.setdefault("rootCause", "No root cause provided by analysis provider.")
    payload["confidence"] = clamp_float(payload.get("confidence"), 0.0, 1.0)
    payload.setdefault("severity", severity_from_text(str(payload.get("rootCause", ""))))
    payload.setdefault("blastRadius", blast_radius_from_input(req))
    payload.setdefault("fix", "")
    payload.setdefault("yaml", "")
    payload.setdefault("events", events_from_input(req))
    payload.setdefault("nodes", nodes_from_input(req))
    payload.setdefault("evidence", input_evidence(req))
    payload.setdefault("recommendations", [])
    payload.setdefault("version", APP_VERSION)
    payload.setdefault("createdAt", now_iso())
    return payload


def clamp_float(value: Any, lo: float, hi: float) -> float:
    try:
        number = float(value)
    except Exception:
        return lo
    return max(lo, min(hi, number))


def input_evidence(req: AnalyzeRequest) -> list[str]:
    rows: list[str] = []
    if req.prompt:
        rows.append(req.prompt[:500])
    for item in req.logs[:20]:
        rows.append(str(item)[:500])
    for item in req.resources[:20]:
        rows.append(str(item)[:500])
    if req.signals:
        rows.append(json.dumps(req.signals, default=str)[:1000])
    return rows


def blast_radius_from_input(req: AnalyzeRequest) -> list[str]:
    names: list[str] = []
    for res in req.resources:
        if not isinstance(res, dict):
            continue
        for key in ("service", "serviceName", "name", "deployment", "pod", "container"):
            value = res.get(key)
            if isinstance(value, str) and value and value not in names:
                names.append(value)
    return names[:12]


def nodes_from_input(req: AnalyzeRequest) -> list[dict[str, Any]]:
    nodes: list[dict[str, Any]] = []
    for res in req.resources[:20]:
        if not isinstance(res, dict):
            continue
        name = str(res.get("name") or res.get("service") or res.get("deployment") or "resource")
        nodes.append({"id": name, "label": name, "type": str(res.get("kind") or res.get("type") or "resource"), "status": str(res.get("status") or res.get("phase") or "unknown")})
    return nodes


def events_from_input(req: AnalyzeRequest) -> list[dict[str, Any]]:
    events: list[dict[str, Any]] = []
    for item in req.logs[:20]:
        text = str(item)
        events.append({"time": now_iso(), "service": "runtime", "severity": severity_from_text(text), "title": text[:160], "status": "observed", "recommendation": "Review this live evidence row."})
    return events


def severity_from_text(text: str) -> str:
    lowered = text.lower()
    if any(x in lowered for x in ("crashloopbackoff", "oomkilled", "imagepullbackoff", "fatal", "critical")):
        return "CRITICAL"
    if any(x in lowered for x in ("error", "failed", "timeout", "connection refused")):
        return "HIGH"
    if "warn" in lowered:
        return "WARN"
    return "INFO"
