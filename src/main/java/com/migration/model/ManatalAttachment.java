package com.migration.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Attachment to be sent to Manatal (public URL)")
public class ManatalAttachment {
    @Schema(description = "File name", example = "cv.pdf")
    private String name;

    @Schema(description = "Attachment description", example = "Candidate CV")
    private String description;

    @Schema(description = "Public file URL", example = "https://example.com/files/123")
    private String file;
}
