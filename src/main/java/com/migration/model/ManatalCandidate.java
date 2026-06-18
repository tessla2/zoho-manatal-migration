package com.migration.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Candidate creation payload for Manatal")
public class ManatalCandidate {

    @Schema(description = "Candidate full name", example = "João Silva")
    private String full_name;

    @Schema(description = "Professional description / summary", example = "Senior developer with 10 years experience")
    private String description;

    @Schema(description = "Creator ID (fixed)", example = "1193857")
    private Integer creator;

    @Schema(description = "Owner ID (fixed)", example = "1193857")
    private Integer owner;

    @Schema(description = "Candidate country", example = "Brazil")
    private String country;

    @Schema(description = "GDPR consent", example = "true")
    private Boolean consent;

    @Schema(description = "Candidate location", example = "São Paulo, Brazil")
    private String candidate_location;

    @Schema(description = "Candidate email", example = "joao@email.com")
    private String email;

    @Schema(description = "Candidate phone number", example = "+5511999999999")
    private String phone_number;

    @Schema(description = "Current salary currency", example = "EUR")
    private String ccurrency;

    @Schema(description = "Expected salary currency", example = "EUR")
    private String ecurrency;

    @Schema(description = "Candidate notes")
    private List<ManatalNote> note;

    @Schema(description = "Manatal custom fields")
    private Map<String, Object> custom_fields;

    @Data
    @Schema(description = "Note associated with the candidate")
    public static class ManatalNote {
        @Schema(description = "Note content", example = "Note text...")
        private String content;

        @Schema(description = "Note creator", example = "Admin")
        private String creator;

        @Schema(description = "Creation date", example = "2024-01-01T00:00:00Z")
        private String created_at;
    }
}
