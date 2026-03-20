variable "namespace" { type = string }

resource "local_file" "helm_values" {
  filename = "${path.root}/../infrastructure/helm/nebulaops/values.v23-1.generated.yaml"
  content  = <<YAML
namespace: ${var.namespace}
frontend:
  replicas: 2
  theme: holographic-terraform
backend:
  gateway:
    replicas: 2
observability:
  grafana: true
  prometheus: true
YAML
}

output "values_path" { value = local_file.helm_values.filename }
