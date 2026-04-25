package com.hkv.AiTherapy.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class SummaryExtraction {
    private String summary;
    private ProfileUpdates profileUpdates;
    private List<TraitExtraction> traits;
    private List<MemoryExtraction> memories;

    @Data
    public static class ProfileUpdates {
        private String name;
        private String age;
        private String gender;
    }

    @Data
    public static class TraitExtraction {
        private String key;
        private double confidence;
    }

    @Data
    public static class MemoryExtraction {
        private String type;
        private String title;
        private String detail;
        private int importance;
        private String followUpAt; // YYYY-MM-DD
    }
}
