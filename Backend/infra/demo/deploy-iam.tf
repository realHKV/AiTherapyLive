resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

resource "aws_iam_role" "deploy" {
  name = "aitherapy-demo-github-deploy"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRoleWithWebIdentity"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Condition = { StringEquals = {
        "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        "token.actions.githubusercontent.com:sub" = "repo:realHKV/AiTherapyLive:environment:backend-demo"
      } }
    }]
  })
}

resource "aws_iam_role_policy" "deploy" {
  name = "mirror-migrate-deploy"
  role = aws_iam_role.deploy.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat([
      {
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability", "ecr:BatchGetImage", "ecr:CompleteLayerUpload",
          "ecr:DescribeImages", "ecr:GetDownloadUrlForLayer", "ecr:InitiateLayerUpload",
          "ecr:PutImage", "ecr:UploadLayerPart"
        ]
        Resource = aws_ecr_repository.backend.arn
      },
      {
        Effect   = "Allow"
        Action   = ["apprunner:DescribeService", "apprunner:UpdateService", "apprunner:ListOperations"]
        Resource = "arn:aws:apprunner:${var.region}:${data.aws_caller_identity.current.account_id}:service/aitherapy-demo-api/*"
      }
      ], length(var.secret_arns) + length(var.migration_secret_arns) == 0 ? [] : [{
        Effect = "Allow"
        Action = ["secretsmanager:GetSecretValue", "ssm:GetParameter"]
        Resource = concat(
          [for key in ["DATABASE_URL"] : var.secret_arns[key] if contains(keys(var.secret_arns), key)],
          values(var.migration_secret_arns)
        )
    }])
  })
}
