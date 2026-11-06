package com.hkv.AiTherapy.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorDetail error;
    private Instant timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorDetail(code, message, null), Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message, Map<String, String> details) {
        return new ApiResponse<>(false, null, new ErrorDetail(code, message, details), Instant.now());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetail {
        private String code;
        private String message;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Map<String, String> details;

        public ErrorDetail(String code, String message) {
            this(code, message, null);
        }
    }
}
