package model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ZohoAttachment {
    private String id;
    private String fileName;
    private String fileType;     // "Resume", "Cover Letter", "Others", etc.
    private byte[] content;
    private boolean isResume;    // true = vai para /resume/, false = /attachments/
}
