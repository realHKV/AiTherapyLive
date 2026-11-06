package com.hkv.AiTherapy.dto.request;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class MemoryPatchRequest {
    private String title;
    private String detail;
    private Integer importance;
    private LocalDate occurredAt;
    private LocalDate followUpAt;
    private String status;
    private List<String> clearFields;
}
