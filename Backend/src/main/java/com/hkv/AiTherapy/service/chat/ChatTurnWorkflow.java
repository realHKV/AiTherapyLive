package com.hkv.AiTherapy.service.chat;

import com.hkv.AiTherapy.domain.AiRequest;
import com.hkv.AiTherapy.domain.ChatSessionStart;
import com.hkv.AiTherapy.domain.ChatTurn;
import com.hkv.AiTherapy.domain.Conversation;
import com.hkv.AiTherapy.domain.Message;
import com.hkv.AiTherapy.domain.User;
import com.hkv.AiTherapy.dto.response.ChatMessageResponse;
import com.hkv.AiTherapy.dto.response.ChatRequestStatusResponse;
import com.hkv.AiTherapy.dto.response.ConversationResponse;
import com.hkv.AiTherapy.repository.AiRequestRepository;
import com.hkv.AiTherapy.repository.ChatSessionStartRepository;
import com.hkv.AiTherapy.repository.ChatTurnRepository;
import com.hkv.AiTherapy.repository.ConversationRepository;
import com.hkv.AiTherapy.repository.MessageRepository;
import com.hkv.AiTherapy.repository.UserRepository;
import com.hkv.AiTherapy.service.ai.PromptBuilder;
import com.hkv.AiTherapy.service.ai.routing.CancellationToken;
import com.hkv.AiTherapy.service.ai.routing.GenerationCommand;
import com.hkv.AiTherapy.service.ai.routing.GenerationEvent;
import com.hkv.AiTherapy.service.ai.routing.GenerationException;
import com.hkv.AiTherapy.service.ai.routing.GenerationFailure;
import com.hkv.AiTherapy.service.ai.routing.GenerationMessage;
import com.hkv.AiTherapy.service.ai.routing.GenerationPurpose;
import com.hkv.AiTherapy.service.ai.routing.GenerationResult;
import com.hkv.AiTherapy.service.ai.routing.GenerationRouter;
import com.hkv.AiTherapy.service.ai.routing.GenerationUsage;
import com.hkv.AiTherapy.service.ai.routing.ResolvedGeneration;
import com.hkv.AiTherapy.service.usage.RedisAdmissionService;
import com.hkv.AiTherapy.service.usage.UsageAccountingService;
import com.hkv.AiTherapy.service.usage.UsageException;
import com.hkv.AiTherapy.service.usage.UsageReservation;
import com.hkv.AiTherapy.service.operations.AiOperationalMetrics;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Opt-in v2 chat lifecycle. Provider calls always run outside PostgreSQL transactions. */
@Service
@ConditionalOnProperty(name = {"ai.chat.v2.enabled", "ai.routing.enabled"}, havingValue = "true")
public class ChatTurnWorkflow {
    private static final int MAX_MESSAGE_CHARS = 8_000;
    private static final int CONTEXT_MESSAGES = 20;
    private final TransactionTemplate transactions;
    private final UserRepository users;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ChatTurnRepository turns;
    private final ChatSessionStartRepository sessionStarts;
    private final AiRequestRepository requests;
    private final PromptBuilder prompts;
    private final GenerationRouter router;
    private final UsageAccountingService accounting;
    private final RedisAdmissionService admission;
    private final TaskExecutor executor;
    private final ScheduledThreadPoolExecutor deadlines;
    private final Duration streamTimeout;
    private final Duration firstTokenTimeout;
    private final Duration totalTimeout;
    private final int maxInputTokens;
    private final int maxOutputTokens;
    private final ConcurrentHashMap<UUID, Set<CancellationToken>> activeStreams = new ConcurrentHashMap<>();
    private AiOperationalMetrics operationalMetrics;

