package com.hkv.AiTherapy.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class SummaryExtraction {
    private String summary;
    private List<ProfileCandidateExtraction> profileCandidates;
    private List<TraitExtraction> traits;
    private List<MemoryExtraction> memories;

    @Data
    public static class ProfileCandidateExtraction {
        private String field;
        private String value;
        private String sourceMessageId;
        private String evidence;
    }

    @Data
    public static class TraitExtraction {
        private String key;
        private String value;
        private Double confidence;
        private String sourceMessageId;
        private String evidence;
    }

    @Data
    public static class MemoryExtraction {
        private String type;
        private String title;
        private String detail;
        private int importance;
        private String followUpAt; // YYYY-MM-DD
        private String sourceMessageId;
        private String evidence;
    }
}
