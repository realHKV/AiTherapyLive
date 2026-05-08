# AI Therapy Platform — Backend Architecture Plan
## For Claude Code Implementation

---

## PROJECT OVERVIEW

Build a production-ready backend for an AI therapy platform where users can share feelings and receive empathetic, context-aware AI responses. The AI remembers past conversations, personality traits, and life events to personalize every session.

**Tech Stack:**
- Backend: Java 21 + Spring Boot 3.x
- Database: PostgreSQL 15+
- Cache: Redis 7+
- Auth: OAuth2 (Google) + JWT (RS256)
- AI: Groq API (Llama 3 70B) via OpenAI-compatible REST — pluggable via interface
- Build: Maven or Gradle
- Migrations: Flyway
- Encryption: AES-256-GCM at application layer

---

## ARCHITECTURE — LAYERED + HEXAGONAL HYBRID

```
┌─────────────────────────────────────────────────────┐
│                  REST Controllers                    │
│         (thin — no business logic, DTOs only)        │
├─────────────────────────────────────────────────────┤
│              Spring Security Filter Chain            │
│    CORS → RateLimit → JwtAuth → Authorization        │
├─────────────────────────────────────────────────────┤
│                   Service Layer                      │
│  AuthService, ChatService, MemoryService,            │
│  ProfileService, SummarizationService                │
├──────────────────────┬──────────────────────────────┤
│    AI Gateway Layer  │    Repository Layer           │
│  AIGateway interface │  Spring Data JPA repos        │
│  OpenAICompatible    │  EncryptionService            │
│  PromptBuilder       │  (AES-256 AttributeConverter) │
└──────────────────────┴──────────────────────────────┘
                        │
         ┌──────────────┼──────────────┐
         ▼              ▼              ▼
    PostgreSQL        Redis         Groq API
  (primary store)  (cache/limits)  (LLM calls)
```

### Package Structure

```
com.therapyai/
├── config/
│   ├── SecurityConfig.java
│   ├── RedisConfig.java
│   ├── AIConfig.java
│   └── CorsConfig.java
├── controller/
│   ├── AuthController.java
│   ├── ChatController.java
│   └── ProfileController.java
├── service/
│   ├── auth/
│   │   ├── AuthService.java
│   │   └── TokenService.java
│   ├── chat/
│   │   ├── ChatService.java
│   │   └── ContextAssemblerService.java
│   ├── memory/
│   │   ├── MemoryService.java
│   │   └── MemoryExtractionService.java
│   └── summarization/
│       └── SummarizationService.java
├── ai/
│   ├── AIGateway.java                  (interface — port)
│   ├── OpenAICompatibleGateway.java    (adapter — Groq/OpenRouter)
│   └── PromptBuilder.java
├── repository/
│   ├── UserRepository.java
│   ├── ConversationRepository.java
│   ├── MessageRepository.java
│   ├── MemoryRepository.java
│   ├── ProfileRepository.java
│   ├── TraitRepository.java
│   └── RefreshTokenRepository.java
├── domain/
│   ├── User.java
│   ├── TherapyProfile.java
│   ├── PersonalityTrait.java
│   ├── Conversation.java
│   ├── Message.java
│   ├── LongTermMemory.java
│   └── RefreshToken.java
├── dto/
│   ├── request/
│   │   ├── RegisterRequest.java
│   │   ├── LoginRequest.java
│   │   ├── SendMessageRequest.java
│   │   └── UpdateProfileRequest.java
│   └── response/
│       ├── AuthResponse.java
│       ├── ChatResponse.java
│       ├── SessionStartResponse.java
│       └── ProfileResponse.java
├── security/
│   ├── JwtAuthenticationFilter.java
│   ├── JwtTokenProvider.java
│   └── OAuth2SuccessHandler.java
├── encryption/
│   └── EncryptedStringConverter.java   (JPA AttributeConverter)
└── scheduler/
    └── SessionSummarizationScheduler.java
```

---

## DATABASE SCHEMA

### Design principles
- 3NF normalisation — no god tables
- AES-256-GCM encryption on all sensitive text columns (marked ENCRYPTED)
- UUID primary keys throughout
- Flyway migrations for all schema changes
- Indexes on all FK columns and frequent query columns

---

