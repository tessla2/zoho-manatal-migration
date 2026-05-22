package com.migration.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.migration.model.ManatalCandidate;
import com.migration.transform.utils.ParseUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CandidateMapper {

    private final ParseUtils utils;


    public ManatalCandidate toManatal(JsonNode zoho) {
        ManatalCandidate c = new ManatalCandidate();

        c.setFull_name(field(zoho, "Full_Name"));
        c.setEmail(field(zoho, "Email"));
        c.setPhone_number(field(zoho, "Phone"));

        String city = safeField(zoho, "City", "Candidate_City");
        String country = safeField(zoho, "Country", "Candidate_Country");
        c.setCandidate_location(buildCandidateLocation(city, country));

        c.setConsent(rgpdToConsent(field(zoho, "Consent_to_RGPD")));

        c.setDescription(buildDescription(zoho));

        c.setCreator(1193857);
        c.setOwner(1193857);

        c.setCcurrency(field(zoho, "Currency"));
        c.setEcurrency(field(zoho, "Currency"));
        c.setCountry(field(zoho, "Country"));
        c.setYearofexperience(intField(zoho, "Experience_in_Years"));

        c.setNote(buildNotes(zoho));

        Map<String, Object> customFields = new HashMap<>();

        List<String> rawSkills = utils.parseSkills(zoho);
        if (rawSkills != null && !rawSkills.isEmpty()) {
            customFields.put("skills", rawSkills);
        }

        putIfNotNull(customFields, "canrelocate", yesNoToBool(field(zoho, "Relocation")));
        putIfNotNull(customFields, "workvisaeucitizenship", yesNoToBool(field(zoho, "WorkVisa")));
        putIfNotNull(customFields, "civilstatus", field(zoho, "Civil_Status"));

        Integer availabilityDays = intField(zoho, "Availability_Days");
        if (availabilityDays != null) customFields.put("availabilityweeks", availabilityDays / 7);

        putIfNotNull(customFields, "numberofdependants", intField(zoho, "Number_of_Dependants"));

        putIfNotNull(customFields, "additional_info", field(zoho, "Additional_Information"));
        putIfNotNull(customFields, "first_name", field(zoho, "First_Name"));
        putIfNotNull(customFields, "last_name", field(zoho, "Last_Name"));

        putIfNotNull(customFields, "salary_notes", field(zoho, "Salary_Notes"));
        putIfNotNull(customFields, "city", safeField(zoho, "City", "Candidate_City"));
        Integer currentSalary = parseSalaryField(zoho, "Current_Salary");
        if (currentSalary != null) customFields.put("csalary", currentSalary);

        Integer expectedSalary = parseSalaryField(zoho, "Expected_Salary");
        if (expectedSalary != null) customFields.put("esalary", expectedSalary);

        c.setCustom_fields(customFields);

        return c;
    }

    private String field(JsonNode node, String name) {
        JsonNode f = node.get(name);
        return f != null && !f.isNull() ? f.asText() : null;
    }

    private String safeField(JsonNode node, String primary, String fallback) {
        String val = field(node, primary);
        if (val != null && !val.isBlank() && !"-None-".equals(val)) return val;
        return field(node, fallback);
    }

    private Integer intField(JsonNode node, String name) {
        JsonNode f = node.get(name);
        if (f == null || f.isNull()) return null;
        if (f.isInt()) return f.asInt();
        try { return Integer.parseInt(f.asText().replaceAll("[^\\d-]", "")); }
        catch (NumberFormatException e) { return null; }
    }


    private Integer parseSalaryField(JsonNode node, String name) {
        String raw = field(node, name);
        return raw != null ? utils.parseSalary(raw) : null;
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }

    private Boolean rgpdToConsent(String value) {
        if (value == null) return null;
        return "Given".equalsIgnoreCase(value.trim()) ? true : null;
    }

    private Boolean yesNoToBool(String value) {
        if (value == null) return null;
        return "Yes".equalsIgnoreCase(value.trim()) || "Sim".equalsIgnoreCase(value.trim()) || "true".equalsIgnoreCase(value.trim());
    }

    private Integer ownerId(JsonNode node, String name) {
        JsonNode obj = node.get(name);
        if (obj != null && obj.isObject()) {
            JsonNode id = obj.get("id");
            if (id == null || id.isNull()) return null;
            if (id.isInt()) return id.asInt();
            try { return Integer.parseInt(id.asText()); }
            catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private String buildDescription(JsonNode node) {
        StringBuilder sb = new StringBuilder();
        String summary = field(node, "Candidate_Description_Summary");
        if (summary != null) sb.append(summary);
        String salary = field(node, "Salary_Notes");
        if (salary != null) {
            if (!sb.isEmpty()) sb.append("\n\n");
            sb.append("Salary: ").append(salary);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    public String extractNoteInfo(JsonNode node) {
        String candidateDesc = field(node, "Candidate_Description_Summary");
        if (candidateDesc == null) candidateDesc = "Migrated from Zoho";

        JsonNode createdBy = node.get("Created_By");
        if (createdBy != null && createdBy.isObject()) {
            JsonNode name = createdBy.get("name");
            if (name != null) {
                candidateDesc += "\n\nCreated by: " + name.asText();
            }
        }
        String createdTime = field(node, "Created_Time");
        if (createdTime != null) {
            candidateDesc += "\nDate: " + createdTime;
        }
        return candidateDesc;
    }

    public String extractLinkedinUrl(JsonNode node) {
        String socialProfiles = field(node, "$social_profiles");
        if (socialProfiles != null && !socialProfiles.isBlank()) {
            int pipe = socialProfiles.indexOf('|');
            String url = pipe >= 0 ? socialProfiles.substring(0, pipe) : socialProfiles;
            return utils.normalizeLinkedin(url);
        }
        String[] candidates = {"LinkedIn__s", "LinkedIn", "LinkedIn_URL", "Linkedin_URL"};
        for (String fieldName : candidates) {
            String val = field(node, fieldName);
            if (val != null && !val.isBlank()) {
                return utils.normalizeLinkedin(val);
            }
        }
        return null;
    }

    private String buildCandidateLocation(String city, String country) {
        if (city != null && country != null) return city + ", " + country;
        if (city != null) return city;
        return country;
    }

    private List<ManatalCandidate.ManatalNote> buildNotes(JsonNode node) {
        JsonNode createdBy = node.get("Created_By");
        String creatorName = null;
        if (createdBy != null && createdBy.isObject()) {
            JsonNode name = createdBy.get("name");
            if (name != null) creatorName = name.asText();
        }
        String createdTime = field(node, "Created_Time");
        String candidateDesc = field(node, "Candidate_Description_Summary");

        if (candidateDesc != null || creatorName != null) {
            List<ManatalCandidate.ManatalNote> notes = new ArrayList<>();
            ManatalCandidate.ManatalNote note = new ManatalCandidate.ManatalNote();
            note.setContent(candidateDesc != null ? candidateDesc : "Migrated from Zoho");
            note.setCreator(creatorName);
            note.setCreated_at(createdTime);
            notes.add(note);
            return notes;
        }
        return null;
    }
}
