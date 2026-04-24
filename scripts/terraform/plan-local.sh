#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT/terraform"
terraform init
terraform fmt -recursive
terraform validate
terraform plan -var-file examples/local-kind/terraform.tfvars
