package com.hkv.AiTherapy.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class UpdateProfileRequest {
    private String preferredName;
    private String ageRange;
    private String communicationStyle;
    private String aiPersona;
    private List<String> topicsOfConcern;
}