### Table: users

```sql
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    display_name    VARCHAR(100),
    oauth_provider  VARCHAR(50),          -- 'google' | null
    oauth_subject   VARCHAR(255),         -- provider's sub claim
    password_hash   VARCHAR(255),         -- null if OAuth-only user
    is_verified     BOOLEAN NOT NULL DEFAULT false,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_oauth ON users(oauth_provider, oauth_subject);
```

---

### Table: therapy_profiles (1:1 with users)

```sql
CREATE TABLE therapy_profiles (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    preferred_name       TEXT,                    -- ENCRYPTED
    age_range            VARCHAR(20),             -- '25-34', optional
    communication_style  VARCHAR(50) DEFAULT 'gentle',  -- 'direct'|'gentle'|'reflective'
    ai_persona           VARCHAR(50) DEFAULT 'calm',    -- 'calm'|'encouraging'|'analytical'
    topics_of_concern    TEXT,                    -- ENCRYPTED JSON array e.g. '["anxiety","work"]'
    total_sessions       INT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

### Table: personality_traits

```sql
CREATE TABLE personality_traits (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trait_key   VARCHAR(100) NOT NULL,   -- e.g. 'perfectionist', 'introvert'
    trait_value TEXT,                    -- ENCRYPTED — AI-inferred description
    confidence  FLOAT NOT NULL DEFAULT 0.5,  -- 0.0 to 1.0
    source      VARCHAR(50) NOT NULL,    -- 'user_stated' | 'ai_inferred'
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, trait_key)
);

CREATE INDEX idx_traits_user ON personality_traits(user_id, confidence DESC);
```

---

### Table: conversations (sessions)

```sql
CREATE TABLE conversations (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status           VARCHAR(20) NOT NULL DEFAULT 'active',  -- 'active'|'completed'|'abandoned'
    session_summary  TEXT,                   -- ENCRYPTED — AI-generated after session ends
    mood_start       VARCHAR(30),            -- 'anxious'|'sad'|'neutral'|'hopeful'
    mood_end         VARCHAR(30),
    token_count      INT NOT NULL DEFAULT 0, -- total tokens used this session (cost tracking)
    started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at         TIMESTAMPTZ,
    summarized_at    TIMESTAMPTZ             -- set when async summarization job completes
);

