package com.migration.transform.utils;

import org.springframework.stereotype.Component;

@Component
public class ParseUtils {

    public String normalizeLinkedin(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }



    public Integer parseSalary(String salaryStr) {
        if (isEmpty(salaryStr)) return null;
        try {
            String cleaned = salaryStr.replaceAll("[^\\d]", "");
            if (cleaned.isEmpty()) return null;
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isEmpty(String str) { return str == null || str.trim().isEmpty(); }
}
