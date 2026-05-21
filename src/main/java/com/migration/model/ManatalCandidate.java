package com.migration.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ManatalCandidate {

    private String full_name;
    private String description;
    private Integer creator;
    private Integer owner;
    private Integer yearofexperience;
    private String country;
    private Integer availability;
    private String nationalities;
    private Integer number_of_dependents;
    private String consent_to_rgpd_;
    private Boolean aceitar_condi_es;
    private String email;
    private String phone_number;
    private String ccurrency;
    private String ecurrency;
    private String worktype;
    private String linkedin;
    private List<String> skills;
    private List<ManatalNote> note;
    private Map<String, Object> custom_fields;

    @Data
    public static class ManatalNote {
        private String content;
        private String creator;
        private String created_at;
    }
}
