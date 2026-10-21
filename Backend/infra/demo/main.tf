terraform {
  required_version = ">= 1.9.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
  # Initialize with a pre-created, encrypted S3 backend. Do not store state in Git.
  backend "s3" {}
}

provider "aws" {
  region = var.region
  default_tags {
    tags = { Project = "AiTherapy", Environment = "demo", ManagedBy = "Terraform" }
  }
}

data "aws_caller_identity" "current" {}

variable "region" {
  type    = string
  default = "ap-south-1"
  validation {
    condition     = var.region == "ap-south-1"
    error_message = "The selected demo region is Mumbai (ap-south-1)."
  }
}

variable "create_service" {
  type        = bool
  default     = false
  description = "Bootstrap ECR first. Set true only after the image is mirrored and secrets exist."
}

variable "image_digest" {
  type        = string
  default     = ""
  description = "Immutable ECR digest, not the Docker Hub digest."
  validation {
    condition     = var.image_digest == "" || can(regex("^sha256:[0-9a-f]{64}$", var.image_digest))
    error_message = "Use an ECR sha256 digest."
  }
}

variable "secret_arns" {
  type        = map(string)
  default     = {}
  description = "Existing Secrets Manager or SSM SecureString ARNs keyed by environment variable. No values in Terraform."
}

variable "migration_secret_arns" {
  type        = map(string)
  default     = {}
  description = "Separate DDL user/password secret ARNs for the one-off migration runner."
}

variable "bedrock_model_arns" {
  type        = list(string)
  default     = []
  description = "Exact model and inference-profile ARNs approved for demo use."
}

variable "bedrock_free_model_id" {
  type    = string
  default = ""
}
variable "bedrock_pro_model_id" {
  type    = string
  default = ""
}
variable "bedrock_summary_model_id" {
  type    = string
  default = ""
}

variable "max_instances" {
  type    = number
  default = 2
  validation {
    condition     = var.max_instances >= 1 && var.max_instances <= 3
    error_message = "Demo scaling is capped at 1 to 3 instances."
  }
}

variable "budget_email" {
  type        = string
  default     = ""
  description = "Email address for AWS monthly budget and App Runner alarm notifications."
}

variable "monthly_budget_usd" {
  type    = number
  default = 15
  validation {
    condition     = var.monthly_budget_usd >= 1 && var.monthly_budget_usd <= 100
    error_message = "Choose a monthly alert limit between $1 and $100."
  }
}

locals {
  required_secrets = toset([
    "DATABASE_URL", "DATABASE_USER", "DATABASE_PASSWORD", "REDIS_HOST", "REDIS_PASSWORD",
    "JWT_PRIVATE_KEY", "JWT_PUBLIC_KEY", "ENCRYPTION_MASTER_KEY",
    "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET", "RAZORPAY_KEY_ID", "RAZORPAY_KEY_SECRET",
    "RAZORPAY_WEBHOOK_SECRET", "CORS_ALLOWED_ORIGINS"
  ])
  secrets_valid = alltrue([
    for key in local.required_secrets :
    contains(keys(var.secret_arns), key) && can(regex("^arn:aws:(secretsmanager|ssm):ap-south-1:[0-9]{12}:", lookup(var.secret_arns, key, "")))
  ])
  migration_secrets_valid = alltrue([
    for key in ["DATABASE_USER", "DATABASE_PASSWORD"] :
    contains(keys(var.migration_secret_arns), key) && can(regex("^arn:aws:(secretsmanager|ssm):ap-south-1:[0-9]{12}:", lookup(var.migration_secret_arns, key, "")))
  ])
}

resource "aws_ecr_repository" "backend" {
  name                 = "aitherapy-backend-demo"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = false
  image_scanning_configuration { scan_on_push = true }
  encryption_configuration { encryption_type = "AES256" }
}

resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the most recent 10 demo images"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 10 }
      action       = { type = "expire" }
    }]
  })
}

resource "aws_iam_role" "access" {
  name = "aitherapy-demo-apprunner-ecr"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = "sts:AssumeRole", Principal = { Service = "build.apprunner.amazonaws.com" } }]
  })
}

resource "aws_iam_role_policy_attachment" "access" {
  role       = aws_iam_role.access.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSAppRunnerServicePolicyForECRAccess"
}