CREATE INDEX idx_conv_user ON conversations(user_id, started_at DESC);
CREATE INDEX idx_conv_status ON conversations(status) WHERE status = 'active';
```

---

### Table: messages

```sql
CREATE TABLE messages (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role             VARCHAR(20) NOT NULL,  -- 'user' | 'assistant'
    content          TEXT NOT NULL,         -- ENCRYPTED
    token_count      INT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_conv ON messages(conversation_id, created_at ASC);
```

---

### Table: long_term_memories

```sql
CREATE TABLE long_term_memories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_conv_id  UUID REFERENCES conversations(id) ON DELETE SET NULL,
    memory_type     VARCHAR(50) NOT NULL,    -- 'life_event'|'goal'|'relationship'|'preference'
    title           VARCHAR(500) NOT NULL,   -- ENCRYPTED — short label e.g. "Sister's wedding"
    detail          TEXT,                    -- ENCRYPTED — full context
    importance      INT NOT NULL DEFAULT 5,  -- 1-10, used to rank context injection
    occurred_at     DATE,                    -- when the event is/was
    follow_up_at    DATE,                    -- when AI should next ask about this
    is_resolved     BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_memory_user ON long_term_memories(user_id, importance DESC);
CREATE INDEX idx_memory_followup ON long_term_memories(follow_up_at) WHERE is_resolved = false;
```

---

### Table: refresh_tokens

```sql
CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(255) UNIQUE NOT NULL,  -- SHA-256 hash of raw token
    device_info  VARCHAR(255),
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked      BOOLEAN NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_user ON refresh_tokens(user_id);
```

---

## API DOCUMENTATION

Base URL: `/api/v1`
All responses use format:
```json
{
  "success": true,
  "data": { },
  "error": null,
  "timestamp": "2025-09-10T14:23:00Z"
}
```

Error format:
```json
{
  "success": false,
  "data": null,
  "error": { "code": "UNAUTHORIZED", "message": "Token expired" }
}
```

---

### AUTH ENDPOINTS (public)

#### POST /auth/register
Register with email + password.

Request:
```json
{
  "email": "user@example.com",
  "password": "SecurePass123!",
  "displayName": "Riya"
}
```

Response 201:
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "expiresIn": 900,
  "user": { "id": "uuid", "email": "user@example.com" }
}
```

---

#### POST /auth/login
Credential login.

Request:
```json
{ "email": "user@example.com", "password": "SecurePass123!" }
```

Response 200: same as register.

---

#### POST /auth/refresh
Exchange refresh token for new access token. Refresh token is rotated.

Request:
```json
{ "refreshToken": "eyJ..." }
```

Response 200:
```json
{ "accessToken": "eyJ...", "refreshToken": "eyJ...", "expiresIn": 900 }
```

---

#### POST /auth/logout
Revoke session. Adds access token jti to Redis deny-list.

Headers: `Authorization: Bearer <accessToken>`
Request: `{ "refreshToken": "eyJ..." }`
Response 200: `{ "message": "Logged out successfully" }`

---

#### GET /auth/oauth2/google
Redirects to Google OAuth. On callback, creates/fetches user and redirects to frontend with tokens as query params.

---

### PROFILE ENDPOINTS (🔒 auth required)

#### GET /profile/me
Returns therapy profile, traits, and summary stats.

Response 200:
```json
{
  "userId": "uuid",
  "preferredName": "Riya",
  "communicationStyle": "gentle",
  "aiPersona": "calm",
  "topicsOfConcern": ["anxiety", "work stress"],
  "totalSessions": 12,
  "traits": [
    { "key": "perfectionist", "confidence": 0.85, "source": "ai_inferred" }
  ]
}
```

---

#### PUT /profile/me
Update preferences.

Request:
```json
{
  "preferredName": "Riya",
  "communicationStyle": "direct",
  "aiPersona": "encouraging",
  "topicsOfConcern": ["relationships", "anxiety"]
}
```

---

#### GET /profile/me/memories
List long-term memories. Supports pagination and filtering.

Query params: `?type=life_event&limit=20&offset=0&resolved=false`

Response 200:
```json
{
  "memories": [
    {
      "id": "uuid",
      "type": "life_event",
      "title": "Sister's wedding next month",
      "importance": 9,
      "occurredAt": "2025-10-15",
      "followUpAt": "2025-10-16",
      "isResolved": false
    }
  ],
  "total": 34,
  "offset": 0,
  "limit": 20
}
```

---

#### POST /profile/me/memories
Manually add a memory.

Request:
```json
{
  "type": "goal",
  "title": "Change careers by March",
  "detail": "Been thinking about moving from finance to UX design",
  "importance": 8,
  "occurredAt": "2025-09-01",
  "followUpAt": "2025-11-01"
}
```

---

#### DELETE /profile/me/memories/{id}
Delete a specific memory. Returns 204 No Content.

---

### CHAT ENDPOINTS (🔒 auth required)

#### POST /chat/sessions
Start a new therapy session. AI generates a personalised greeting using past memory.

Response 201:
```json
{
  "sessionId": "uuid",
  "aiGreeting": "Hi Riya, it's good to hear from you. Last time you mentioned your sister's wedding was coming up — how did it go?",
  "sessionNumber": 13
}
```

---

#### POST /chat/sessions/{sessionId}/messages
Send a message. Supports SSE streaming.

Request:
```json
{ "content": "I've been feeling really anxious about work lately." }
```

Standard Response 200:
```json
{
  "messageId": "uuid",
  "aiResponse": "I hear you — work anxiety can feel all-consuming. What's been the main source of that feeling lately?",
  "tokenCount": 312
}
```

SSE Streaming (set `Accept: text/event-stream`):
```
data: {"delta": "I hear"}
data: {"delta": " you —"}
data: {"delta": " work anxiety..."}
data: [DONE]
```

---

#### POST /chat/sessions/{sessionId}/end
End the session. Triggers async summarization job.

Response 200:
```json
{
  "sessionId": "uuid",
  "duration": 1842,
  "messageCount": 18,
  "moodStart": "anxious",
  "moodEnd": "calm",
  "summaryPreview": "Session focused on work anxiety and upcoming performance review..."
}
```

---

#### GET /chat/sessions
List past sessions (paginated).

Query params: `?limit=10&offset=0`

Response 200:
```json
{
  "sessions": [
    {
      "id": "uuid",
      "startedAt": "2025-09-08T18:00:00Z",
      "endedAt": "2025-09-08T18:32:00Z",
      "moodStart": "anxious",
      "moodEnd": "calm",
      "summaryPreview": "Discussed work pressure and sister's wedding..."
    }
  ],
  "total": 12
}
```

---

#### GET /chat/sessions/{sessionId}/messages
Full message history for a session. Cursor-based pagination.

Query params: `?limit=50&cursor=<messageId>`

---

## SECURITY DESIGN

### JWT Configuration
- Algorithm: RS256 (asymmetric — generate RSA key pair)
- Access token expiry: 15 minutes
- Refresh token expiry: 30 days, rotated on each use
- Each token carries a `jti` (JWT ID) claim for deny-list support
- On logout: jti added to Redis with TTL = remaining access token lifetime

### Spring Security Filter Chain (in order)

```
1. CorsFilter            — allow frontend origin, methods, headers
2. RateLimitFilter       — Redis token bucket, 429 with Retry-After header
3. JwtAuthFilter         — validate JWT, check deny-list, set SecurityContext
4. AuthorizationFilter   — method-level @PreAuthorize checks
5. Controller            — business logic
```

### JwtAuthenticationFilter skeleton

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractBearerToken(req);
        if (token != null && jwtProvider.validateToken(token)) {
            String jti = jwtProvider.getJti(token);
            if (!redisService.isDenylisted(jti)) {
                UsernamePasswordAuthenticationToken auth =
                    jwtProvider.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(req, res);
    }
}
```

### Encryption at Rest

All sensitive columns use a JPA AttributeConverter with AES-256-GCM:

```java
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Autowired
    private EncryptionService encryptionService;

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) return null;
        return encryptionService.encrypt(plainText); // AES-256-GCM + base64 encode
    }

    @Override
    public String convertToEntityAttribute(String cipherText) {
        if (cipherText == null) return null;
        return encryptionService.decrypt(cipherText);
    }
}

