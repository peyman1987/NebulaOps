import os
from datetime import datetime
from typing import Any

import jwt
from fastapi import Depends, FastAPI, Header, HTTPException
from jwt import PyJWKClient
from pydantic import BaseModel

app = FastAPI(title="NebulaOps AI Engine", version="22.1")

KEYCLOAK_AUTH_ENABLED = os.getenv("KEYCLOAK_AUTH_ENABLED", "false").lower() == "true"
KEYCLOAK_JWKS_URI = os.getenv(
    "KEYCLOAK_JWKS_URI",
    "http://localhost:8180/realms/nebulaops/protocol/openid-connect/certs",
)
_jwks_client = PyJWKClient(KEYCLOAK_JWKS_URI)

class AnalyzeRequest(BaseModel):
    prompt: str = ""
    logs: list[dict[str, Any]] = []
    resources: list[dict[str, Any]] = []

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

def has(text: str, word: str) -> bool:
    return word.lower() in text.lower()

@app.get("/health")
def health():
    return {"status": "ok", "engine": "nebulaops-ai-engine", "version": "22.1", "auth": KEYCLOAK_AUTH_ENABLED}

@app.post("/analyze")
def analyze(req: AnalyzeRequest, user: dict[str, Any] = Depends(require_keycloak_user)):
    text = " ".join([req.prompt] + [str(x) for x in req.logs] + [str(x) for x in req.resources])
    root = "Readiness probe timeout and rollout instability."
    fix = "Patch readiness probe, verify rollout and restart gateway-service."
    if has(text, "image") or has(text, "ImagePullBackOff"):
        root = "Image tag, registry credential or pull policy mismatch."
        fix = "Verify image tag, imagePullSecrets and Helm values, then restart rollout."
    if has(text, "oom") or has(text, "OOMKilled"):
        root = "Container memory limit too low or memory leak after deploy."
        fix = "Raise memory limits, inspect heap profile and scale replicas temporarily."
    if has(text, "crash") or has(text, "CrashLoopBackOff"):
        severity = "CRITICAL"
        health = 18
    else:
        severity = "HIGH"
        health = 46
    now = datetime.utcnow().strftime("%H:%M:%S")
    return {
        "incidentId": "AIOPS-22-1-" + datetime.utcnow().strftime("%H%M%S"),
        "summary": "AI engine correlated logs, Kubernetes events and service dependencies into an incident summary.",
        "authenticatedUser": user.get("preferred_username") or user.get("email") or user.get("sub"),
        "rootCause": root,
        "confidence": 0.92,
        "blastRadius": ["frontend", "gateway-service", "task-service", "notification-service"],
        "fix": fix,
        "yaml": "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: gateway-service\n  namespace: nebulaops\nspec:\n  template:\n    spec:\n      containers:\n        - name: gateway-service\n          readinessProbe:\n            httpGet:\n              path: /actuator/health\n              port: 8080\n            initialDelaySeconds: 25\n            periodSeconds: 10\n",
        "events": [
            {"time": now, "service": "gateway-service", "severity": severity, "title": "CrashLoopBackOff / readiness failure", "status": "active", "recommendation": fix},
            {"time": now, "service": "frontend", "severity": "HIGH", "title": "5xx propagation", "status": "degraded", "recommendation": "Send traffic to healthy gateway replicas"},
            {"time": now, "service": "task-service", "severity": "MEDIUM", "title": "Queue latency spike", "status": "watch", "recommendation": "Scale workers if queue depth grows"},
            {"time": now, "service": "mongodb", "severity": "LOW", "title": "No storage anomaly", "status": "stable", "recommendation": "No action"}
        ],
        "nodes": [
            {"id": "frontend", "label": "Frontend", "type": "edge", "health": 72, "x": 14, "y": 24, "z": 1, "status": "warn"},
            {"id": "gateway", "label": "Gateway", "type": "api", "health": health, "x": 42, "y": 38, "z": 4, "status": "critical"},
            {"id": "tasks", "label": "Tasks", "type": "svc", "health": 66, "x": 69, "y": 22, "z": 2, "status": "warn"},
            {"id": "mongo", "label": "MongoDB", "type": "db", "health": 96, "x": 78, "y": 66, "z": 1, "status": "ok"},
            {"id": "notify", "label": "Notify", "type": "svc", "health": 61, "x": 30, "y": 72, "z": 3, "status": "warn"}
        ]
    }
