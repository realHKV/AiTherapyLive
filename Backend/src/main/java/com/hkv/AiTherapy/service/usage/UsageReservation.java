package com.hkv.AiTherapy.service.usage;

import java.time.Instant;
import java.util.UUID;

public record UsageReservation(UUID requestId, boolean existing, Instant leaseExpiresAt) { }
