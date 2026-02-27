package nebulaops.governance

# NebulaOps v22.5 Policy, Approval & Governance Center
# Runtime policy rules evaluated by OPA. These are configuration rules, not UI mock data.

default allow = false
default approval_required = false

default severity = "INFO"

default outcome = "DENY"

critical_role(role) {
  lower(role) == "realm-admin"
}

critical_role(role) {
  lower(role) == "nebula-admin"
}

critical_group(group) {
  lower(group) == "platform-admins"
}

critical_group(group) {
  lower(group) == "security-admins"
}

is_admin {
  some i
  lower(input.actor.roles[i]) == "nebula-admin"
}

is_operator {
  some i
  lower(input.actor.roles[i]) == "nebula-operator"
}

is_qa {
  some i
  lower(input.actor.roles[i]) == "qa"
}

# Identity governance
approval_required {
  input.action == "identity.disableUser"
}

approval_required {
  input.action == "identity.assignRole"
  critical_role(input.payload.role)
}

approval_required {
  input.action == "identity.updateGroup"
  critical_group(input.payload.group)
}

# Task governance
approval_required {
  input.action == "task.move"
  lower(input.payload.toStatus) == "done"
  not input.payload.qaApproved
}

deny[msg] {
  input.action == "identity.disableUser"
  input.actor.username == input.target.username
  msg := "A user cannot disable their own identity account."
}

deny[msg] {
  input.action == "task.move"
  lower(input.payload.fromStatus) == "to_start"
  lower(input.payload.toStatus) == "done"
  msg := "A task cannot move directly from To start to Done."
}

deny[msg] {
  input.action == "release.promote"
  input.payload.criticalVulnerabilities > 0
  msg := "Release promotion is blocked while critical vulnerabilities are present."
}

deny[msg] {
  input.action == "runtime.delete"
  not is_admin
  msg := "Runtime delete operations require the nebula-admin role."
}

allow {
  count(deny) == 0
  not approval_required
}

allow {
  count(deny) == 0
  approval_required
  input.approval.status == "APPROVED"
}

severity = "CRITICAL" {
  count(deny) > 0
}

severity = "WARN" {
  count(deny) == 0
  approval_required
}

outcome = "DENY" {
  count(deny) > 0
}

outcome = "APPROVAL_REQUIRED" {
  count(deny) == 0
  approval_required
}

outcome = "ALLOW" {
  allow
}

reasons := [msg | deny[msg]]

approval_reason = "Operation requires governance approval." {
  approval_required
}

approval_reason = "No approval is required." {
  not approval_required
}

decision := {
  "allow": allow,
  "approvalRequired": approval_required,
  "outcome": outcome,
  "severity": severity,
  "reasons": reasons,
  "approvalReason": approval_reason,
  "policyPackage": "nebulaops.governance",
  "policyVersion": "22.5-governance-center"
}
