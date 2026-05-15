package com.migration.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.migration.model.ManatalCandidate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CandidateMapper {

    public ManatalCandidate toManatal(JsonNode zoho) {
        ManatalCandidate c = new ManatalCandidate();

        c.setName(field(zoho, "Full_Name"));
        c.setEmail(field(zoho, "Email"));
        c.setPhonenumber(field(zoho, "Phone"));
        c.setLinkedin(field(zoho, "LinkedIn__s"));
        c.setCountry(field(zoho, "Country"));
        c.setNationalities(firstFromArray(zoho, "Nationalities"));

        Integer expYears = intField(zoho, "Experience_in_Years");
        c.setYearofexperience(expYears);

        c.setAvailability(intField(zoho, "Availability_Days"));
        c.setNumber_of_dependents(intField(zoho, "Number_of_Dependants"));
        c.setConsent_to_rgpd_(field(zoho, "Consent_to_RGPD"));
        c.setCcurrency(field(zoho, "Currency"));
        c.setEcurrency(field(zoho, "Currency"));

        c.setCreator(ownerName(zoho, "Candidate_Owner"));
        c.setOwner(ownerName(zoho, "Candidate_Owner"));

        String desc = buildDescription(zoho);
        c.setDescription(desc);

        c.setSkills(parseSkills(zoho));

        c.setNote(buildNotes(zoho));

        return c;
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

    private String firstFromArray(JsonNode node, String name) {
        JsonNode arr = node.get(name);
        if (arr != null && arr.isArray() && arr.size() > 0) {
            JsonNode first = arr.get(0);
            return first.isTextual() ? first.asText() : null;
        }
        return null;
    }

    private String ownerName(JsonNode node, String name) {
        JsonNode obj = node.get(name);
        if (obj != null && obj.isObject()) {
            JsonNode n = obj.get("name");
            return n != null ? n.asText() : null;
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

    private List<String> parseSkills(JsonNode node) {
        JsonNode stacks = node.get("Stacks_LinkedIn");
        if (stacks != null && !stacks.isNull()) {
            List<String> list = new ArrayList<>();
            for (String line : stacks.asText().split("\\r?\\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) list.add(trimmed);
            }
            return list;
        }
        JsonNode skills = node.get("Skills");
        if (skills != null && skills.isArray()) {
            List<String> list = new ArrayList<>();
            skills.forEach(s -> list.add(s.asText()));
            return list;
        }
        return null;
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
