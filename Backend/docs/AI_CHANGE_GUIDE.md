# Guide for AI-assisted backend changes

Use this as task context for a coding assistant. It is a working guide, not evidence that the migration or proposed fixes have been implemented. Keep tasks small enough to review and roll back independently.

Use [DEVELOPMENT_PLAN](DEVELOPMENT_PLAN.md) as the canonical execution order and status tracker. Complete backend contracts and staging handoff before frontend implementation; frontend work starts at Step 18. Cognito and voice are later releases.

## 1. Reading order

1. Read [README](../README.md) and [backend reference](BACKEND_REFERENCE.md).
   For schema, profile, onboarding, payment, or usage work, also read [schema review](SCHEMA_REVIEW.md) and [memory design](MEMORY_DESIGN.md), which defines the proposed memory contract.
2. Read [future architecture](FUTURE_ARCHITECTURE.md) for requested direction, researched providers, quotas, auth, upgrades, and milestone acceptance criteria; read [AWS migration plan](AWS_MIGRATION.md) for ECS/pipeline/cutover tasks.
3. Inspect the actual source, migrations, tests, and frontend callers for the requested area. Source takes precedence over stale descriptions; the old architecture plan is historical intent.
4. Inspect `git status` and any applicable repository instructions before editing. Preserve unrelated local changes.

Do not paste the entire repository into every prompt. Supply the task and these entry points, then have the assistant retrieve relevant files. This keeps assumptions visible and reduces accidental changes outside the task.

## 2. Change map

Paths in this table are relative to `Backend/`, except explicitly prefixed frontend paths.

| Change | Inspect together |
| --- | --- |
| Auth/session lifecycle | `controller/AuthController.java`, `service/auth/`, `security/`, `config/SecurityConfig.java`, refresh-token schema, frontend auth context/API |
| Chat behavior or SSE | `controller/ChatController.java`, `service/chat/ChatService.java`, `service/ai/`, message/conversation entities, frontend `src/api/chat.js` and chat page |
| Memory/profile behavior | `service/profile/`, `service/memory/`, `service/job/SummarizationJob.java`, `service/ai/PromptBuilder.java`, relevant DTOs/entities/migrations |
| Provider/model change | `service/ai/AIGateway.java`, gateway implementation, AI DTOs, provider configuration, PRO routing, summary JSON parsing |
| Payments/entitlements | `controller/PaymentController.java`, `controller/DevController.java`, `service/payment/SubscriptionService.java`, subscription job/entities, V4, frontend subscription/payment code |
| Schema/encryption | `domain/`, `repository/`, `encryption/`, Flyway migrations, account deletion, restore procedure |
| Deployment/scaling | `Dockerfile`, `pom.xml`, `application.yaml`, security/OAuth, async jobs, SSE, `.github/workflows/keep-alive.yml` at repo root |
| Dependency upgrade | `pom.xml`, Maven wrapper, Docker base images, `config/JacksonConfig.java`, JJWT/Jackson compatibility, all contract/integration tests |
| Tier quotas/Bedrock | Gateway interface/implementation, subscription service, rate-limit filter, summary job, proposed durable usage records; FUTURE_ARCHITECTURE sections 3–4 |
| Cognito/voice (later) | Existing identity-to-UUID assumptions and frontend auth; existing stream/message lifecycle; FUTURE_ARCHITECTURE sections 5 and 7 |

Java paths in the table are under `src/main/java/com/hkv/AiTherapy/`. Tests are under the matching `src/test/java` package. Check consumers as well as the file being edited.

## 3. Default constraints for future work

- Preserve `/api/v1`, response fields, ownership checks, and frontend compatibility unless the task explicitly changes them. Test actual JSON rather than inferring names from Java boolean fields.
- Add Flyway migrations; do not edit applied migrations or enable Hibernate schema auto-update as a shortcut.
- Preserve encrypted data format and existing key compatibility. A key change needs a versioned migration/rotation design and restore test.
- Keep credentials, JWTs, database dumps, therapy transcripts, and user details out of prompts, patches, fixtures, and logs. Use synthetic test data.
- Keep cloud hosting changes separate from AI-provider, authentication-provider, and framework migrations unless the task explicitly requires combining them.
- Treat external callbacks and background jobs as duplicate-prone. Define idempotency, failure recovery, and transaction boundaries before parallelizing them.
- Distinguish verified code behavior, proposed behavior, and unknown live-environment settings in the final report.
- Update these docs when changing routes, schema, configuration, data flows, or deployment decisions. Remove resolved findings only after recording the fix and validation.
- Docker Hub → ECS on Fargate is the corrected requested target. Avoid EC2-backed capacity and Kubernetes setup; the user wants AWS-managed servers. ECR mirroring and Cognito are recommendations to evaluate, not implemented or approved selections.
- The target is Bedrock-only for both tiers. Recheck model availability, account quotas/data controls, Bedrock region/API support, and maintained Spring versions using official sources. Document the verification date and account-specific unknowns.
- All AI operations, including summaries/profile extraction, use Bedrock. FREE traffic is subsidized billable inference: enforce a FREE subsidy budget, PRO usage budgets, and an account-wide spend cap. Do not add external-provider fallback.
- Active memories stay in encrypted PostgreSQL tables. No S3 memory-file replacement, vector store, or external memory service is required. Implement the four layers, confirmation/provenance, relevance-based selection, and forget/reset semantics in MEMORY_DESIGN.
- Use PostgreSQL-backed extraction jobs first. Keep model output as pending candidates until confirmed; never silently overwrite user-confirmed profile facts. Reject stale jobs after context resets and never deduplicate memories solely by title.
- Product quotas are separate from provider quotas. Implement token/spend/concurrency controls and durable usage accounting; the draft beta message counts are not affordability guarantees.

