package com.migration.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.config.ZohoProperties;
import com.migration.model.ManatalCandidate;
import com.migration.service.ManatalClientService;
import com.migration.service.ZohoClientService;
import com.migration.transform.CandidateMapper;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/migration")
@RequiredArgsConstructor
public class MigrationController {

    private final ZohoClientService zohoClientService;
    private final ManatalClientService manatalClientService;
    private final CandidateMapper candidateMapper;
    private final ZohoProperties zohoProperties;
    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping("/candidates/{candidateId}/preview")
    public ResponseEntity<?> previewTransform(@PathVariable String candidateId) {
        try {
            String zohoJson = zohoClientService.fetchCandidateById(candidateId);
            JsonNode zohoData = mapper.readTree(zohoJson).path("data").get(0);
            ManatalCandidate transformed = candidateMapper.toManatal(zohoData);
            return ResponseEntity.ok(transformed);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro: " + e.getMessage());
        }
    }

    @GetMapping("/candidates/{candidateId}/migrate")
    public ResponseEntity<String> migrateSingle(@PathVariable String candidateId) {
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
            return ResponseEntity.ok("Migrated: " + response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro: " + e.getMessage());
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
            String noteInfo = candidateMapper.extractNoteInfo(zohoData);
            if (noteInfo != null) {
                manatalClientService.createNote(manatalId, noteInfo);
            }
        }
    }

    @GetMapping("/custom-fields/verify")
    public ResponseEntity<?> verifyCustomFields(@RequestParam(required = false) String candidateId) {
        try {
            java.util.Map<String, Object> existingFields;
            if (candidateId != null && !candidateId.isBlank()) {
                existingFields = manatalClientService.fetchCustomFieldsByCandidateId(candidateId);
            } else {
                existingFields = manatalClientService.fetchFirstCandidateCustomFields();
            }

            String zohoJson = zohoClientService.fetchOneCandidate();
            JsonNode zohoData = mapper.readTree(zohoJson).path("data").get(0);
            ManatalCandidate transformed = candidateMapper.toManatal(zohoData);
            java.util.Map<String, Object> expectedFields = transformed.getCustom_fields();

            java.util.Set<String> existingKeys = existingFields.keySet();
            java.util.Set<String> expectedKeys = expectedFields.keySet();

            java.util.Set<String> present = new java.util.HashSet<>(expectedKeys);
            present.retainAll(existingKeys);

            java.util.Set<String> missing = new java.util.HashSet<>(expectedKeys);
            missing.removeAll(existingKeys);

            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("existingCustomFields", existingFields);
            result.put("expectedCustomFields", expectedFields);
            result.put("presentKeys", present);
            result.put("missingKeys", missing);
            result.put("allConfigured", missing.isEmpty());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro: " + e.getMessage());
        }
    }

    @GetMapping("/tags/verify")
    public ResponseEntity<?> verifyTag(@RequestParam(defaultValue = "Candidates") String module) {
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

            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("module", module);
            result.put("expectedTag", expectedTag);
            result.put("tagExists", tagExists);
            if (tagNode != null) {
                result.put("tagId", tagNode.path("id").asText());
                result.put("tagDetails", mapper.convertValue(tagNode, java.util.Map.class));
            }
            result.put("allTags", tagsData);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro: " + e.getMessage());
        }
    }
}
