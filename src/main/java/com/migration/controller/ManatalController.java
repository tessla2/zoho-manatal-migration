package com.migration.controller;


import com.migration.service.ManatalClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/manatal")
@RequiredArgsConstructor
public class ManatalController {

    private final ManatalClientService service;

    @GetMapping("/candidates")
    public ResponseEntity<String> fetchOneCandidate() {
        String response = service.fetchOneCandidate();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/candidates/{candidateId}/activities")
    public ResponseEntity<String> fetchCandidateActivities(@PathVariable String candidateId) {
        String response = service.fetchCandidateActivities(candidateId);
        return ResponseEntity.ok(response);
    }

}
