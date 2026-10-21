# Bedrock gateway and candidate evaluation

Status: **adapter and synthetic fixture implemented; live candidate comparison pending an AWS account/region selection and model access**. No AWS invocation has been made by this change.

## Adapter behavior

- `BedrockGateway` uses the AWS SDK for Java v2 `bedrockruntime` module and the Converse/ConverseStream APIs. It maps only text, finish status, and token usage into the provider-neutral contract.
- `DefaultCredentialsProvider` is used. Local development should use AWS IAM Identity Center / CLI SSO. ECS should use its task role. Do not create or share long-lived access keys for this application.
- SDK retries are disabled. A later application retry must be an explicit provider attempt so Step 10 can reserve, record, cancel, and settle it.
- API calls have bounded connect, attempt, and total timeouts. Cancellation cancels the SDK future. Provider errors map to sanitized categories; SDK messages and response bodies are not returned or logged.
- Bedrock is disabled by default (`AI_BEDROCK_ENABLED=false`). Model routing is also disabled by default. Enabling this adapter alone does not route current chat endpoints through Bedrock; Step 10 owns that integration.

## Synthetic evaluation fixture

`src/test/resources/bedrock-evaluation.json` has three invented examples for supportive conversation, grounded summary, and resistance to prompt instructions embedded in memory. Review each candidate's output blind against the stated criteria. Score each criterion 0 (miss), 1 (partial), or 2 (meets). Record response latency and reported input/output tokens. Do not use real user conversations in the evaluation fixture.

| Candidate role | Exact model or inference profile ID | Source region | Processing regions | Access verified | Quality score | p50 latency | Input/output tokens | Price basis/date | Decision |
|---|---|---|---|---|---|---|---|---|---|
| FREE conversation | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending |
| PRO conversation | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending |
| SUMMARY / PROFILE_REFRESH | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending |

Candidate IDs in `FUTURE_ARCHITECTURE.md` are hypotheses. Resolve exact IDs and whether a direct model ID or inference-profile ID is appropriate from the current Bedrock model page and `GetInferenceProfile` response in the selected AWS account and region. Cross-region inference can process data in destination Regions; check its destination list and privacy requirements before choosing it.

## What is needed for a real smoke/evaluation run

1. Your AWS account and preferred Bedrock source region. The account ID is not a secret; do not send passwords, access keys, or secret keys here.
2. Model IDs you want to evaluate (or authorize us to use the current planning candidates after checking their availability in that region). Confirm whether cross-region inference is acceptable for the data.
3. Bedrock model access enabled for the selected account, with any provider/model use-case form completed where AWS requires it.
4. A short-lived local AWS SSO session for a human evaluation identity, or an ECS task role when staging exists. The role needs only `bedrock:InvokeModel` and `bedrock:InvokeModelWithResponseStream` on the approved model/inference-profile resources. For cross-region profiles, IAM/SCP permissions must also allow the profile's destination resources.
5. A spending limit you are comfortable with for a small run. The application policy has no currency cap until current regional model pricing and measured token usage are reviewed.

Never place AWS credentials in `.env`, source code, GitHub, or a chat message. For ECS, attach the permissions to the task role; the AWS SDK then gets temporary credentials automatically.

## Exit record

The SDK fixture tests cover normal responses, text/usage mapping, stream deltas, partial stream failure, throttling, access denial, invalid model access, timeout, cancellation, and terminal-event uniqueness. The full backend suite passed 72 discovered tests (66 passed; 6 PostgreSQL/Redis integration tests skipped because Docker is unavailable here). Live model identifiers, account/region access, latency, quality results, pricing assumptions, and a capped smoke run remain blocked on the AWS inputs above. Until those are recorded, no candidate is approved for production and Bedrock remains off.
