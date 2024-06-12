output "cluster_name" { value = var.cluster_name }
output "namespace" { value = var.namespace }
output "kind_manifest" { value = module.kind.kind_config_path }
output "generated_values" { value = module.apps.values_path }
output "next_steps" { value = "./scripts/terraform/apply-local.sh && ./scripts/helm-install-local.sh" }
