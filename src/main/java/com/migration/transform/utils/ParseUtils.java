package com.migration.transform.utils;


import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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



    public List<String> parseSkills(JsonNode node) {
        JsonNode stacks = node.get("Stacks_LinkedIn");
        if (stacks != null && !stacks.isNull()) {
            List<String> list = new ArrayList<>();
            for (String line : stacks.asText().split("\\r?\\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) list.add(trimmed);
            }
            return list;
        }
        JsonNode skills = node.get("Skills");
        if (skills != null && skills.isArray()) {
            List<String> list = new ArrayList<>();
            skills.forEach(s -> list.add(s.asText()));
            return list;
        }
        return null;
    }


    public String parseDate(String dateStr) {
        if (isEmpty(dateStr)) return null;
        dateStr = dateStr.trim();

        Pattern monthYearPattern = Pattern.compile("^([A-Za-z]{3})-(\\d{4})$");
        Matcher matcher = monthYearPattern.matcher(dateStr);
        if (matcher.matches()) {
            Map<String, String> monthMap = Map.ofEntries(
                    Map.entry("Jan", "01"), Map.entry("Feb", "02"), Map.entry("Mar", "03"),
                    Map.entry("Apr", "04"), Map.entry("May", "05"), Map.entry("Jun", "06"),
                    Map.entry("Jul", "07"), Map.entry("Aug", "08"), Map.entry("Sep", "09"),
                    Map.entry("Oct", "10"), Map.entry("Nov", "11"), Map.entry("Dec", "12")
            );
            return matcher.group(2) + "-" + monthMap.getOrDefault(matcher.group(1), "01") + "-01";
        }

        String[] formats = {"dd-MM-yyyy", "yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy"};
        for (String fmt : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(fmt);
                sdf.setLenient(false);
                Date date = sdf.parse(dateStr);
                return new SimpleDateFormat("yyyy-MM-dd").format(date);
            } catch (ParseException ignored) {}
        }

        return null;
    }

    public Integer parseSalary(String salaryStr) {
        if (isEmpty(salaryStr)) return null;
        try {
            String cleaned = salaryStr.replaceAll("[^\\d.]", "").replace(",", "");
            if (cleaned.isEmpty()) return null;
            return (int) Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isEmpty(String str) { return str == null || str.trim().isEmpty(); }
}
