output "files_bucket" { value = aws_s3_bucket.files.bucket }
output "ecr_repositories" { value = [for repo in aws_ecr_repository.services : repo.repository_url] }
