package com.migration.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ManatalCandidate {

    private String full_name;
    private String description;
    private Integer creator;
    private Integer owner;
    private String country;
    private Boolean consent;
    private String candidate_location;
    private String email;
    private String phone_number;
    private String ccurrency;
    private String ecurrency;
    private List<ManatalNote> note;
    private Map<String, Object> custom_fields;

    @Data
    public static class ManatalNote {
        private String content;
        private String creator;
        private String created_at;
    }
}
