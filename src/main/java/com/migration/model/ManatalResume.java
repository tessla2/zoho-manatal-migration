package com.migration.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Resume (CV) to be sent to Manatal (public URL)")
public class ManatalResume {
    @Schema(description = "Public resume file URL", example = "https://example.com/files/cv.pdf")
    private String resume_file;
}
