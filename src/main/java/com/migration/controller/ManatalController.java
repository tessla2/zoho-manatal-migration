package com.migration.controller;


import com.migration.service.ManatalClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/manatal")
@RequiredArgsConstructor
@Tag(name = "Manatal", description = "Manatal API direct query endpoints (debug/admin)")
public class ManatalController {

    private final ManatalClientService service;

    @Operation(summary = "Fetch first Manatal candidate (debug)")
    @GetMapping("/candidates")
    public ResponseEntity<String> fetchOneCandidate() {
        return ResponseEntity.ok(service.fetchOneCandidate());
    }

    @Operation(summary = "Fetch Manatal candidate by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Candidate found"),
        @ApiResponse(responseCode = "502", description = "Error communicating with Manatal", content = @Content)
    })
    @GetMapping("/candidates/{candidateId}")
    public ResponseEntity<String> fetchCandidateById(
            @Parameter(description = "Manatal candidate ID") @PathVariable String candidateId) {
        return ResponseEntity.ok(service.fetchCandidateById(candidateId));
    }

    @Operation(summary = "Verify Manatal candidate custom fields")
    @GetMapping("/custom-fields")
    public ResponseEntity<Map<String, Object>> fetchCustomFields(
            @Parameter(description = "Candidate ID (optional — uses first if empty)")
            @RequestParam(required = false) String candidateId) {
        Map<String, Object> fields;
        if (candidateId != null && !candidateId.isBlank()) {
            fields = service.fetchCustomFieldsByCandidateId(candidateId);
        } else {
            fields = service.fetchFirstCandidateCustomFields();
        }
        return ResponseEntity.ok(fields);
    }

}
