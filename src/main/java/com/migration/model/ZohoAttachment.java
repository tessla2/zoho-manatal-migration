package com.migration.model;

import lombok.Data;

@Data
public class ZohoAttachment {
    private String id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String parentId;
    private String createdTime;
    private String createdBy;
}
