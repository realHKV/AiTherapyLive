# Sydney staging: one EC2 instance, no load balancer — 2026-10-27

Status: **SSM-only development host prepared; browser HTTPS and OAuth integration remain later acceptance gates**. No AWS resources created. Account `492613852824`, Region `ap-southeast-2`, Netlify frontend. Synthetic staging data only.

## First deployment: private development access

The first [EC2 stack](../infra/ec2-demo-instance.yaml) has no inbound security-group rules, no Elastic IP, and no load balancer. It uses a public subnet only for outbound package, database, Redis, and AWS API access; the automatically assigned public IPv4 changes after a stop. Administration and local API testing use Systems Manager Session Manager or port forwarding. This follows the request to defer HTTPS and avoids exposing an HTTP API to browsers. The live Netlify site cannot call this private staging host; a trusted HTTPS ingress and a workable Google callback are required before frontend integration.

At the verified Sydney rates, 17 hours/day of `t3.small` compute, 16 GiB gp3, and a public IPv4 only while running give an illustrative **$17.77/month gross** before credits and usage. The AWS Console showed $140 credits remaining on 2026-10-27, and the existing budget alerts at $15/month. The stack was validated but not executed: automatic approval review rejected creating billed EC2 and persistent IAM resources without a clearer user authorization. A former unexecuted change set that included public HTTPS ingress and an Elastic IP was deleted. Review and approve this narrower SSM-only cost and resource scope before apply.

## Why EC2

The existing Java container is x86_64 and needs a warm process plus background jobs. A `t3.small` has 2 vCPUs and 2 GiB RAM and is eligible for the post-2025 AWS Free account plan. That plan uses promotional credits for EC2 usage; “Free tier eligible” does **not** mean indefinite zero-cost compute. The account showed $140 credit remaining on 2026-10-27; recheck the balance before deployment.

One public-subnet EC2 instance can run the backend with an IAM instance profile for Bedrock and access to the existing Neon database and Upstash Redis. Use a free-tier-eligible AMI, 16 GiB gp3 EBS, T3 CPU credit mode `standard`, and AWS Systems Manager Session Manager for administration. Add an Elastic IP only with the later HTTPS ingress phase. Do not expose SSH. Keep secrets in scoped AWS Secrets Manager references or standard SSM SecureString parameters; choose the latter for this small demo where automatic rotation is not needed, and verify IAM read access. Do not put values in user data, Git, or Terraform state. Run the one-off Flyway migrator first, then set `SPRING_FLYWAY_ENABLED=false` for the server.

## HTTPS and OAuth

Netlify provides HTTPS for the frontend only. The browser must call a trusted HTTPS API. A raw EC2 IP can obtain a publicly trusted, short-lived IP certificate from Let's Encrypt with Certbot 5.4+ and automated renewal. Use an HTTPS reverse proxy on EC2 to forward to Java port 8080. The certificate expires after roughly six days, so renewal monitoring is mandatory. Do not assume a certificate can be issued for the AWS-assigned EC2 public DNS name.

Google disallows a raw IP as a web OAuth redirect host. Therefore `https://<Elastic-IP>/api/v1/login/oauth2/code/google` cannot be the Google callback. The frontend now supports a separate `VITE_OAUTH_BASE_URL`, and its Netlify build can generate narrow HTTPS proxy rules for OAuth start and callback paths when `NETLIFY_API_ORIGIN` is set. The redirect URI uses the assigned Netlify site domain. Chat and other API calls go directly to the EC2 IP HTTPS endpoint. Netlify's proxy has a 26-second timeout and cannot carry the app's 90-second chat stream. Verify cookies, Spring's generated redirect URI, callback latency, and Google's acceptance of the Netlify hostname before relying on this split route. If it fails, use a controlled DNS subdomain for direct EC2 HTTPS or revisit managed HTTPS ingress. Do not use HTTP or an untrusted certificate for OAuth or user data.

The frontend site is `https://trust-therapy-ai.netlify.app`. After an Elastic IP is allocated, use `VITE_API_BASE_URL=https://<Elastic-IP>/api/v1`, `VITE_OAUTH_BASE_URL=https://trust-therapy-ai.netlify.app/api/v1`, and `NETLIFY_API_ORIGIN=https://<Elastic-IP>`. Set `CORS_ALLOWED_ORIGINS=https://trust-therapy-ai.netlify.app` and `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI=https://trust-therapy-ai.netlify.app/api/v1/login/oauth2/code/google`. Keep Razorpay in test mode. Use only synthetic Bedrock prompts until the data-location decision is made.

## Daily demo window

The requested window is **07:00–00:00 Asia/Kolkata every day**. [The schedule template](../infra/ec2-demo-schedule.json) prepares two EventBridge Scheduler rules: stop the one EBS-backed EC2 instance at 00:00 and start it at 07:00. It accepts the instance ID after EC2 is created, uses an IAM role scoped to that instance, and has no Lambda. Deploy it in `ap-southeast-2` only when the instance exists. Check the first stop and first start in EC2; a scheduled API request does not itself prove the instance reached the desired state. Startup and application readiness may take several minutes after 07:00. The Netlify page remains online overnight, but login, chat, and other backend actions will be unavailable. Resume the instance manually for interviews outside this window.

