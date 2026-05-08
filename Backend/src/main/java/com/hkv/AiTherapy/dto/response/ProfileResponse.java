package com.hkv.AiTherapy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {
    private String userId;
    private String preferredName;
    private String ageRange;
    private String communicationStyle;
    private String aiPersona;
    private List<String> topicsOfConcern;
    private int totalSessions;
    private List<TraitDto> traits;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TraitDto {
        private String key;
        private double confidence;
        private String source;
    }
}
