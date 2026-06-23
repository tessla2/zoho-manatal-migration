package com.migration.controller;

import com.migration.service.ZohoClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Zoho", description = "Zoho Recruit API direct query endpoints (debug/admin)")
public class ZohoController {

    private final ZohoClientService zohoClientService;

    @Operation(summary = "List Zoho candidates", description = "Returns the first 10 candidates from Zoho Recruit")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Candidates returned successfully"),
        @ApiResponse(responseCode = "502", description = "Error communicating with Zoho", content = @Content)
    })
    @GetMapping("/candidates")
    public ResponseEntity<String> getCandidates() {
        return ResponseEntity.ok(zohoClientService.fetchOneCandidate());
    }

    @Operation(summary = "Fetch and save raw Zoho data",
            description = "Fetches Zoho candidates and saves raw JSON to local database")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Data saved successfully"),
        @ApiResponse(responseCode = "502", description = "Error communicating with Zoho", content = @Content)
    })
    @GetMapping("/candidates/save")
    public ResponseEntity<String> fetchAndSaveCandidates() {
        String response = zohoClientService.fetchAndSaveCandidates();
        return ResponseEntity.ok("Dados salvos no PostgreSQL. Raw JSON: " + response);
    }

    @Operation(summary = "Fetch Zoho candidate by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Candidate found"),
        @ApiResponse(responseCode = "502", description = "Error communicating with Zoho", content = @Content)
    })
    @GetMapping("/candidates/{candidateId}")
    public ResponseEntity<String> getCandidateById(
            @Parameter(description = "ID do candidato no Zoho", example = "76333000000000001")
            @PathVariable String candidateId) {
        return ResponseEntity.ok(zohoClientService.fetchCandidateById(candidateId));
    }

    @Operation(summary = "List candidate attachments in Zoho")
    @GetMapping("/candidates/{candidateId}/attachments")
    public ResponseEntity<String> listCandidateAttachments(
            @Parameter(description = "ID do candidato no Zoho", example = "76333000000000001")
            @PathVariable String candidateId) {
        return ResponseEntity.ok(zohoClientService.listCandidateAttachments(candidateId));
    }

    @Operation(summary = "Download Zoho attachment", description = "Downloads the binary file of an attachment")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File downloaded"),
        @ApiResponse(responseCode = "204", description = "Attachment has no content", content = @Content)
    })
    @GetMapping("/candidates/{candidateId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> downloadAttachment(
            @Parameter(description = "ID do candidato no Zoho") @PathVariable String candidateId,
            @Parameter(description = "ID do attachment no Zoho") @PathVariable String attachmentId) {
        byte[] data = zohoClientService.downloadAttachment(candidateId, attachmentId);
        if (data.length == 0) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    @Operation(summary = "Save Zoho attachment to local DB",
            description = "Downloads and persists a Zoho attachment to the local database")
    @GetMapping("/candidates/{candidateId}/attachments/{attachmentId}/save")
    public ResponseEntity<String> saveAttachment(
            @Parameter(description = "ID do candidato no Zoho") @PathVariable String candidateId,
            @Parameter(description = "ID do attachment") @PathVariable String attachmentId,
            @Parameter(description = "Nome do ficheiro", example = "cv.pdf") @RequestParam String fileName,
            @Parameter(description = "Tipo MIME", example = "application/pdf") @RequestParam(defaultValue = "") String fileType,
            @Parameter(description = "URL de download alternativo") @RequestParam(required = false) String downloadUrl) {
        Long id = zohoClientService.saveAttachment(candidateId, attachmentId, fileName, fileType, downloadUrl);
        if (id == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok("Anexo salvo no PostgreSQL. ID: " + id);
    }

    @Operation(summary = "Fetch one Zoho interview (debug)")
    @GetMapping("/interviews")
    public ResponseEntity<String> fetchOneInterview() {
        return ResponseEntity.ok(zohoClientService.fetchOneInterview());
    }

    @Operation(summary = "Fetch one Zoho application (debug)")
    @GetMapping("/applications")
    public ResponseEntity<String> fetchOneApplication() {
        return ResponseEntity.ok(zohoClientService.fetchOneApplication());
    }

    @Operation(summary = "List application attachments in Zoho")
    @GetMapping("/applications/{applicationId}/attachments")
    public ResponseEntity<String> listApplicationAttachments(
            @Parameter(description = "ID da application") @PathVariable String applicationId) {
        return ResponseEntity.ok(zohoClientService.listApplicationAttachments(applicationId));
    }

    @Operation(summary = "List Zoho tags for a module")
    @GetMapping("/tags")
    public ResponseEntity<String> listTags(
            @Parameter(description = "Módulo Zoho", example = "Candidates")
            @RequestParam(defaultValue = "Candidates") String module) {
        return ResponseEntity.ok(zohoClientService.listTags(module));
    }

}
