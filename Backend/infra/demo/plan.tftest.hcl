mock_provider "aws" {
  override_during = plan
  mock_data "aws_caller_identity" {
    defaults = { account_id = "123456789012" }
  }
}

run "bootstrap_without_service" {
  command = plan
  assert {
    condition     = length(aws_apprunner_service.api) == 0 && aws_ecr_repository.backend.name == "aitherapy-backend-demo"
    error_message = "Bootstrap must create the registry without an App Runner service."
  }
}

run "service_after_migration" {
  command = plan
  variables {
    create_service = true
    image_digest   = "sha256:616ce6d782766107abe713a84cf3782a1cf865afb788013b4668718a4242f585"
    budget_email   = "demo@example.com"
    secret_arns = { for name in [
      "DATABASE_URL", "DATABASE_USER", "DATABASE_PASSWORD", "REDIS_HOST", "REDIS_PASSWORD",
      "JWT_PRIVATE_KEY", "JWT_PUBLIC_KEY", "ENCRYPTION_MASTER_KEY",
      "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET", "RAZORPAY_KEY_ID", "RAZORPAY_KEY_SECRET",
      "RAZORPAY_WEBHOOK_SECRET"
    ] : name => "arn:aws:ssm:ap-south-1:123456789012:parameter/demo/${name}" }
    migration_secret_arns = {
      DATABASE_USER     = "arn:aws:ssm:ap-south-1:123456789012:parameter/demo/MIGRATION_DATABASE_USER"
      DATABASE_PASSWORD = "arn:aws:ssm:ap-south-1:123456789012:parameter/demo/MIGRATION_DATABASE_PASSWORD"
    }
    bedrock_model_arns       = ["arn:aws:bedrock:ap-south-1:123456789012:inference-profile/demo"]
    bedrock_free_model_id    = "demo-free"
    bedrock_pro_model_id     = "demo-pro"
    bedrock_summary_model_id = "demo-summary"
  }
  assert {
    condition     = length(aws_apprunner_service.api) == 1 && aws_apprunner_auto_scaling_configuration_version.demo.min_size == 1
    error_message = "The activated service must have one warm instance."
  }
}
