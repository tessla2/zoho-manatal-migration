package com.migration.controller;

import com.migration.service.FileStorageService;
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
public class FileController {

    private final FileStorageService fileStorageService;

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> serveFile(@PathVariable Long id) {
        byte[] data = fileStorageService.getAttachmentData(id);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        String contentType = fileStorageService.getContentType(id);
        String fileName = fileStorageService.getFileName(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header("Content-Disposition", "inline; filename=\"" + fileName + "\"")
                .body(data);
    }
}
