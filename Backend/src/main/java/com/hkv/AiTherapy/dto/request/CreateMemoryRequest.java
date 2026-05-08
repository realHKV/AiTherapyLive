package com.hkv.AiTherapy.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateMemoryRequest {
    @NotBlank(message = "Memory type is required")
    private String type;

    @NotBlank(message = "Title is required")
    private String title;

    private String detail;

    @Min(value = 1, message = "Importance must be at least 1")
    @Max(value = 10, message = "Importance cannot be more than 10")
    private int importance = 5;

    private LocalDate occurredAt;
    private LocalDate followUpAt;
}