There is no single switch that pauses every AWS service. Bedrock on-demand has no idle model charge; calls during the active window incur token charges, including background extraction or summaries. The EBS disk and reserved public IPv4 keep their charges while EC2 is stopped. Secrets Manager, logs, snapshots, and data transfer can also incur usage charges. An AWS Budget is an alert, not a hard spending cap.

## Later public HTTPS phase: gross cost, before credits and usage

For 730 hours in Sydney, with an **illustrative** `t3.small` rate of $0.0264/hour, 16 GiB gp3 at $0.096/GiB-month, and one public IPv4 at $0.005/hour:

| Component | Approximate USD/month |
| --- | ---: |
| EC2 `t3.small`, 24/7 | $19.27 |
| 16 GiB gp3 disk | $1.54 |
| One public IPv4 | $3.65 |
| **24/7 baseline before credits** | **$24.46** |
| EC2 `t3.small`, scheduled 17/24 hours | $13.65 |
| **Scheduled baseline before credits** | **$18.84** |

The scheduled estimate uses a representative 730-hour month × 17/24 running hours, saving about $5.62/month in compute. The two daily EventBridge Scheduler invocations are far below its monthly free allowance. No ALB, Fargate, or CloudFront is required. The $15 monthly budget is an alert and could trigger on gross usage. Promotional credit may cover eligible charges while available. EBS snapshots, CloudWatch logs, data transfer, and Bedrock tokens add usage cost. Secrets Manager is about $0.40 per secret per month: 13 separate secrets would add about $5.20/month, making this scheduled baseline about $24.04 before other usage. Standard SSM SecureString parameters avoid that per-secret storage fee, subject to their limits and possible KMS charges. Validate the account-specific EC2 rate and credit balance before deployment.

## Bedrock usage for a quiet resume demo

The proposed `global.amazon.nova-2-lite-v1:0` text route is estimated at **$0.30 per million input tokens and $2.50 per million output tokens** at standard on-demand rates. A 2,000-input/500-output-token chat costs about **$0.00185**; 100 such chats cost **$0.19**, and 1,000 cost **$1.85**. A larger 6,000-input/1,500-output-token chat costs about **$0.00555**. Prompt history, memory extraction, summaries, and any other model calls add tokens. These are usage examples, not a bill forecast or a hard limit; verify the live Bedrock pricing and actual model route at deployment.

For staging, set `AI_USAGE_ACCOUNT_DAILY_REQUESTS=50`, `AI_USAGE_ACCOUNT_DAILY_TOKENS=100000`, `AI_USAGE_MODEL_DAILY_REQUESTS=50`, and `AI_USAGE_MODEL_DAILY_TOKENS=100000` as conservative application guardrails. They limit the app's accounted traffic; they do not cap AWS charges from other callers or guarantee a maximum dollar spend. Keep the existing AWS budget alert, inspect Bedrock usage after the first synthetic tests, and set a lower alert threshold if the demo needs tighter cost visibility.

## Gate order

1. Run all PostgreSQL/Redis integration tests without Docker-related skips and produce an immutable x86_64 image digest.
2. Verify eligibility, remaining credits, Sydney EC2 price, and AMI. Prepare scoped IAM and secrets without values in user data.
3. After cost acceptance, create EC2; install trusted IP HTTPS and automated renewal; confirm browser trust. Deploy the daily schedule with the actual instance ID and verify its first stop/start.
4. Run migration on a disposable or backed-up staging database; start the backend with migration disabled; test health, Bedrock with synthetic prompts, streaming, and monitoring.
5. Prove the narrow Netlify OAuth proxy. Resolve its hostname/callback if it fails. Complete Razorpay test-mode and Step 17–19 acceptance gates before release.

## Sources

- [EC2 Free Tier eligibility and credit model](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-free-tier-usage.html)
- [AWS Free account plan and credits](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/free-tier.html)
- [Let's Encrypt IP certificates](https://letsencrypt.org/2026/01/15/6day-and-ip-general-availability)
- [Certbot support for IP certificates](https://letsencrypt.org/2026/03/11/shorter-certs-certbot)
- [Google OAuth redirect URI rules](https://developers.google.com/identity/protocols/oauth2/web-server)
- [Netlify proxy and 26-second limit](https://docs.netlify.com/manage/routing/redirects/rewrites-proxies/)
- [AWS public IPv4 pricing](https://aws.amazon.com/vpc/pricing/)
- [EC2 stop/start billing and persistence](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/how-ec2-instance-stop-start-works.html)
- [EventBridge Scheduler time zones](https://docs.aws.amazon.com/scheduler/latest/UserGuide/schedule-types.html)
- [EventBridge Scheduler pricing](https://aws.amazon.com/eventbridge/pricing/)
- [AWS Nova 2 Lite token-rate example](https://aws.amazon.com/blogs/machine-learning/pair-nova-2-lite-with-claude-for-cost-optimized-document-processing/)
- [Secrets Manager pricing](https://aws.amazon.com/secrets-manager/pricing/)
- [Parameter Store pricing](https://aws.amazon.com/systems-manager/pricing/)
