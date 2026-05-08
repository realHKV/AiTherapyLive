package com.hkv.AiTherapy.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiResponse {
    private List<Choice> choices;
    private Usage usage;

    @Data
    public static class Choice {
        private AiMessage message;
        private String finish_reason;
    }

    @Data
    public static class Usage {
        private int prompt_tokens;
        private int completion_tokens;
        private int total_tokens;
    }

    public String getResponseText() {
        if (choices != null && !choices.isEmpty() && choices.get(0).getMessage() != null) {
            return choices.get(0).getMessage().getContent();
        }
        return "";
    }
}
