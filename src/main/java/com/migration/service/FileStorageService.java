package com.migration.service;

import com.migration.entity.StoredAttachment;
import com.migration.repository.StoredAttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final StoredAttachmentRepository repository;

    public StoredAttachment getAttachment(Long id) {
        return repository.findById(id).orElse(null);
    }

    public byte[] getAttachmentData(Long id) {
        StoredAttachment attachment = getAttachment(id);
        if (attachment == null) return null;
        return attachment.getData();
    }

    public String getContentType(Long id) {
        StoredAttachment attachment = getAttachment(id);
        if (attachment == null) return "application/octet-stream";
        String type = attachment.getFileType();
        if (type != null && !type.isBlank() && !"application/octet-stream".equals(type))
            return type;
        return inferContentType(attachment.getFileName());
    }

    private String inferContentType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".rtf")) return "application/rtf";
        return "application/octet-stream";
    }

    public String getFileName(Long id) {
        StoredAttachment attachment = getAttachment(id);
        if (attachment == null) return "unknown";
        return attachment.getFileName();
    }
}
