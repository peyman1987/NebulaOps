#!/usr/bin/env bash
# v23.1 Governance real-data verification: no seeded policy/approval/decision records.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

controller="backend/policy-governance-service/src/main/java/dev/nebulaops/policy/api/PolicyGovernanceController.java"
endpoints="frontend/remotes/policy-center/live-endpoints.json"
rego="infrastructure/opa/policies/governance.rego"

[ -f "$controller" ] || { log_err "Missing governance controller"; exit 1; }
[ -f "$endpoints" ] || { log_err "Missing policy-center live endpoints"; exit 1; }
[ -f "$rego" ] || { log_err "Missing OPA governance policy"; exit 1; }

if grep -RInE 'new ArrayList<.*polic|List<Map<String,Object>> policies|sample records|demo records|mock[A-Z]|mock[A-Za-z]*\(' "$controller" "$endpoints" frontend/remotes/policy-center/src frontend/remotes/policy-center/dist/browser 2>/dev/null; then
  log_err "Governance static/mock records detected"
  exit 1
fi

grep -q 'MongoTemplate' "$controller" || { log_err "Governance service must persist runtime records in MongoDB"; exit 1; }
grep -q '/v1/data/nebulaops/governance/decision' "$controller" || { log_err "Governance decisions must call OPA"; exit 1; }
grep -q 'openpolicyagent/opa' docker-compose.yml || { log_err "docker-compose.yml missing OPA service"; exit 1; }
grep -q '/api/governance/approvals' "$endpoints" || { log_err "Policy center missing approval endpoint"; exit 1; }
grep -q 'package nebulaops.governance' "$rego" || { log_err "OPA policy package mismatch"; exit 1; }

log_ok "Governance real-data mode verified: OPA-backed decisions, MongoDB runtime records, no seeded UI records"