## 4. Reusable implementation prompt

```text
Task: [one concrete outcome]

Read Backend/README.md and Backend/docs/AI_CHANGE_GUIDE.md, then the
relevant sections of BACKEND_REFERENCE.md. For deployment tasks also read
AWS_MIGRATION.md. Read the relevant milestone in FUTURE_ARCHITECTURE.md.
Follow the assigned numbered step in DEVELOPMENT_PLAN.md and its exit gate.
Inspect current source and git status before editing.

Scope: [allowed behavior changes and components]
Compatibility to preserve: [HTTP/schema/encryption/frontend requirements]
Acceptance criteria: [observable successes and failure cases]
Environment: [local/staging; known versions; no secret values]

Implement the change, with meaningful tests for its failure modes.
Do not treat proposals in the documentation as existing features.
Preserve unrelated edits and use synthetic data. If a decision requires
missing live information, state what is missing and continue independent work.

Report changed behavior, files, validation results, remaining limitations,
and rollback implications. Update affected documentation.
```

Example bounded first task:

```text
Prevent production users from changing subscriptions through /dev/*.
Inspect DevController, SecurityConfig, application.yaml, and frontend callers.
Make the routes unavailable when DEV_MODE=false. Preserve normal paid
subscription behavior. Add HTTP/security tests proving the routes are
unavailable in normal mode and require authentication when explicitly enabled.
Update the backend reference with the resulting behavior. Do not deploy.
```

Example AWS planning/implementation task after prerequisites are resolved:

```text
Prepare reviewable ECS Fargate staging infrastructure-as-code and a GitHub Actions
Docker Hub publishing pipeline using the recorded decisions in AWS_MIGRATION.md.
Inputs: [region, tool, budget constraints, network, DNS, recovery targets].
Also identify Docker Hub repository/visibility, direct pulls versus ECR mirror,
and Fargate sizing/networking. Include task definitions, digest promotion,
ALB health routing, private data services, secret injection, task/execution roles,
GitHub OIDC, a one-off migration task, logs, and deployment/rollback instructions.
Keep real secrets out of code. Validate the configuration locally and report
which checks require AWS access. Do not apply infrastructure or switch traffic.
```

## 5. Execution phases

| Steps | Outcome | Evidence |
| --- | --- | --- |
| 00–03 | Local baseline, security, auth, payments | Reproducible tests and ownership/replay checks |
| 04–06 | Framework upgrade, API contracts, data consistency | Dependency checkpoints, contract fixtures, upgrade/restore tests |
| 07–11 | Bedrock routing, budgets, chat, PostgreSQL jobs, memory lifecycle | Usage/concurrency/cancellation, memory relevance/confirmation/forgetting, and recovery tests |
| 12–13 | Bedrock-only operations and backend acceptance | Safe configuration, health/metrics, stable contract |
| 14–17 | CI, ECS staging, recovery rehearsal, frontend handoff | Tested artifact, actual staging evidence, complete handoff package |
| 18–20 | Frontend integration, browser acceptance, coordinated release | Full user journeys and release/rollback evidence |

Detailed scope, dependencies, and completion gates live in DEVELOPMENT_PLAN. Use its tracker rather than maintaining a second status list here. Split application fixes from infrastructure commits so each change has an understandable cause and effect.

## 6. Maintaining a useful project memory

For each substantial architecture decision, create a short record in `docs/decisions/` with: date/status, problem, constraints, decision, alternatives considered, consequences, and how it will be validated. Add the folder when the first decision is made; do not populate it with invented approvals.

Keep current behavior in BACKEND_REFERENCE, source-review findings in SCHEMA_REVIEW, memory behavior in MEMORY_DESIGN, product/platform proposals in FUTURE_ARCHITECTURE, execution status in DEVELOPMENT_PLAN, ECS/cutover progress in AWS_MIGRATION, and working conventions here. Avoid copying whole source files into docs. Link to source and document the relationships, invariants, and failure modes that are harder to infer from one file.

At the end of each task, record exactly which tests ran and what remains unverified. A passing mock unit test does not prove OAuth works through an ALB, ciphertext can be restored, or a stream survives a deployment.