// Usage on entity field:
@Convert(converter = EncryptedStringConverter.class)
@Column(name = "content")
private String content;
```

Master encryption key loaded from environment variable — NEVER hardcoded:
```
ENCRYPTION_MASTER_KEY=<32-byte base64 value from secrets manager>
```

### Rate Limiting

```java
// Redis token bucket — per user for AI endpoints
public boolean isAllowed(String userId, String bucket, int maxPerHour) {
    String key = "ratelimit:" + bucket + ":" + userId;
    Long count = redisTemplate.opsForValue().increment(key);
    if (count == 1) redisTemplate.expire(key, 1, TimeUnit.HOURS);
    return count <= maxPerHour;
}
```

Limits:
- `/auth/*` (public): 10 requests/minute per IP
- `/chat/*/messages`: 20 AI messages/hour per user (configurable)
- All other protected endpoints: 120 requests/minute per user

### OAuth2 Configuration (application.yml)

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid, email, profile
            redirect-uri: "{baseUrl}/api/v1/auth/oauth2/callback/google"
```

---

## AI MEMORY SYSTEM

### Two-Tier Memory Architecture

**Short-term memory (Redis)**
- Key: `ctx:{userId}:{sessionId}`
- Value: JSON array of last 20 messages `[{role, content}]`
- TTL: 2 hours (extended on each message)
- Used as the `messages` array in every AI API call

**Long-term memory (PostgreSQL)**
- Stored in `long_term_memories` table
- Types: life_event, goal, relationship, preference
- Ranked by importance score (1-10) for context injection
- `follow_up_at` date drives AI proactive follow-ups

### Context Assembly — what gets sent to AI on every message

