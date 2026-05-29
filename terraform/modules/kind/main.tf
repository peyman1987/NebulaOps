variable "cluster_name" { type = string }
variable "namespace" { type = string }

resource "local_file" "kind_config" {
  filename = "${path.root}/../infrastructure/kind/generated-${var.cluster_name}.yaml"
  content = yamlencode({
    kind       = "Cluster"
    apiVersion = "kind.x-k8s.io/v1alpha4"
    name       = var.cluster_name
    nodes = [{
      role = "control-plane"
      extraPortMappings = [
        { containerPort = 80, hostPort = 8080, protocol = "TCP" },
        { containerPort = 443, hostPort = 8443, protocol = "TCP" }
      ]
    }]
  })
}

resource "local_file" "namespace" {
  filename = "${path.root}/../infrastructure/kubernetes/namespace.generated.yaml"
  content  = "apiVersion: v1\nkind: Namespace\nmetadata:\n  name: ${var.namespace}\n  labels:\n    app.kubernetes.io/part-of: nebulaops-v24-1\n"
}

output "kind_config_path" { value = local_file.kind_config.filename }
