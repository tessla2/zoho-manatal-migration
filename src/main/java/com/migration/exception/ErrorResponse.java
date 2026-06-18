package com.migration.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API error response")
public class ErrorResponse {

    @Schema(description = "Error timestamp", example = "2026-06-15T10:30:00")
    private final LocalDateTime timestamp;

    @Schema(description = "HTTP status code", example = "400")
    private final int status;

    @Schema(description = "Error reason phrase", example = "Bad Request")
    private final String error;

    @Schema(description = "Error cause detail", example = "Required parameter 'fileUrl' is missing")
    private final String detail;

    @Schema(description = "Request path", example = "/api/migration/candidates/123/preview")
    private final String path;

    @Schema(description = "Field validation errors")
    private final List<FieldError> fieldErrors;

    public ErrorResponse(int status, String error, String detail, String path, List<FieldError> fieldErrors) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.detail = detail;
        this.path = path;
        this.fieldErrors = fieldErrors;
    }

    public static ErrorResponse of(int status, String error, String detail) {
        return new ErrorResponse(status, error, detail, null, null);
    }

    public static ErrorResponse of(int status, String error, String detail, String path) {
        return new ErrorResponse(status, error, detail, path, null);
    }

    public static ErrorResponse withFieldErrors(int status, String error, String detail, List<FieldError> fieldErrors) {
        return new ErrorResponse(status, error, detail, null, fieldErrors);
    }

    @Getter
    @Schema(description = "Specific field validation error")
    public static class FieldError {
        @Schema(description = "Field name", example = "email")
        private final String field;

        @Schema(description = "Error message", example = "Invalid email")
        private final String message;

        public FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }
    }
}