    public ChatTurnWorkflow(PlatformTransactionManager transactionManager, UserRepository users,
                            ConversationRepository conversations, MessageRepository messages,
                            ChatTurnRepository turns, ChatSessionStartRepository sessionStarts,
                            AiRequestRepository requests, PromptBuilder prompts, GenerationRouter router,
                            UsageAccountingService accounting, RedisAdmissionService admission,
                            @Qualifier("chatGenerationExecutor") TaskExecutor executor,
                            @Qualifier("chatDeadlineScheduler") ScheduledThreadPoolExecutor deadlines,
                            @Value("${ai.chat.v2.stream-timeout:90s}") Duration streamTimeout,
                            @Value("${ai.chat.v2.first-token-timeout:15s}") Duration firstTokenTimeout,
                            @Value("${ai.chat.v2.total-timeout:60s}") Duration totalTimeout,
                            @Value("${ai.chat.v2.max-input-tokens:6000}") int maxInputTokens,
                            @Value("${ai.chat.v2.max-output-tokens:1500}") int maxOutputTokens) {
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setTimeout(10);
        this.users = users;
        this.conversations = conversations;
        this.messages = messages;
        this.turns = turns;
        this.sessionStarts = sessionStarts;
        this.requests = requests;
        this.prompts = prompts;
        this.router = router;
        this.accounting = accounting;
        this.admission = admission;
        this.executor = executor;
        this.deadlines = deadlines;
        this.streamTimeout = positive(streamTimeout, "stream timeout");
        this.firstTokenTimeout = positive(firstTokenTimeout, "first-token timeout");
        this.totalTimeout = positive(totalTimeout, "generation timeout");
        if (firstTokenTimeout.compareTo(totalTimeout) > 0 || totalTimeout.compareTo(streamTimeout) >= 0
                || maxInputTokens < 100 || maxInputTokens > 100_000 || maxOutputTokens < 1 || maxOutputTokens > 8192)
            throw new IllegalStateException("Chat timeout or token limits are inconsistent");
        this.maxInputTokens = maxInputTokens;
        this.maxOutputTokens = maxOutputTokens;
    }

    @Autowired(required = false)
    public void setOperationalMetrics(AiOperationalMetrics operationalMetrics) {
        this.operationalMetrics = operationalMetrics;
    }

    /** Reuses an active session on reload; a fresh greeting is accounted and persisted exactly once. */
    public ConversationResponse startSession(UUID userId, String idempotencyKey) {
        requireKey(idempotencyKey);
        SessionDecision decision = required(transactions.execute(status -> prepareSession(userId, idempotencyKey)));
        if (decision.prepared() == null) return decision.response();
        Prepared prepared = decision.prepared();
        try {
            GenerationResult result = callSync(prepared);
            Message assistant = persistSuccess(prepared.requestId(), result);
            if (assistant == null) throw conflict("Session ended before the greeting completed");
            return conversationResponse(prepared.conversationId(), assistant);
        } catch (CallFailure e) {
            persistFailure(prepared.requestId(), e.failure(), e.providerAttempted(), true);
            throw providerError(e.failure());
        } catch (RuntimeException e) {
            if (!(e instanceof ResponseStatusException))
                persistFailure(prepared.requestId(), unavailable(), true, true);
            throw e instanceof ResponseStatusException responseStatus ? responseStatus : unavailableError();
        } finally {
            releaseLease(userId, prepared.requestId());
        }
    }

    public ChatMessageResponse submit(UUID userId, UUID conversationId, String idempotencyKey, String content) {
        Prepared prepared = required(transactions.execute(status -> prepareTurn(userId, conversationId, idempotencyKey, content)));
        if (prepared.replay()) return replayMessage(userId, prepared);
        try {
            GenerationResult result = callSync(prepared);
            Message assistant = persistSuccess(prepared.requestId(), result);
            if (assistant == null) throw conflict("Conversation ended before the response completed");
            return map(assistant, prepared.conversationId());
        } catch (CallFailure e) {
            persistFailure(prepared.requestId(), e.failure(), e.providerAttempted(), false);
            throw providerError(e.failure());
        } catch (RuntimeException e) {
            if (!(e instanceof ResponseStatusException)) persistFailure(prepared.requestId(), unavailable(), true, false);
            throw e instanceof ResponseStatusException responseStatus ? responseStatus : unavailableError();
        } finally {
            releaseLease(userId, prepared.requestId());
        }
    }

