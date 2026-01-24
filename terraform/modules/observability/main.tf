variable "namespace" { type = string }
resource "local_file" "grafana_note" {
  filename = "${path.root}/../infrastructure/observability/V22_4_OBSERVABILITY_NOTE.md"
  content  = "# NebulaOps v22.4 Observability\n\nNamespace: ${var.namespace}\n\n- Prometheus: http://nebulaops.localhost/prometheus/\n- Grafana: http://nebulaops.localhost/grafana/\n- FE tab: OBSERVABILITY with auto-refresh logs\n"
}
