package com.migration.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Login credentials to obtain JWT token")
public record LoginRequest(
        @Schema(description = "Username", example = "admin")
        String username,
        @Schema(description = "Password", example = "admin123")
        String password
) {
}
