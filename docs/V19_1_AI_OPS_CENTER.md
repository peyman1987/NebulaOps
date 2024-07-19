# AI Ops Center v19.1

## Purpose

AI Ops Center is the main v19.1 feature. It turns NebulaOps into an operational cockpit for detecting, explaining and
remediating Kubernetes incidents.

## Frontend

Tab: `AI OPS`

UI elements:

- futuristic glassmorphism cockpit;
- cyber grid background;
- holographic dependency cards;
- animated particles;
- animated beam connections between microservices;
- red pulsing node when a pod crashes;
- operational AI chat;
- visual RCA panel;
- realtime incident timeline;
- generated YAML preview;
- `AUTO FIX` button.

## Backend architecture

```text
Angular AI OPS tab
  -> Gateway /api/ai-ops/**
  -> ai-ops-service Spring Boot :8085
  -> ai-engine FastAPI :8095
```

## Spring Boot service

Path: `backend/ai-ops-service`

Endpoints:

| Method | Path                   | Description                                           |
|--------|------------------------|-------------------------------------------------------|
| POST   | `/api/ai-ops/analyze`  | Correlates prompt, logs and resources into RCA output |
| POST   | `/api/ai-ops/autofix`  | Stages a remediation patch in demo-safe mode          |
| GET    | `/api/ai-ops/playbook` | Lists AI Ops capabilities                             |

## Python AI engine

Path: `ai-engine`

Responsibilities:

- log analysis;
- anomaly detection heuristics;
- Kubernetes error explanation;
- Helm / YAML generation;
- incident summary generation;
- root cause and blast-radius scoring.

## Safe auto-fix model

The current implementation stages a fix and returns the generated patch. This is intentional for a portfolio/demo
environment. In production, connect `/api/ai-ops/autofix` to an approval workflow before running `kubectl apply`.

## Example payload

```json
{
  "prompt": "pod gateway-service CrashLoopBackOff readiness probe failed",
  "logs": [],
  "resources": []
}
```

## Example response fields

- `incidentId`
- `summary`
- `rootCause`
- `confidence`
- `blastRadius`
- `fix`
- `yaml`
- `events`
- `nodes`
