package com.hkv.AiTherapy.dto.request;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@Data
public class UpdateProfileRequest {
    private String preferredName;
    private String ageRange;
    private String communicationStyle;
    private String aiPersona;
    private List<String> topicsOfConcern;
    private String personaId;
    private String gender;
    private String country;
    private List<String> interests;
    private String additionalNote;
    private List<String> clearFields;

    @JsonProperty("topics_of_concern")
    private String legacyTopicsOfConcern;

    @JsonProperty("enjoyments")
    private String legacyEnjoyments;

    @JsonProperty("selected_therapist")
    private String legacySelectedTherapist;
}
