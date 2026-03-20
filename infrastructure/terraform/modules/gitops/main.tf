variable "namespace" { type = string }
variable "argocd_path" { type = string }
resource "local_file" "argocd_overlay" {
  filename = "${path.root}/../infrastructure/argocd/v23-1-generated-note.yaml"
  content  = "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: nebulaops-v23-1-gitops-note\n  namespace: ${var.namespace}\ndata:\n  source: ${var.argocd_path}\n  managedBy: terraform\n"
}
