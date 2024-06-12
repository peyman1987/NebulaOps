terraform {
  required_version = ">= 1.5.0"
  required_providers {
    local = { source = "hashicorp/local", version = "~> 2.5" }
    null  = { source = "hashicorp/null", version = "~> 3.2" }
  }
}

provider "local" {}
provider "null" {}

module "kind" {
  source       = "./modules/kind"
  cluster_name = var.cluster_name
  namespace    = var.namespace
}

module "observability" {
  source    = "./modules/observability"
  namespace = var.namespace
}

module "gitops" {
  source      = "./modules/gitops"
  namespace   = var.namespace
  argocd_path = "../infrastructure/argocd/application.yaml"
}

module "security" {
  source    = "./modules/security"
  namespace = var.namespace
}

module "apps" {
  source    = "./modules/apps"
  namespace = var.namespace
}
