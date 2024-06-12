variable "namespace" { type = string }
resource "local_file" "grafana_note" {
  filename = "${path.root}/../infrastructure/observability/V18_OBSERVABILITY_NOTE.md"
  content  = "# NebulaOps v18 Observability\n\nNamespace: ${var.namespace}\n\n- Prometheus: http://localhost:9090\n- Grafana: http://localhost:3000\n- FE tab: OBSERVABILITY with auto-refresh logs\n"
}