    public ChatStreamHandle stream(UUID userId, UUID conversationId, String idempotencyKey, String content) {
        Prepared prepared = required(transactions.execute(status -> prepareTurn(userId, conversationId, idempotencyKey, content)));
        SseEmitter emitter = new SseEmitter(streamTimeout.toMillis());
        if (prepared.replay()) {
            replayStream(userId, emitter, prepared);
            return new ChatStreamHandle(prepared.requestId().toString(), emitter);
        }

        CancellationToken cancellation = new CancellationToken();
        register(conversationId, cancellation);
        if (operationalMetrics != null) operationalMetrics.streamStarted();
        AtomicBoolean terminal = new AtomicBoolean();
        AtomicBoolean connected = new AtomicBoolean(true);
        AtomicBoolean attempted = new AtomicBoolean();
        AtomicBoolean sawDelta = new AtomicBoolean();
        AtomicReference<ScheduledFuture<?>> firstTimer = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> totalTimer = new AtomicReference<>();
        Runnable cancelTimers = () -> {
            ScheduledFuture<?> first = firstTimer.getAndSet(null);
            if (first != null) first.cancel(false);
            ScheduledFuture<?> total = totalTimer.getAndSet(null);
            if (total != null) total.cancel(false);
        };
        java.util.function.Consumer<GenerationFailure> finishFailure = failure -> {
            if (!terminal.compareAndSet(false, true)) return;
            if (operationalMetrics != null) operationalMetrics.streamFinished(
                    failure.code() == GenerationFailure.Code.CANCELLED ? "cancelled" : "failed");
            cancelTimers.run();
            cancellation.cancel();
            boolean persisted = persistFailure(prepared.requestId(), failure, attempted.get(), false);
            releaseLease(userId, prepared.requestId());
            unregister(conversationId, cancellation);
            if (connected.get() && persisted) sendError(emitter, prepared, failure);
            else if (connected.get()) emitter.completeWithError(new IllegalStateException("Request status is being reconciled"));
        };
        emitter.onTimeout(() -> {
            connected.set(false);
            finishFailure.accept(timeoutFailure());
        });
        emitter.onError(error -> {
            connected.set(false);
            finishFailure.accept(cancelledFailure());
        });
        emitter.onCompletion(() -> {
            if (!terminal.get()) {
                connected.set(false);
                finishFailure.accept(cancelledFailure());
            }
        });

        try {
            sendStart(emitter, prepared);
            totalTimer.set(deadlines.schedule(() -> finishFailure.accept(timeoutFailure()),
                    totalTimeout.toMillis(), TimeUnit.MILLISECONDS));
            firstTimer.set(deadlines.schedule(() -> finishFailure.accept(timeoutFailure()),
                    firstTokenTimeout.toMillis(), TimeUnit.MILLISECONDS));
            executor.execute(() -> {
                try {
                    if (!isConversationActive(conversationId) || cancellation.isCancelled())
                        throw new GenerationException(cancelledFailure());
                    accounting.startAttempt(prepared.requestId());
                    attempted.set(true);
                    router.stream(prepared.generation(), cancellation, event -> {
                        if (terminal.get()) return;
                        if (event instanceof GenerationEvent.Delta delta) {
                            if (sawDelta.compareAndSet(false, true)) {
                                ScheduledFuture<?> timer = firstTimer.getAndSet(null);
                                if (timer != null) timer.cancel(false);
                            }
                            try { send(emitter, "delta", new DeltaEvent(prepared.requestId().toString(), delta.text())); }
                            catch (IOException e) { connected.set(false); finishFailure.accept(cancelledFailure()); }
                        } else if (event instanceof GenerationEvent.Completed completed) {
                            if (!terminal.compareAndSet(false, true)) return;
                            cancelTimers.run();
                            Message assistant;
                            try {
                                assistant = persistSuccess(prepared.requestId(), completed.result());
                            } catch (RuntimeException e) {
                                if (operationalMetrics != null) operationalMetrics.streamFinished("failed");
                                boolean persisted = persistFailure(prepared.requestId(), unavailable(), true, false);
                                releaseLease(userId, prepared.requestId());
                                unregister(conversationId, cancellation);
                                if (connected.get() && persisted) sendError(emitter, prepared, unavailable());
                                else if (connected.get()) emitter.completeWithError(e);
                                return;
                            }
                            releaseLease(userId, prepared.requestId());
                            unregister(conversationId, cancellation);
                            if (operationalMetrics != null) operationalMetrics.streamFinished(
                                    assistant == null ? "cancelled" : "completed");
                            if (connected.get() && assistant != null) {
                                try {
                                    send(emitter, "complete", new CompleteEvent(prepared.requestId().toString(),
                                            assistant.getId().toString(), "completed", completed.result().usage()));
                                    emitter.complete();
                                } catch (IOException e) { connected.set(false); emitter.completeWithError(e); }
                            } else if (connected.get()) sendError(emitter, prepared, cancelledFailure());
                        } else if (event instanceof GenerationEvent.Failed failed) {
                            finishFailure.accept(failed.failure());
                        }
                    });
                } catch (RuntimeException e) {
                    finishFailure.accept(cancellation.isCancelled() ? cancelledFailure() : unavailable());
                }
            });
        } catch (IOException e) {
            connected.set(false);
            finishFailure.accept(cancelledFailure());
        } catch (RuntimeException e) {
            finishFailure.accept(unavailable());
        }
        return new ChatStreamHandle(prepared.requestId().toString(), emitter);
    }