```
SYSTEM PROMPT
├── Base therapist persona instructions
├── User's preferred name: "Riya"
├── Communication style: "gentle"
├── Top 5 personality traits (ordered by confidence)
│     "perfectionist (confidence: 0.85)"
│     "introvert (confidence: 0.72)"
├── Top 8 long-term memories (ordered by importance DESC)
│     "Sister's wedding next month (importance: 9/10)"
│     "Work anxiety around performance reviews (importance: 8/10)"
│     "Goal: career change to UX design by March (importance: 7/10)"
├── Follow-up items due today or overdue
│     "Ask how the job interview went (due: 2025-09-09)"
└── Last session summary (max 300 words)
      "Previous session focused on work pressure..."

USER HISTORY: [last 40 messages from Redis]

USER: [current message]
```

### PromptBuilder.java skeleton

```java
@Service
public class PromptBuilder {

    public String buildSystemPrompt(User user, TherapyProfile profile,
                                    List<PersonalityTrait> traits,
                                    List<LongTermMemory> memories,
                                    Conversation lastSession) {

        StringBuilder sb = new StringBuilder();
        sb.append(BASE_THERAPIST_PERSONA);
        sb.append("\n\nUser's preferred name: ").append(profile.getPreferredName());
        sb.append("\nCommunication style: ").append(profile.getCommunicationStyle());

        sb.append("\n\nPersonality traits:\n");
        traits.stream()
              .sorted(Comparator.comparingDouble(PersonalityTrait::getConfidence).reversed())
              .limit(5)
              .forEach(t -> sb.append("- ").append(t.getTraitKey())
                              .append(" (confidence: ").append(t.getConfidence()).append(")\n"));

        sb.append("\nImportant memories:\n");
        memories.stream()
                .sorted(Comparator.comparingInt(LongTermMemory::getImportance).reversed())
                .limit(8)
                .forEach(m -> sb.append("- ").append(m.getTitle())
                                .append(" (importance: ").append(m.getImportance()).append("/10)\n"));

        // Add follow-up items due today
        List<LongTermMemory> dueFollowUps = memories.stream()
            .filter(m -> m.getFollowUpAt() != null
                      && !m.getFollowUpAt().isAfter(LocalDate.now())
                      && !m.isResolved())
            .collect(Collectors.toList());

        if (!dueFollowUps.isEmpty()) {
            sb.append("\nFollow-up items for this session:\n");
            dueFollowUps.forEach(m -> sb.append("- Ask about: ").append(m.getTitle()).append("\n"));
        }

        if (lastSession != null && lastSession.getSessionSummary() != null) {
            sb.append("\nLast session summary:\n").append(lastSession.getSessionSummary());
        }

        return sb.toString();
    }
}
```

### Base Therapist Persona (system prompt constant)

```
You are a compassionate AI companion providing emotional support. You are NOT a
licensed therapist and never diagnose mental health conditions.

Guidelines:
- Always greet the user by their preferred name
- Reference their history naturally: "Last time you mentioned..."
- Ask only ONE follow-up question per response, not several
- Keep responses warm and concise (150-250 words)
- Use the user's specified communication style preference
- NEVER give medical advice or medication recommendations
- If the user expresses suicidal ideation or self-harm intent, IMMEDIATELY
  provide crisis resources. In India: iCall: 9152987821, Vandrevala Foundation: 1860-2662-345
- If the user seems to be in a medical emergency, instruct them to call emergency services

You are a supportive companion, not a replacement for professional help.
Gently encourage professional support when topics become clinical.
```

### Post-Session Summarization Job

```java
@Async
@Service
public class SummarizationService {

    public void summarizeSession(UUID sessionId) {
        Conversation session = conversationRepo.findById(sessionId).orElseThrow();
        List<Message> messages = messageRepo.findByConversationId(sessionId);

        // 1. Build summarization prompt
        String prompt = """
            Analyse this therapy support session and respond in JSON with this exact structure:
            {
              "summary": "2-3 paragraph narrative summary",
              "moodTrajectory": "improved|stable|declined",
              "keyTopics": ["topic1", "topic2"],
              "newMemories": [
                {
                  "type": "life_event|goal|relationship|preference",
                  "title": "short title",
                  "detail": "full context",
                  "importance": 7,
                  "occurredAt": "2025-09-10",
                  "followUpAt": "2025-10-10"
                }
              ],
              "newTraits": [
                { "key": "trait name", "value": "description", "confidence": 0.7 }
              ]
            }
            
            Session messages:
            """ + formatMessages(messages);

        // 2. Call AI
        AIResponse response = aiGateway.chat(new AIRequest(SUMMARIZER_SYSTEM, prompt));
        SummarizationResult result = parseJson(response.getContent());

        // 3. Persist results atomically
        transactionTemplate.execute(status -> {
            session.setSessionSummary(result.getSummary());
            session.setMoodEnd(result.getMoodTrajectory());
            session.setSummarizedAt(Instant.now());
            conversationRepo.save(session);

            result.getNewMemories().forEach(m -> memoryRepo.save(toEntity(m, session)));
            result.getNewTraits().forEach(t -> upsertTrait(session.getUserId(), t));

            return null;
        });

        // 4. Update therapy profile session count
        profileRepo.incrementSessionCount(session.getUserId());
    }
}
```

