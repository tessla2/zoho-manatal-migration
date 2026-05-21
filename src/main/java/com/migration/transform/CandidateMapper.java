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
        c.setCountry(field(zoho, "Country"));

        Integer expYears = intField(zoho, "Experience_in_Years");
        c.setYearofexperience(expYears);

        c.setAvailability(intField(zoho, "Availability_Days"));
        c.setNumber_of_dependents(intField(zoho, "Number_of_Dependants"));
        c.setConsent_to_rgpd_(field(zoho, "Consent_to_RGPD"));
        String currency = field(zoho, "Currency");
        c.setCcurrency(currency != null ? currency : "EUR");
        c.setEcurrency(currency != null ? currency : "EUR");

        c.setWorktype("permanent");

        c.setCreator(ownerName(zoho, "Candidate_Owner"));
        c.setOwner(ownerName(zoho, "Candidate_Owner"));

        String desc = buildDescription(zoho);
        c.setDescription(desc);

        String linkedinRaw = field(zoho, "LinkedIn__s");
        c.setLinkedin(utils.normalizeLinkedin(linkedinRaw));

        List<String> rawSkills = utils.parseSkills(zoho);
        c.setSkills(rawSkills);

        c.setNote(buildNotes(zoho));

        Map<String, Object> customFields = buildCustomFields(c);

        Integer currentSalary = parseSalaryField(zoho, "current_salary");
        if (currentSalary != null) customFields.put("csalary", currentSalary);

        Integer expectedSalary = parseSalaryField(zoho, "expected_salary");
        if (expectedSalary != null) customFields.put("esalary", expectedSalary);

        String additionalInfo = field(zoho, "additional_info");
        if (additionalInfo != null) customFields.put("additional_info", additionalInfo);

        c.setCustom_fields(customFields);

        return c;
    }

    private Map<String, Object> buildCustomFields(ManatalCandidate c) {
        Map<String, Object> fields = new HashMap<>();
        if (c.getCountry() != null) fields.put("country", c.getCountry());
        if (c.getLinkedin() != null) fields.put("linkedin", c.getLinkedin());
        if (c.getSkills() != null && !c.getSkills().isEmpty()) fields.put("skills", c.getSkills());
        if (c.getWorktype() != null) fields.put("worktype", c.getWorktype());
        if (c.getCcurrency() != null) fields.put("ccurrency", c.getCcurrency());
        if (c.getEcurrency() != null) fields.put("ecurrency", c.getEcurrency());
        if (c.getYearofexperience() != null) fields.put("yearofexperience", c.getYearofexperience());
        if (c.getAvailability() != null) fields.put("availability", c.getAvailability());
        if (c.getNumber_of_dependents() != null) fields.put("number_of_dependents", c.getNumber_of_dependents());
        if (c.getConsent_to_rgpd_() != null) fields.put("consent_to_rgpd_", c.getConsent_to_rgpd_());
        return fields.isEmpty() ? null : fields;
    }

    private String field(JsonNode node, String name) {
        JsonNode f = node.get(name);
        return f != null && !f.isNull() ? f.asText() : null;
    }

    private Integer intField(JsonNode node, String name) {
        JsonNode f = node.get(name);
        if (f == null || f.isNull()) return null;
        return f.isInt() ? f.asInt() : null;
    }


    private Integer parseSalaryField(JsonNode node, String name) {
        String raw = field(node, name);
        return raw != null ? utils.parseSalary(raw) : null;
    }

    private Integer ownerName(JsonNode node, String name) {
        JsonNode obj = node.get(name);
        if (obj != null && obj.isObject()) {
            JsonNode id = obj.get("id");
            if (id != null && id.isInt()) return id.asInt();
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