    public ChatRequestStatusResponse status(UUID userId, UUID requestId) {
        return required(transactions.execute(status -> {
            ChatTurn turn = turns.findByRequestIdAndUserId(requestId, userId)
                    .orElseThrow(() -> notFound("Request not found"));
            AiRequest request = requests.findById(requestId).orElseThrow(() -> notFound("Request not found"));
            GenerationUsage usage = request.getActualInputTokens() == null || request.getActualOutputTokens() == null
                    ? GenerationUsage.unknown()
                    : new GenerationUsage(request.getActualInputTokens(), request.getActualOutputTokens());
            return new ChatRequestStatusResponse(requestId.toString(), turn.getConversation().getId().toString(),
                    turn.getStatus().toLowerCase(), turn.getFailureCode(),
                    map(turn.getAssistantMessage(), turn.getConversation().getId()), usage);
        }));
    }

    /** Called after the end-session transaction commits, so provider cancellation cannot race a rolled-back end. */
    public void cancelConversation(UUID userId, UUID conversationId) {
        Set<CancellationToken> tokens = activeStreams.get(conversationId);
        if (tokens != null) tokens.forEach(CancellationToken::cancel);
    }

    /** Cancels active upstream streams so their normal terminal path settles usage during task shutdown. */
    @PreDestroy
    public void cancelActiveStreamsOnShutdown() {
        activeStreams.values().forEach(tokens -> tokens.forEach(CancellationToken::cancel));
    }

    private SessionDecision prepareSession(UUID userId, String key) {
        User user = lockActiveUser(userId);
        var repeated = sessionStarts.findByUserIdAndIdempotencyKey(userId, key);
        if (repeated.isPresent()) {
            ChatSessionStart start = repeated.get();
            if (start.getRequestId() == null)
                return new SessionDecision(null, conversationResponse(start.getConversation(), latestAssistant(start.getConversation().getId())));
            ChatTurn turn = turns.findById(start.getRequestId()).orElseThrow(ChatTurnWorkflow::unavailableError);
            if ("IN_PROGRESS".equals(turn.getStatus())) throw conflict("Session greeting is still being generated");
            if (!"COMPLETED".equals(turn.getStatus())) throw providerError(failureFromCode(turn.getFailureCode()));
            return new SessionDecision(null, conversationResponse(start.getConversation(), turn.getAssistantMessage()));
        }

        Conversation active = conversations.findFirstByUserIdAndStatusOrderByStartedAtDesc(userId, "active").orElse(null);
        if (active != null) {
            if (turns.findFirstByConversationIdAndPurposeAndStatusOrderByCreatedAtDesc(
                    active.getId(), "GREETING", "IN_PROGRESS").isPresent())
                throw conflict("Session greeting is still being generated");
            sessionStarts.save(ChatSessionStart.builder().id(UUID.randomUUID()).user(user)
                    .idempotencyKey(key).conversation(active).build());
            return new SessionDecision(null, conversationResponse(active, latestAssistant(active.getId())));
        }

        Conversation conversation = conversations.saveAndFlush(Conversation.builder().user(user)
                .status("active").tokenCount(0).build());
        List<GenerationMessage> generationMessages = List.of(
                new GenerationMessage(GenerationMessage.Role.SYSTEM,
                        prompts.buildSystemPrompt(userId, null, Math.min(600, maxInputTokens / 3))),
                new GenerationMessage(GenerationMessage.Role.USER,
                        "Greet the user briefly, be supportive, and ask how they would like to use this session."));
        ResolvedGeneration resolved = router.resolve(GenerationCommand.serverSelected(userId, GenerationPurpose.GREETING,
                generationMessages, maxOutputTokens));
        UsageReservation reservation = reserve(userId, key, resolved, estimateTokens(generationMessages));
        ChatTurn turn = turns.saveAndFlush(ChatTurn.builder().requestId(reservation.requestId()).user(user)
                .conversation(conversation).idempotencyKey(key).inputHash(hash("GREETING"))
                .purpose("GREETING").status("IN_PROGRESS").build());
        sessionStarts.save(ChatSessionStart.builder().id(UUID.randomUUID()).user(user).idempotencyKey(key)
                .conversation(conversation).requestId(turn.getRequestId()).build());
        return new SessionDecision(new Prepared(turn.getRequestId(), conversation.getId(), null, resolved, null,
                "GREETING", false, null, null), null);
    }

