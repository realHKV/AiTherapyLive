# AI Therapy backend

Java 21 / Spring Boot 3.4.4 application providing authentication, personalized AI conversations, profiles, memories, and subscriptions.

Start with these documents when changing the backend:

| Document | Purpose |
| --- | --- |
| [Development plan](docs/DEVELOPMENT_PLAN.md) | Ordered AI-agent tasks, exit tests, progress tracker, and frontend integration as the final phase |
| [Backend reference](docs/BACKEND_REFERENCE.md) | Implemented architecture, API, data, configuration, and known gaps |
| [Schema review](docs/SCHEMA_REVIEW.md) | Current UI/API/database mismatches, recommended data model, and migration priorities |
| [Future architecture](docs/FUTURE_ARCHITECTURE.md) | Docker Hub/ECS Fargate direction, Bedrock-only model candidates for both tiers, quotas, auth, dependencies, and later voice |
| [AWS migration plan](docs/AWS_MIGRATION.md) | ECS Fargate pipeline/infrastructure, prerequisites, cutover, and rollback |
| [AI change guide](docs/AI_CHANGE_GUIDE.md) | Reading order, change checklist, and reusable task prompts |

These documents were reviewed against the working tree on **2026-10-19**. They describe source code, not a verified live deployment. The older [architecture plan](ai_therapy_backend_plan.md) is historical design intent; do not assume its features exist.

The reference is the current-state snapshot. Future architecture and migration documents are proposals: the Docker Hub/ECS Fargate, Bedrock, Cognito, voice, and framework changes are not implemented. The target is ECS on Fargate with AWS-managed servers; Cognito and the ECR mirror remain recommendations, not settled requirements.

For implementation, start with Step 00 of the development plan and use its copyable agent prompt. The development plan owns execution order; architecture documents explain the design. Update the relevant documentation with each behavior or infrastructure change.

## Local backend baseline

Requires Java 21, Docker with a running daemon, Docker Compose, and OpenSSL. The Maven wrapper downloads Maven and dependencies on its first use.

1. Start isolated local services: `cd Backend && docker compose up -d --wait`.
2. Copy `config/local.env.example` to `.env`. Its database and Redis settings match `compose.yaml`. Fill `ENCRYPTION_MASTER_KEY` with `openssl rand -base64 32` and generate a **local-only** RSA key pair:

   ```bash
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /tmp/therapy-local-private.pem
   openssl pkey -in /tmp/therapy-local-private.pem -outform DER -out /tmp/therapy-local-private.der
   openssl pkey -in /tmp/therapy-local-private.pem -pubout -outform DER -out /tmp/therapy-local-public.der
   base64 -w 0 /tmp/therapy-local-private.der  # JWT_PRIVATE_KEY
   base64 -w 0 /tmp/therapy-local-public.der   # JWT_PUBLIC_KEY
   ```

   The JWT provider expects base64-encoded DER, not PEM text. Do not use these local keys with existing data. The `.env` file is ignored by Git.
3. Run `bash ./mvnw test` for unit and container tests. When Docker is unavailable, Testcontainers skips its PostgreSQL/Redis checks; this is **not** a successful integration run. Check the skipped-test count. Run `bash ./run-with-env.sh` to start the app using `.env`; Spring does not load that file by itself.
4. Stop services with `docker compose down`. Add `--volumes` only when you intend to delete the local database.

The baseline tests use synthetic data and a disposable test encryption key. They do not invoke an AI provider, Google, or Razorpay. Flyway applies V1–V4 to the local database at application startup. Never point `.env` at production data for this procedure.
