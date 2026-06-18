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
        return type != null ? type : "application/octet-stream";
    }

    public String getFileName(Long id) {
        StoredAttachment attachment = getAttachment(id);
        if (attachment == null) return "unknown";
        return attachment.getFileName();
    }
}
