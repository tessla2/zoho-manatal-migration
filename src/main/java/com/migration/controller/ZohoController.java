package com.migration.controller;

import com.migration.service.ZohoClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/zoho")
@RequiredArgsConstructor
public class ZohoController {

    private final ZohoClientService zohoClientService;

    //Candidates
    @GetMapping("/candidates")
    public ResponseEntity<String> getCandidates() {
        String  response = zohoClientService.fetchOneCandidate();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/candidates/save")
    public ResponseEntity<String> fetchAndSaveCandidates() {
        String response = zohoClientService.fetchAndSaveCandidates();
        return ResponseEntity.ok("Dados salvos no PostgreSQL. Raw JSON: " + response);
    }

    @GetMapping("/candidates/{candidateId}")
    public ResponseEntity<String> getCandidateById(@PathVariable String candidateId) {
        String response = zohoClientService.fetchCandidateById(candidateId);
        return ResponseEntity.ok(response);
    }


    //Attachments
    @GetMapping("/candidates/{candidateId}/attachments")
    public ResponseEntity<String> listCandidateAttachments(@PathVariable String candidateId) {
        String response = zohoClientService.listCandidateAttachments(candidateId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/attachments/{attachmentId}")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable String attachmentId) {
        byte[] data = zohoClientService.downloadAttachment(attachmentId);
        if (data.length == 0) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    @GetMapping("/candidates/{candidateId}/attachments/{attachmentId}/save")
    public ResponseEntity<String> saveAttachment(
            @PathVariable String candidateId,
            @PathVariable String attachmentId,
            @RequestParam String fileName,
            @RequestParam(defaultValue = "") String fileType,
            @RequestParam(required = false) String downloadUrl) {
        Long id = zohoClientService.saveAttachment(candidateId, attachmentId, fileName, fileType, downloadUrl);
        if (id == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok("Anexo salvo no PostgreSQL. ID: " + id);
    }

    //Interviews
    @GetMapping("/interviews")
    public ResponseEntity<String> fetchOneInterview() {
        String response = zohoClientService.fetchOneInterview();
        return ResponseEntity.ok(response);
    }

    //Applications
    @GetMapping("/applications")
    public ResponseEntity<String> fetchOneApplication() {
        String response = zohoClientService.fetchOneApplication();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/applications/{applicationId}/attachments")
    public ResponseEntity<String> listApplicationAttachments(@PathVariable String applicationId) {
        String response = zohoClientService.listApplicationAttachments(applicationId);
        return ResponseEntity.ok(response);
    }

    // Tags //
    @GetMapping("/tags")
    public ResponseEntity<?> listTags(@RequestParam(defaultValue = "Candidates") String module) {
        try {
            String response = zohoClientService.listTags(module);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro: " + e.getMessage());
        }
    }

}
