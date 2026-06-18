package com.migration.controller;

import com.migration.dto.LoginRequest;
import com.migration.security.JwtUtils;
import com.migration.security.SecurityProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "API authentication (JWT Bearer Token)")
public class AuthController {

    private final SecurityProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final String encodedPassword;

    public AuthController(SecurityProperties properties,
                          PasswordEncoder passwordEncoder,
                          JwtUtils jwtUtils) {
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        String raw = properties.adminPassword();
        if (raw.startsWith("$2a$") || raw.startsWith("$2b$") || raw.startsWith("$2y$")) {
            this.encodedPassword = raw;
        } else {
            this.encodedPassword = passwordEncoder.encode(raw);
        }
    }

    @Operation(summary = "Authentication", description = "Generates a JWT token to access protected endpoints")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token generated successfully",
                content = @Content(mediaType = "application/json",
                examples = @ExampleObject(value = "{\"token\": \"eyJhbG...\", \"expiresIn\": \"24h\"}"))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content)
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (!properties.adminUsername().equals(request.username())) {
            return ResponseEntity.status(401).body(Map.of("error", "Credenciais inválidas"));
        }

        if (!passwordEncoder.matches(request.password(), encodedPassword)) {
            return ResponseEntity.status(401).body(Map.of("error", "Credenciais inválidas"));
        }

        String token = jwtUtils.generateToken(request.username());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "expiresIn", properties.jwtExpirationHours() + "h"
        ));
    }
}