resource "aws_iam_role" "instance" {
  name = "aitherapy-demo-apprunner-instance"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = "sts:AssumeRole", Principal = { Service = "tasks.apprunner.amazonaws.com" } }]
  })
}

resource "aws_iam_role_policy" "instance" {
  count = length(var.secret_arns) + length(var.bedrock_model_arns) > 0 ? 1 : 0
  name  = "demo-runtime"
  role  = aws_iam_role.instance.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat(
      length(var.secret_arns) == 0 ? [] : [{
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue", "ssm:GetParameters"]
        Resource = values(var.secret_arns)
      }],
      length(var.bedrock_model_arns) == 0 ? [] : [{
        Effect   = "Allow"
        Action   = ["bedrock:InvokeModel", "bedrock:InvokeModelWithResponseStream"]
        Resource = var.bedrock_model_arns
      }]
    )
  })
}

resource "aws_apprunner_auto_scaling_configuration_version" "demo" {
  auto_scaling_configuration_name = "aitherapy-demo"
  max_concurrency                 = 30
  max_size                        = var.max_instances
  min_size                        = 1
}

resource "aws_apprunner_service" "api" {
  count        = var.create_service ? 1 : 0
  service_name = "aitherapy-demo-api"
  source_configuration {
    authentication_configuration { access_role_arn = aws_iam_role.access.arn }
    auto_deployments_enabled = false
    image_repository {
      image_identifier      = "${aws_ecr_repository.backend.repository_url}@${var.image_digest}"
      image_repository_type = "ECR"
      image_configuration {
        port = "8080"
        runtime_environment_variables = {
          SPRING_PROFILES_ACTIVE          = "production"
          SERVER_FORWARD_HEADERS_STRATEGY = "framework"
          SPRING_FLYWAY_ENABLED           = "false"
          REDIS_SSL                       = "true"
          BEDROCK_REGION                  = var.region
          AI_BEDROCK_ENABLED              = "true"
          AI_ROUTING_ENABLED              = "true"
          AI_ROUTING_POLICY_ID            = "demo-v1"
          AI_CHAT_V2_ENABLED              = "true"
          AI_EXTRACTION_WORKER_ENABLED    = "true"
          BEDROCK_FREE_MODEL_ID           = var.bedrock_free_model_id
          BEDROCK_PRO_MODEL_ID            = var.bedrock_pro_model_id
          BEDROCK_SUMMARY_MODEL_ID        = var.bedrock_summary_model_id
          DEV_MODE                        = "false"
        }
        runtime_environment_secrets = var.secret_arns
      }
    }
  }
  instance_configuration {
    cpu               = "1024"
    memory            = "2048"
    instance_role_arn = aws_iam_role.instance.arn
  }
  auto_scaling_configuration_arn = aws_apprunner_auto_scaling_configuration_version.demo.arn
  health_check_configuration {
    protocol            = "HTTP"
    path                = "/api/v1/actuator/health/readiness"
    interval            = 10
    timeout             = 5
    healthy_threshold   = 1
    unhealthy_threshold = 5
  }
  network_configuration {
    egress_configuration { egress_type = "DEFAULT" }
    ingress_configuration { is_publicly_accessible = true }
  }
  lifecycle {
    precondition {
      condition     = local.secrets_valid && local.migration_secrets_valid && var.image_digest != "" && length(var.bedrock_model_arns) > 0 && var.bedrock_free_model_id != "" && var.bedrock_pro_model_id != "" && var.bedrock_summary_model_id != "" && can(regex("^[^@ ]+@[^@ ]+\\.[^@ ]+$", var.budget_email))
      error_message = "Before creating the service, supply the ECR digest, all required secret ARNs, and approved Bedrock models."
    }
    # Deployment workflow owns image revisions after initial creation.
    ignore_changes = [source_configuration[0].image_repository[0].image_identifier]
  }
  depends_on = [aws_iam_role_policy_attachment.access, aws_iam_role_policy.instance]
}

output "ecr_repository_url" { value = aws_ecr_repository.backend.repository_url }
output "service_arn" { value = var.create_service ? aws_apprunner_service.api[0].arn : null }
output "service_url" { value = var.create_service ? "https://${aws_apprunner_service.api[0].service_url}" : null }
output "deploy_role_arn" { value = aws_iam_role.deploy.arn }