### Session Start — AI Greeting Generation

```java
public SessionStartResponse startSession(UUID userId) {
    // 1. Fetch context
    TherapyProfile profile = profileRepo.findByUserId(userId);
    List<PersonalityTrait> traits = traitRepo.findTopByUserId(userId, 5);
    List<LongTermMemory> memories = memoryRepo.findTopByImportance(userId, 8);
    Conversation lastSession = convRepo.findLastCompleted(userId);

    // 2. Build greeting prompt
    String systemPrompt = promptBuilder.buildSystemPrompt(profile, traits, memories, lastSession);
    String greetingPrompt = profile.getTotalSessions() == 0
        ? "Greet this user warmly for their first session. Ask what brings them here today."
        : "Greet this returning user. Reference something specific from their history. Ask how they've been.";

    // 3. Call AI for greeting
    AIResponse greeting = aiGateway.chat(new AIRequest(systemPrompt, greetingPrompt));

    // 4. Create conversation record
    Conversation session = new Conversation(userId);
    convRepo.save(session);

    // 5. Seed Redis context with greeting
    redisService.appendMessage(userId, session.getId(),
        new ChatMessage("assistant", greeting.getContent()));

    return new SessionStartResponse(session.getId(), greeting.getContent(),
                                    profile.getTotalSessions() + 1);
}
```

---

## AI PROVIDER INTEGRATION

### Recommended Provider: Groq (cheapest, fastest)

- Base URL: `https://api.groq.com/openai/v1`
- Model: `llama3-70b-8192` (recommended) or `mixtral-8x7b-32768`
- Cost: ~$0.10/million input tokens (free tier available)
- Speed: ~500 tokens/second — excellent for real-time chat
- API format: OpenAI-compatible (drop-in)

### Backup: OpenRouter

- Base URL: `https://openrouter.ai/api/v1`
- Use model: `mistralai/mistral-7b-instruct` (~$0.07/M tokens)
- Advantage: 100+ models behind one API key — upgrade without code changes

### AIGateway Interface

```java
public interface AIGateway {
    AIResponse chat(AIRequest request);
    AIResponse chatStream(AIRequest request, StreamCallback callback); // SSE
}

public record AIRequest(
    String systemPrompt,
    String userMessage,
    List<ChatMessage> history,  // from Redis short-term memory
    int maxTokens,
    float temperature
) {}

public record AIResponse(
    String content,
    int promptTokens,
    int completionTokens
) {}
```

### OpenAICompatibleGateway Implementation

```java
@Component
public class OpenAICompatibleGateway implements AIGateway {

    private final RestClient restClient;
    private final String model;

    @Override
    public AIResponse chat(AIRequest request) {
        var body = Map.of(
            "model", model,
            "max_tokens", request.maxTokens(),
            "temperature", request.temperature(),
            "messages", buildMessages(request)
        );

        var response = restClient.post()
            .uri("/chat/completions")
            .body(body)
            .retrieve()
            .body(OpenAIResponse.class);

        return new AIResponse(
            response.choices().get(0).message().content(),
            response.usage().promptTokens(),
            response.usage().completionTokens()
        );
    }

    private List<Map<String, String>> buildMessages(AIRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        request.history().forEach(h ->
            messages.add(Map.of("role", h.role(), "content", h.content())));
        messages.add(Map.of("role", "user", "content", request.userMessage()));
        return messages;
    }
}
```

### application.yml — full AI config

