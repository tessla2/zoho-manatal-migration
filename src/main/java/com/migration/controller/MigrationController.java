package com.migration.controller;

import com.migration.model.ManatalAttachment;
import com.migration.model.ManatalResume;
import com.migration.service.ManatalClientService;
import com.migration.service.MigrationService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/migration")
@RequiredArgsConstructor
@Tag(name = "Migration", description = "Zoho → Manatal candidate migration endpoints")
public class MigrationController {

    private final MigrationService migrationService;
    private final ManatalClientService manatalClientService;

    @Operation(summary = "Preview transformation",
            description = "Simulates the Zoho-to-Manatal candidate transformation without sending it")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transformation executed successfully"),
        @ApiResponse(responseCode = "502", description = "Error communicating with Zoho", content = @Content)
    })
    @GetMapping("/candidates/{candidateId}/preview")
    public ResponseEntity<?> previewTransform(
            @Parameter(description = "Zoho candidate ID", example = "76333000000000001")
            @PathVariable String candidateId) throws Exception {
        return ResponseEntity.ok(migrationService.previewCandidate(candidateId));
    }

    @Operation(summary = "Migrate single candidate",
            description = "Migrates a candidate from Zoho to Manatal (creation + notes + social media)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Candidate migrated successfully"),
        @ApiResponse(responseCode = "502", description = "Error communicating with Zoho or Manatal", content = @Content)
    })
    @PostMapping("/candidates/{candidateId}/migrate")
    public ResponseEntity<String> migrateSingle(
            @Parameter(description = "Zoho candidate ID", example = "76333000000000001")
            @PathVariable String candidateId) throws Exception {
        return ResponseEntity.ok(migrationService.migrateCandidate(candidateId));
    }

    @Operation(summary = "Verify Manatal custom fields",
            description = "Compares expected custom fields (from mapper) with existing ones in Manatal")
    @GetMapping("/custom-fields/verify")
    public ResponseEntity<?> verifyCustomFields(
            @Parameter(description = "Manatal candidate ID (optional — uses first if empty)")
            @RequestParam(required = false) String candidateId) throws Exception {
        return ResponseEntity.ok(migrationService.verifyCustomFields(candidateId));
    }

    @Operation(summary = "Test attachment in Manatal",
            description = "Sends an attachment via public URL to an existing Manatal candidate (test)")
    @PostMapping("/candidates/{manatalId}/attachments/test")
    public ResponseEntity<String> testAttachment(
            @Parameter(description = "Manatal candidate ID") @PathVariable String manatalId,
            @Parameter(description = "Public file URL", example = "https://example.com/file.pdf")
            @RequestParam String fileUrl,
            @Parameter(description = "File name", example = "cv.pdf")
            @RequestParam(defaultValue = "test.pdf") String fileName) {
        ManatalAttachment attachment = new ManatalAttachment();
        attachment.setName(fileName);
        attachment.setDescription("Test attachment");
        attachment.setFile(fileUrl);
        String response = manatalClientService.createAttachment(manatalId, attachment);
        return ResponseEntity.ok("Attachment created: " + response);
    }

    @Operation(summary = "Test resume in Manatal",
            description = "Sends a resume via public URL to an existing Manatal candidate (test)")
    @PostMapping("/candidates/{manatalId}/resume/test")
    public ResponseEntity<String> testResume(
            @Parameter(description = "Manatal candidate ID") @PathVariable String manatalId,
            @Parameter(description = "Public resume file URL", example = "https://example.com/cv.pdf")
            @RequestParam String fileUrl) {
        ManatalResume resume = new ManatalResume();
        resume.setResume_file(fileUrl);
        String response = manatalClientService.updateResume(manatalId, resume);
        return ResponseEntity.ok("Resume updated: " + response);
    }

    @Operation(summary = "Verify Zoho tag",
            description = "Checks whether the migration tag is configured in Zoho Recruit")
    @GetMapping("/tags/verify")
    public ResponseEntity<?> verifyTag(
            @Parameter(description = "Zoho module", example = "Candidates")
            @RequestParam(defaultValue = "Candidates") String module) throws Exception {
        return ResponseEntity.ok(migrationService.verifyTag(module));
    }
}
