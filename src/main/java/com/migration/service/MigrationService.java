package com.migration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.config.ZohoProperties;
import com.migration.model.ManatalCandidate;
import com.migration.transform.CandidateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationService {

    private final ZohoClientService zohoClientService;
    private final ManatalClientService manatalClientService;
    private final CandidateMapper candidateMapper;
    private final ZohoProperties zohoProperties;
    private final ObjectMapper mapper;

    public ManatalCandidate previewCandidate(String candidateId) {
        try {
            String zohoJson = zohoClientService.fetchCandidateById(candidateId);
            JsonNode zohoData = mapper.readTree(zohoJson).path("data").get(0);
            return candidateMapper.toManatal(zohoData);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao processar preview", e);
        }
    }

    public String migrateCandidate(String candidateId) {
        try {
            String zohoJson = zohoClientService.fetchCandidateById(candidateId);
            JsonNode zohoData = mapper.readTree(zohoJson).path("data").get(0);
            ManatalCandidate transformed = candidateMapper.toManatal(zohoData);
            String response = manatalClientService.createCandidate(transformed);
            String manatalId = mapper.readTree(response).path("id").asText();

            postNotes(manatalId, candidateId, zohoData);

            String linkedinUrl = candidateMapper.extractLinkedinUrl(zohoData);
            if (linkedinUrl != null) {
                manatalClientService.createSocialMedia(manatalId, "linkedin", linkedinUrl);
            }
            return "Migrated: " + response;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao migrar candidato", e);
        }
    }

    private void postNotes(String manatalId, String candidateId, JsonNode zohoData) {
        try {
            String notesJson = zohoClientService.fetchCandidateNotes(candidateId);
            JsonNode notesData = mapper.readTree(notesJson).path("data");
            boolean postedAny = false;
            if (notesData.isArray()) {
                for (JsonNode note : notesData) {
                    String content = note.path("Note_Content").asText("");
                    String title = note.path("Note_Title").asText("");
                    String createdBy = note.path("Created_By").path("name").asText("");
                    String createdTime = note.path("Created_Time").asText("");

                    StringBuilder sb = new StringBuilder();
                    if (!title.isBlank()) sb.append("**").append(title).append("**\n\n");
                    sb.append(content);
                    if (!createdBy.isBlank()) sb.append("\n\n---\nBy: ").append(createdBy);
                    if (!createdTime.isBlank()) sb.append("\nDate: ").append(createdTime);

                    manatalClientService.createNote(manatalId, sb.toString());
                    postedAny = true;
                }
            }
            if (!postedAny) {
                String noteInfo = candidateMapper.extractNoteInfo(zohoData);
                if (noteInfo != null) {
                    manatalClientService.createNote(manatalId, noteInfo);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to post notes for candidate {}: {}", candidateId, e.getMessage());
            String noteInfo = candidateMapper.extractNoteInfo(zohoData);
            if (noteInfo != null) {
                manatalClientService.createNote(manatalId, noteInfo);
            }
        }

        postStructuredInfo(manatalId, zohoData);
        postInterviewNotes(manatalId, candidateId);
    }

    private void postStructuredInfo(String manatalId, JsonNode zohoData) {
        try {
            String structuredInfo = candidateMapper.extractStructuredInfo(zohoData);
            if (structuredInfo != null) {
                manatalClientService.createNote(manatalId, structuredInfo);
            }
        } catch (Exception e) {
            log.warn("Failed to post structured info for candidate {}: {}", manatalId, e.getMessage());
        }
    }

    private void postInterviewNotes(String manatalId, String candidateId) {
        try {
            String interviewsJson = zohoClientService.fetchInterviewsByCandidate(candidateId);
            JsonNode interviewsData = mapper.readTree(interviewsJson).path("data");
            if (interviewsData.isArray()) {
                for (JsonNode interview : interviewsData) {
                    String interviewNote = candidateMapper.extractInterviewInfo(interview);
                    if (interviewNote != null) {
                        manatalClientService.createNote(manatalId, interviewNote);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to post interview notes for candidate {}: {}", candidateId, e.getMessage());
        }
    }

    public Map<String, Object> verifyCustomFields(String candidateId) {
        try {
            Map<String, Object> existingFields;
            if (candidateId != null && !candidateId.isBlank()) {
                existingFields = manatalClientService.fetchCustomFieldsByCandidateId(candidateId);
            } else {
                existingFields = manatalClientService.fetchFirstCandidateCustomFields();
            }

            String zohoJson = zohoClientService.fetchOneCandidate();
            JsonNode zohoData = mapper.readTree(zohoJson).path("data").get(0);
            ManatalCandidate transformed = candidateMapper.toManatal(zohoData);
            Map<String, Object> expectedFields = transformed.getCustom_fields();

            Set<String> existingKeys = existingFields.keySet();
            Set<String> expectedKeys = expectedFields.keySet();

            Set<String> present = new java.util.HashSet<>(expectedKeys);
            present.retainAll(existingKeys);

            Set<String> missing = new java.util.HashSet<>(expectedKeys);
            missing.removeAll(existingKeys);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("existingCustomFields", existingFields);
            result.put("expectedCustomFields", expectedFields);
            result.put("presentKeys", present);
            result.put("missingKeys", missing);
            result.put("allConfigured", missing.isEmpty());

            return result;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao verificar custom fields", e);
        }
    }

    public Map<String, Object> verifyTag(String module) {
        try {
            String tagsJson = zohoClientService.listTags(module);
            JsonNode tagsData = mapper.readTree(tagsJson).path("data").path("tags");

            String expectedTag = zohoProperties.tagName();
            boolean tagExists = false;
            JsonNode tagNode = null;
            if (tagsData.isArray()) {
                for (JsonNode tag : tagsData) {
                    String name = tag.path("name").asText();
                    if (expectedTag.equals(name)) {
                        tagExists = true;
                        tagNode = tag;
                        break;
                    }
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("module", module);
            result.put("expectedTag", expectedTag);
            result.put("tagExists", tagExists);
            if (tagNode != null) {
                result.put("tagId", tagNode.path("id").asText());
                result.put("tagDetails", mapper.convertValue(tagNode, java.util.Map.class));
            }
            result.put("allTags", tagsData);

            return result;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao verificar tag", e);
        }
    }
}