```yaml
ai:
  provider:
    base-url: ${AI_BASE_URL:https://api.groq.com/openai/v1}
    api-key: ${AI_API_KEY}
    model: ${AI_MODEL:llama3-70b-8192}
    max-tokens: 512
    temperature: 0.75
  limits:
    messages-per-hour: 20
    context-messages: 20
    memory-items-in-prompt: 8
    system-prompt-max-tokens: 600

spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USER}
    password: ${DATABASE_PASSWORD}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid, email, profile

jwt:
  private-key: ${JWT_PRIVATE_KEY}   # RSA private key (PEM)
  public-key: ${JWT_PUBLIC_KEY}     # RSA public key (PEM)
  access-token-expiry: 900          # 15 minutes in seconds
  refresh-token-expiry: 2592000     # 30 days in seconds

encryption:
  master-key: ${ENCRYPTION_MASTER_KEY}  # 32-byte base64 key

logging:
  level:
    com.therapyai: INFO
  pattern:
    console: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"
```

---

## TOKEN COST CONTROL STRATEGY

1. System prompt hard cap: 600 tokens maximum
   - Achieved by limiting traits to top 5, memories to top 8 (by importance)
   - Trim last session summary to 300 words if needed

2. Short-term context cap: last 20 messages (~1500 tokens average)

3. AI response cap: 512 max_tokens per response

4. After session end, async job summarizes and prunes:
   - Raw messages older than 90 days are deleted
   - Summaries and extracted memories are kept indefinitely

5. Track token_count per message and session — build a cost dashboard

Estimated cost per session (Groq pricing):
- System prompt: ~600 tokens × $0.10/M = $0.00006
- Per message exchange: ~2000 tokens × $0.10/M = $0.0002
- 20-message session: ~$0.004 per session

---

## ENVIRONMENT VARIABLES (required)

```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/therapyai
DATABASE_USER=therapyai_user
DATABASE_PASSWORD=<strong-password>

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=<redis-password>

# JWT (RSA key pair — generate with: openssl genrsa -out private.pem 2048)
JWT_PRIVATE_KEY=<base64-encoded RSA private key>
JWT_PUBLIC_KEY=<base64-encoded RSA public key>

# Encryption (generate with: openssl rand -base64 32)
ENCRYPTION_MASTER_KEY=<32-byte base64 key>

# AI Provider
AI_BASE_URL=https://api.groq.com/openai/v1
AI_API_KEY=<groq-api-key>
AI_MODEL=llama3-70b-8192

# OAuth2
GOOGLE_CLIENT_ID=<google-oauth-client-id>
GOOGLE_CLIENT_SECRET=<google-oauth-client-secret>
```

---

## IMPLEMENTATION ORDER (recommended for Claude Code)

1. Project setup — Spring Boot 3, dependencies, Flyway, PostgreSQL connection
2. Database migrations — create all 7 tables via Flyway V1__init.sql
3. Domain entities — JPA entities with EncryptedStringConverter
4. Repository layer — Spring Data JPA repositories
5. Security config — JWT provider, filter, SecurityConfig, OAuth2
6. Auth flow — register, login, refresh, logout endpoints
7. Profile endpoints — CRUD for therapy profile and memories
8. AI gateway — AIGateway interface + OpenAICompatibleGateway
9. PromptBuilder — context assembly logic
10. Chat service — session start (greeting), message send, session end
11. Redis integration — short-term context cache, rate limiting
12. Summarization scheduler — async post-session job
13. SSE streaming — for real-time AI response streaming
14. Integration tests — auth flow, chat flow, memory extraction

---

## MAVEN DEPENDENCIES (pom.xml)

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-client</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.5</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.5</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.12.5</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
</dependencies>
```

---

## FUTURE UPGRADES (when to add them)

| When | Add |
|---|---|
| Users have 200+ sessions, context too large | pgvector extension — embed session summaries, semantic search |
| Need to switch AI models frequently | Spring AI — unified abstraction over LLM providers |
| 10k+ users, read latency issues | Read replica for PostgreSQL |
| Message volume very high | Partition messages table by conversation_id |
| Need knowledge base (articles, coping tips) | RAG pipeline over that corpus + vector DB |
| Multi-region deployment | Redis cluster, DB connection pooling with PgBouncer |

---

*End of backend architecture plan. Feed this entire document to Claude Code as context before starting implementation.*
