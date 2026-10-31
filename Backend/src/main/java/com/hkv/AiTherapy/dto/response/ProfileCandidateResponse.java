package com.hkv.AiTherapy.dto.response;

import java.util.UUID;

public record ProfileCandidateResponse(UUID id, String field, String proposedValue,
                                       String evidence, Double confidence) { }
