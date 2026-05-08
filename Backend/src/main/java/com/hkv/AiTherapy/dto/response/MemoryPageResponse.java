package com.hkv.AiTherapy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryPageResponse {
    private List<MemoryResponse> memories;
    private long total;
    private int offset;
    private int limit;
}
