package com.migration.notification;

import com.migration.report.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Slf4j
@Service
public class NotificationService {

    private final ReportService reportService;

    @Value("${migration.alerts.enabled:false}")
    private boolean alertsEnabled;

    @Value("${migration.alerts.slack-webhook:}")
    private String slackWebhook;

    @Value("${migration.alerts.email.to:}")
    private String emailTo;

    @Value("${migration.alerts.email.from:migration@company.com}")
    private String emailFrom;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    public NotificationService(ReportService reportService) {
        this.reportService = reportService;
    }

    public void sendBatchReport() {
        if (!alertsEnabled) return;

        Map<String, Object> summary = reportService.generateSummary();
        String message = buildMessage(summary);

        send(message);
    }

    public void sendAlert(String subject, String message) {
        if (!alertsEnabled) return;

        String full = "⚠️ *" + subject + "*\n" + message;
        send(full);
    }

    private void send(String message) {
        if (!slackWebhook.isBlank()) {
            sendSlack(message);
        }
        if (!emailTo.isBlank()) {
            sendEmail(message);
        }
    }

    private String buildMessage(Map<String, Object> summary) {
        return """
                📊 Relatório de Migração
                Total: %s
                ✅ Sucesso: %s
                ❌ Erro: %s
                ⏳ Pendente: %s
                Taxa de Sucesso: %s
                """.formatted(
                summary.get("total"),
                summary.get("sucesso"),
                summary.get("erro"),
                summary.get("pendente"),
                summary.get("taxa_sucesso")
        ).stripIndent();
    }

    private void sendSlack(String message) {
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(Map.of("text", message));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(slackWebhook))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Slack notification sent: {}", response.statusCode());
        } catch (Exception e) {
            log.warn("Failed to send Slack notification: {}", e.getMessage());
        }
    }

    private void sendEmail(String message) {
        if (mailSender == null) {
            log.warn("JavaMailSender not configured, skipping email");
            return;
        }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(emailFrom);
            mail.setTo(emailTo);
            mail.setSubject("Migração Zoho → Manatal — Relatório");
            mail.setText(message);
            mailSender.send(mail);
            log.info("Email notification sent to {}", emailTo);
        } catch (Exception e) {
            log.warn("Failed to send email notification: {}", e.getMessage());
        }
    }
}