    private Prepared prepareTurn(UUID userId, UUID conversationId, String key, String content) {
        requireKey(key);
        if (content == null || content.isBlank() || content.length() > MAX_MESSAGE_CHARS)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message must contain 1 to 8000 characters.");
        String bodyHash = hash("CHAT|" + conversationId + "|" + content);
        lockActiveUser(userId);
        var previous = turns.findByUserIdAndIdempotencyKey(userId, key);
        if (previous.isPresent()) {
            ChatTurn turn = previous.get();
            if (!turn.getInputHash().equals(bodyHash) || !turn.getConversation().getId().equals(conversationId))
                throw conflict("Idempotency key was already used for a different request");
            if ("IN_PROGRESS".equals(turn.getStatus())) throw conflict("Request is still in progress");
            return new Prepared(turn.getRequestId(), conversationId, turn.getUserMessage(), null,
                    turn.getAssistantMessage(), turn.getPurpose(), true, turn.getStatus(), turn.getFailureCode());
        }
        Conversation conversation = conversations.findByIdForUpdate(conversationId)
                .filter(c -> c.getUser().getId().equals(userId))
                .orElseThrow(() -> notFound("Conversation not found"));
        if (!"active".equals(conversation.getStatus()) || !conversation.isContextEligible())
            throw conflict("Conversation is no longer active");

        Message userMessage = messages.saveAndFlush(Message.builder().conversation(conversation).role("user")
                .content(content).tokenCount(estimateTokens(content)).build());
        String systemPrompt = prompts.buildSystemPrompt(userId, content, Math.min(600, maxInputTokens / 3));
        List<Message> history = messages.findByConversationIdOrderByCreatedAtDescIdDesc(conversationId,
                org.springframework.data.domain.PageRequest.of(0, CONTEXT_MESSAGES));
        history.removeIf(message -> message.getId().equals(userMessage.getId()));
        Collections.reverse(history);
        history.add(userMessage);
        List<GenerationMessage> generationMessages = boundedMessages(systemPrompt, history);
        int inputTokens = estimateTokens(generationMessages);
        if (inputTokens > maxInputTokens)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Conversation context exceeds the configured limit.");
        ResolvedGeneration resolved = router.resolve(GenerationCommand.serverSelected(userId, GenerationPurpose.CHAT,
                generationMessages, maxOutputTokens));
        UsageReservation reservation = reserve(userId, key, resolved, inputTokens);
        turns.saveAndFlush(ChatTurn.builder().requestId(reservation.requestId()).user(users.findById(userId).orElseThrow())
                .conversation(conversation).idempotencyKey(key).inputHash(bodyHash).purpose("CHAT")
                .userMessage(userMessage).status("IN_PROGRESS").build());
        return new Prepared(reservation.requestId(), conversationId, userMessage, resolved, null,
                "CHAT", false, "IN_PROGRESS", null);
    }

    private User lockActiveUser(UUID userId) {
        return users.findByIdForUpdate(userId).filter(User::isActive)
                .orElseThrow(() -> notFound("Account not found"));
    }

    private UsageReservation reserve(UUID userId, String key, ResolvedGeneration generation, int inputTokens) {
        try {
            admission.admit(userId, generation.tier());
            UsageReservation reservation = accounting.reserve(generation, key, inputTokens);
            if (reservation.existing()) throw conflict("An earlier request with this key has no chat result");
            if (!admission.acquireLease(userId, reservation.requestId()))
                throw conflict("Another response is already being generated for this account");
            return reservation;
        } catch (RedisAdmissionService.RateLimitedException e) {
            throw new RateLimitStatusException(e.retryAfterSeconds());
        } catch (UsageException e) {
            throw switch (e.code()) {
                case USER_LIMIT -> new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Daily AI usage limit reached.");
                case CAPACITY_LIMIT, ACCOUNTING_UNAVAILABLE -> unavailableError();
                case IDEMPOTENCY_CONFLICT -> conflict("Idempotency key was already used for a different request");
            };
        }
    }

