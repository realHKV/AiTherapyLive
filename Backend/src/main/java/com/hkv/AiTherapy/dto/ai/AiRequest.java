package com.hkv.AiTherapy.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiRequest {
    private String model;
    
    private List<AiMessage> messages;
    
    private Double temperature;
    
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    
    private Boolean stream;
}
