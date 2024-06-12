variable "namespace" { type = string }
resource "local_file" "security_baseline" {
  filename = "${path.root}/../infrastructure/kubernetes/security-baseline.generated.yaml"
  content  = "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: nebulaops-security-baseline\n  namespace: ${var.namespace}\ndata:\n  nonRoot: recommended\n  secretsPolicy: examples-only\n  terraformState: local-demo\n  networkPolicy: recommended\n"
}
