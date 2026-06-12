package com.migration.security;

import com.migration.dto.LoginRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
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
