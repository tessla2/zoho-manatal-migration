package com.migration.controller;

import com.migration.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "Serve locally stored files/attachments")
public class FileController {

    private final FileStorageService fileStorageService;

    @Operation(summary = "Serve file by ID",
            description = "Returns the binary content of an attachment stored in the local database")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File found"),
        @ApiResponse(responseCode = "404", description = "File not found", content = @Content)
    })
    @GetMapping({"/{id}", "/{id}/{filename:.+}"})
    public ResponseEntity<byte[]> serveFile(
            @Parameter(description = "Local database file ID") @PathVariable Long id) {
        byte[] data = fileStorageService.getAttachmentData(id);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        String contentType = fileStorageService.getContentType(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(data);
    }
}
