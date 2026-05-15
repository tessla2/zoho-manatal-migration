package com.migration.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.model.ManatalCandidate;
import com.migration.service.ManatalClientService;
import com.migration.service.ZohoClientService;
import com.migration.transform.CandidateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/migration")
@RequiredArgsConstructor
public class MigrationController {

    private final ZohoClientService zohoClientService;
    private final ManatalClientService manatalClientService;
    private final CandidateMapper candidateMapper;
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
            return ResponseEntity.ok("Migrated: " + response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro: " + e.getMessage());
        }
    }
}
