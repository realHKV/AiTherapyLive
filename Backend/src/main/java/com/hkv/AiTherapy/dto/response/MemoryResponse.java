package com.hkv.AiTherapy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryResponse {
    private String id;
    private String type;
    private String title;
    private String detail;
    private int importance;
    private LocalDate occurredAt;
    private LocalDate followUpAt;
    private boolean isResolved;
}
