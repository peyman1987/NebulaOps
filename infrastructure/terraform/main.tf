terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.0" }
  }
}
provider "aws" { region = var.aws_region }
resource "aws_s3_bucket" "files" { bucket = var.files_bucket_name }
resource "aws_ecr_repository" "services" {
  for_each = toset(["auth-service","task-service","file-service","notification-service","gateway-service"])
  name = "nebulaops/${each.key}"
  image_scanning_configuration { scan_on_push = true }
}
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "5.13.0"
  name = "nebulaops-vpc"
  cidr = "10.20.0.0/16"
  azs = ["${var.aws_region}a", "${var.aws_region}b"]
  private_subnets = ["10.20.1.0/24", "10.20.2.0/24"]
  public_subnets  = ["10.20.101.0/24", "10.20.102.0/24"]
  enable_nat_gateway = true
}