    private List<GenerationMessage> boundedMessages(String systemPrompt, List<Message> history) {
        List<GenerationMessage> selected = new ArrayList<>();
        selected.add(new GenerationMessage(GenerationMessage.Role.SYSTEM, systemPrompt));
        int available = maxInputTokens * 4 - systemPrompt.length();
        if (available <= 0) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "System context exceeds the configured limit.");
        int used = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            Message message = history.get(i);
            int length = message.getContent().length();
            if (used + length > available) {
                if (i == history.size() - 1 && "user".equals(message.getRole()))
                    throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Message exceeds the configured context limit.");
                continue;
            }
            used += length;
            selected.add(new GenerationMessage("user".equals(message.getRole())
                    ? GenerationMessage.Role.USER : GenerationMessage.Role.ASSISTANT, message.getContent()));
        }
        Collections.reverse(selected.subList(1, selected.size()));
        return List.copyOf(selected);
    }

    private GenerationResult callSync(Prepared prepared) {
        CancellationToken cancellation = new CancellationToken();
        register(prepared.conversationId(), cancellation);
        AtomicBoolean providerAttempted = new AtomicBoolean();
        FutureTask<GenerationResult> task = new FutureTask<>(() -> {
            if (!isConversationActive(prepared.conversationId()) || cancellation.isCancelled())
                throw new GenerationException(cancelledFailure());
            accounting.startAttempt(prepared.requestId());
            providerAttempted.set(true);
            return router.generate(prepared.generation(), cancellation);
        });
        try {
            executor.execute(task);
            return task.get(totalTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            cancellation.cancel();
            task.cancel(true);
            throw new CallFailure(timeoutFailure(), providerAttempted.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancellation.cancel();
            task.cancel(true);
            throw new CallFailure(cancelledFailure(), providerAttempted.get());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            GenerationFailure failure = cause instanceof GenerationException generationException
                    ? generationException.failure() : unavailable();
            throw new CallFailure(failure, providerAttempted.get());
        } catch (RuntimeException e) {
            task.cancel(true);
            throw new CallFailure(unavailable(), providerAttempted.get());
        } finally {
            unregister(prepared.conversationId(), cancellation);
        }
    }

    private void register(UUID conversationId, CancellationToken token) {
        activeStreams.compute(conversationId, (ignored, current) -> {
            Set<CancellationToken> tokens = current == null ? ConcurrentHashMap.newKeySet() : current;
            tokens.add(token);
            return tokens;
        });
    }

    private void unregister(UUID conversationId, CancellationToken token) {
        activeStreams.computeIfPresent(conversationId, (ignored, tokens) -> {
            tokens.remove(token);
            return tokens.isEmpty() ? null : tokens;
        });
    }

    private boolean isConversationActive(UUID conversationId) {
        return Boolean.TRUE.equals(transactions.execute(status -> conversations.findById(conversationId)
                .map(conversation -> "active".equals(conversation.getStatus()) && conversation.isContextEligible())
                .orElse(false)));
    }

    private Message persistSuccess(UUID requestId, GenerationResult result) {
        return required(transactions.execute(status -> {
            ChatTurn turn = turns.findById(requestId).orElseThrow(ChatTurnWorkflow::unavailableError);
            if (!"IN_PROGRESS".equals(turn.getStatus())) return turn.getAssistantMessage();
            Conversation conversation = conversations.findByIdForUpdate(turn.getConversation().getId())
                    .orElseThrow(ChatTurnWorkflow::unavailableError);
            if (!"active".equals(conversation.getStatus()) || !conversation.isContextEligible()) {
                settleFailure(turn, cancelledFailure(), result.usage(), "CANCELLED");
                return null;
            }
            if (result.text().isBlank() || result.text().length() > maxOutputTokens * 20)
                throw new IllegalStateException("Provider response is outside the accepted output bounds");
            Message assistant = messages.saveAndFlush(Message.builder().conversation(conversation).role("assistant")
                    .content(result.text()).tokenCount(result.usage().outputTokens() == null
                            ? estimateTokens(result.text()) : result.usage().outputTokens()).build());
            turn.setAssistantMessage(assistant);
            turn.setStatus("COMPLETED");
            turn.setFailureCode(null);
            int userTokens = turn.getUserMessage() == null ? 0 : turn.getUserMessage().getTokenCount();
            conversation.setTokenCount(conversation.getTokenCount() + userTokens + assistant.getTokenCount());
            conversations.save(conversation);
            turns.save(turn);
            accounting.settle(requestId, result.usage().inputTokens(), result.usage().outputTokens(), "COMPLETED", null);
            return assistant;
        }));
    }

    private boolean persistFailure(UUID requestId, GenerationFailure failure, boolean attempted, boolean greeting) {
        try {
            Boolean persisted = transactions.execute(status -> {
                ChatTurn turn = turns.findById(requestId).orElse(null);
                if (turn == null) return false;
                if (!"IN_PROGRESS".equals(turn.getStatus())) return true;
                String state = failure.code() == GenerationFailure.Code.CANCELLED ? "CANCELLED" : "FAILED";
                turn.setStatus(state);
                turn.setFailureCode(apiCode(failure));
                if (greeting) {
                    Conversation conversation = conversations.findByIdForUpdate(turn.getConversation().getId()).orElse(null);
                    if (conversation != null && "active".equals(conversation.getStatus())) {
                        conversation.setStatus("abandoned");
                        conversation.setEndedAt(Instant.now());
                        conversations.save(conversation);
                    }
                }
                turns.save(turn);
                accounting.settle(requestId, attempted ? null : 0, attempted ? null : 0, state, turn.getFailureCode());
                return true;
            });
            return Boolean.TRUE.equals(persisted);
        } catch (RuntimeException ignored) {
            // Expired durable leases are reconciled by the usage accounting service.
            return false;
        }
    }

    private void settleFailure(ChatTurn turn, GenerationFailure failure, GenerationUsage usage, String status) {
        turn.setStatus(status);
        turn.setFailureCode(apiCode(failure));
        turns.save(turn);
        accounting.settle(turn.getRequestId(), usage.inputTokens(), usage.outputTokens(), status, turn.getFailureCode());
    }

    private ChatMessageResponse replayMessage(UUID userId, Prepared prepared) {
        if ("COMPLETED".equals(prepared.replayStatus()) && prepared.assistantMessage() != null)
            return map(prepared.assistantMessage(), prepared.conversationId());
        throw providerError(failureFromCode(prepared.replayFailure()));
    }

    private void replayStream(UUID userId, SseEmitter emitter, Prepared prepared) {
        try {
            sendStart(emitter, prepared);
            if ("COMPLETED".equals(prepared.replayStatus()) && prepared.assistantMessage() != null) {
                ChatRequestStatusResponse request = status(userId, prepared.requestId());
                send(emitter, "complete", new CompleteEvent(prepared.requestId().toString(),
                        prepared.assistantMessage().getId().toString(), "completed", request.usage()));
            } else sendError(emitter, prepared, failureFromCode(prepared.replayFailure()));
            emitter.complete();
        } catch (IOException e) { emitter.completeWithError(e); }
    }

    private Message latestAssistant(UUID conversationId) {
        return messages.findByConversationIdOrderByCreatedAtDescIdDesc(conversationId,
                        org.springframework.data.domain.PageRequest.of(0, CONTEXT_MESSAGES)).stream()
                .filter(message -> "assistant".equals(message.getRole())).findFirst().orElse(null);
    }

    private ConversationResponse conversationResponse(UUID id, Message message) {
        Conversation conversation = conversations.findById(id).orElseThrow(ChatTurnWorkflow::unavailableError);
        return conversationResponse(conversation, message);
    }

    private ConversationResponse conversationResponse(Conversation conversation, Message message) {
        return ConversationResponse.builder().id(conversation.getId().toString()).status(conversation.getStatus())
                .startedAt(conversation.getStartedAt()).tokenCount(conversation.getTokenCount())
                .recentMessages(message == null ? List.of() : List.of(map(message, conversation.getId()))).build();
    }

    private static void sendStart(SseEmitter emitter, Prepared prepared) throws IOException {
        send(emitter, "start", new StartEvent(prepared.requestId().toString(), prepared.conversationId().toString(),
                prepared.userMessage() == null ? null : prepared.userMessage().getId().toString()));
    }

    private void sendError(SseEmitter emitter, Prepared prepared, GenerationFailure failure) {
        try {
            send(emitter, "error", new ErrorEvent(prepared.requestId().toString(), apiCode(failure), publicMessage(failure),
                    failure.retryable(), false, GenerationUsage.unknown()));
            emitter.complete();
        } catch (IOException e) { emitter.completeWithError(e); }
    }

    private static void send(SseEmitter emitter, String name, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(data));
    }

    private void releaseLease(UUID userId, UUID requestId) {
        try { admission.releaseLease(userId, requestId); } catch (RuntimeException ignored) { }
    }

    private static ChatMessageResponse map(Message message, UUID conversationId) {
        if (message == null) return null;
        return ChatMessageResponse.builder().id(message.getId().toString())
                .conversationId(conversationId.toString()).senderRole(message.getRole())
                .content(message.getContent()).sentAt(message.getCreatedAt()).build();
    }

    private static int estimateTokens(String text) { return Math.max(1, (text.length() + 3) / 4); }
    private static int estimateTokens(List<GenerationMessage> list) {
        return Math.max(1, (list.stream().mapToInt(message -> message.content().length()).sum() + list.size() * 8 + 3) / 4);
    }
    private static String hash(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
    private static void requireKey(String value) {
        try { UUID.fromString(value); }
        catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A UUID Idempotency-Key is required."); }
    }
    private static String apiCode(GenerationFailure failure) {
        return switch (failure.code()) {
            case THROTTLED -> "RATE_LIMITED";
            case TIMEOUT -> "GENERATION_TIMEOUT";
            case CANCELLED -> "GENERATION_CANCELLED";
            case ACCESS_DENIED -> "MODEL_ACCESS_DENIED";
            case MODEL_NOT_FOUND -> "MODEL_UNAVAILABLE";
            case INVALID_REQUEST -> "GENERATION_INVALID_REQUEST";
            case INVALID_RESPONSE -> "GENERATION_INVALID_RESPONSE";
            case UNAVAILABLE -> "PROVIDER_UNAVAILABLE";
        };
    }
    private static String publicMessage(GenerationFailure failure) {
        return switch (failure.code()) {
            case THROTTLED -> "AI capacity is temporarily limited. Please try again shortly.";
            case TIMEOUT -> "The response took too long. Please try again.";
            case CANCELLED -> "The response was canceled.";
            case ACCESS_DENIED, MODEL_NOT_FOUND, INVALID_REQUEST, INVALID_RESPONSE -> "The AI service could not complete this request.";
            case UNAVAILABLE -> "The AI service is temporarily unavailable.";
        };
    }
    private static GenerationFailure failureFromCode(String code) {
        return switch (code == null ? "" : code) {
            case "RATE_LIMITED" -> new GenerationFailure(GenerationFailure.Code.THROTTLED, true);
            case "GENERATION_TIMEOUT" -> timeoutFailure();
            case "GENERATION_CANCELLED", "SESSION_ENDED" -> cancelledFailure();
            case "MODEL_ACCESS_DENIED" -> new GenerationFailure(GenerationFailure.Code.ACCESS_DENIED, false);
            case "MODEL_UNAVAILABLE" -> new GenerationFailure(GenerationFailure.Code.MODEL_NOT_FOUND, false);
            case "GENERATION_INVALID_REQUEST" -> new GenerationFailure(GenerationFailure.Code.INVALID_REQUEST, false);
            case "GENERATION_INVALID_RESPONSE" -> new GenerationFailure(GenerationFailure.Code.INVALID_RESPONSE, false);
            default -> unavailable();
        };
    }
    private static GenerationFailure timeoutFailure() { return new GenerationFailure(GenerationFailure.Code.TIMEOUT, true); }
    private static GenerationFailure cancelledFailure() { return new GenerationFailure(GenerationFailure.Code.CANCELLED, false); }
    private static GenerationFailure unavailable() { return new GenerationFailure(GenerationFailure.Code.UNAVAILABLE, true); }
    private static ResponseStatusException providerError(GenerationFailure failure) {
        HttpStatus status = failure.code() == GenerationFailure.Code.THROTTLED ? HttpStatus.TOO_MANY_REQUESTS
                : failure.code() == GenerationFailure.Code.CANCELLED ? HttpStatus.CONFLICT : HttpStatus.SERVICE_UNAVAILABLE;
        return new ResponseStatusException(status, publicMessage(failure));
    }
    private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static ResponseStatusException unavailableError() { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chat generation is temporarily unavailable."); }
    private static <T> T required(T value) { if (value == null) throw unavailableError(); return value; }
    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) throw new IllegalStateException(name + " must be positive");
        return value;
    }

    private record Prepared(UUID requestId, UUID conversationId, Message userMessage,
                            ResolvedGeneration generation, Message assistantMessage, String purpose,
                            boolean replay, String replayStatus, String replayFailure) { }
    private static final class CallFailure extends RuntimeException {
        private final GenerationFailure failure;
        private final boolean providerAttempted;
        private CallFailure(GenerationFailure failure, boolean providerAttempted) {
            super(failure.code().name());
            this.failure = failure;
            this.providerAttempted = providerAttempted;
        }
        private GenerationFailure failure() { return failure; }
        private boolean providerAttempted() { return providerAttempted; }
    }
    private static final class RateLimitStatusException extends ResponseStatusException {
        private final long retryAfterSeconds;
        private RateLimitStatusException(long retryAfterSeconds) {
            super(HttpStatus.TOO_MANY_REQUESTS, "Chat rate limit exceeded; try again shortly.");
            this.retryAfterSeconds = retryAfterSeconds;
        }
        @Override public org.springframework.http.HttpHeaders getHeaders() {
            org.springframework.http.HttpHeaders headers = super.getHeaders();
            headers.set("Retry-After", Long.toString(retryAfterSeconds));
            return headers;
        }
    }
    private record SessionDecision(Prepared prepared, ConversationResponse response) { }
    private record StartEvent(String requestId, String sessionId, String userMessageId) { }
    private record DeltaEvent(String requestId, String text) { }
    private record CompleteEvent(String requestId, String assistantMessageId, String status, GenerationUsage usage) { }
    private record ErrorEvent(String requestId, String code, String message, boolean retryable,
                              boolean partialPersisted, GenerationUsage usage) { }
}
