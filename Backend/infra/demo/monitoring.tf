resource "aws_budgets_budget" "demo" {
  count        = var.budget_email == "" ? 0 : 1
  name         = "aitherapy-demo-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 50
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.budget_email]
  }
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.budget_email]
  }
}

resource "aws_sns_topic" "alerts" {
  count = var.budget_email == "" ? 0 : 1
  name  = "aitherapy-demo-alerts"
}

resource "aws_sns_topic_subscription" "alerts" {
  count     = var.budget_email == "" ? 0 : 1
  topic_arn = aws_sns_topic.alerts[0].arn
  protocol  = "email"
  endpoint  = var.budget_email
}

resource "aws_cloudwatch_metric_alarm" "server_errors" {
  count               = var.create_service ? 1 : 0
  alarm_name          = "aitherapy-demo-api-5xx"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 2
  threshold           = 3
  metric_name         = "5xxStatusResponses"
  namespace           = "AWS/AppRunner"
  period              = 60
  statistic           = "Sum"
  treat_missing_data  = "notBreaching"
  dimensions = {
    ServiceName = aws_apprunner_service.api[0].service_name
    ServiceId   = aws_apprunner_service.api[0].service_id
  }
  alarm_actions = var.budget_email == "" ? [] : [aws_sns_topic.alerts[0].arn]
}
