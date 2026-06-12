package com.migration.controller;

import com.migration.model.ManatalAttachment;
import com.migration.model.ManatalResume;
import com.migration.service.ManatalClientService;
import com.migration.service.MigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/migration")
@RequiredArgsConstructor
public class MigrationController {

    private final MigrationService migrationService;
    private final ManatalClientService manatalClientService;

    @GetMapping("/candidates/{candidateId}/preview")
    public ResponseEntity<?> previewTransform(@PathVariable String candidateId) {
        try {
            return ResponseEntity.ok(migrationService.previewCandidate(candidateId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro: " + e.getMessage());
        }
    }

    @PostMapping("/candidates/{candidateId}/migrate")
    public ResponseEntity<String> migrateSingle(@PathVariable String candidateId) {
        try {
            return ResponseEntity.ok(migrationService.migrateCandidate(candidateId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro: " + e.getMessage());
        }
    }

    @GetMapping("/custom-fields/verify")
    public ResponseEntity<?> verifyCustomFields(@RequestParam(required = false) String candidateId) {
        try {
            return ResponseEntity.ok(migrationService.verifyCustomFields(candidateId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro: " + e.getMessage());
        }
    }

    @PostMapping("/candidates/{manatalId}/attachments/test")
    public ResponseEntity<String> testAttachment(
            @PathVariable String manatalId,
            @RequestParam String fileUrl,
            @RequestParam(defaultValue = "test.pdf") String fileName) {
        try {
            ManatalAttachment attachment = new ManatalAttachment();
            attachment.setName(fileName);
            attachment.setDescription("Test attachment");
            attachment.setFile(fileUrl);
            String response = manatalClientService.createAttachment(manatalId, attachment);
            return ResponseEntity.ok("Attachment created: " + response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro: " + e.getMessage());
        }
    }

    @PostMapping("/candidates/{manatalId}/resume/test")
    public ResponseEntity<String> testResume(
            @PathVariable String manatalId,
            @RequestParam String fileUrl) {
        try {
            ManatalResume resume = new ManatalResume();
            resume.setResume_file(fileUrl);
            String response = manatalClientService.updateResume(manatalId, resume);
            return ResponseEntity.ok("Resume updated: " + response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro: " + e.getMessage());
        }
    }

    @GetMapping("/tags/verify")
    public ResponseEntity<?> verifyTag(@RequestParam(defaultValue = "Candidates") String module) {
        try {
            return ResponseEntity.ok(migrationService.verifyTag(module));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro: " + e.getMessage());
        }
    }
}
