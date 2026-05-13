package com.migration.controller;

import com.migration.service.ZohoClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/zoho")
@RequiredArgsConstructor
public class ZohoController {

    private final ZohoClientService zohoClientService;

    @GetMapping("/candidates")
    public ResponseEntity<String> getCandidates() {
        String response = zohoClientService.fetchOneCandidate();
        return ResponseEntity.ok(response);
    }
}
