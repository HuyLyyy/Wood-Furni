package com.woodfurni.auth.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Sends emails via the Resend HTTPS API (https://resend.com).
 * Uses Spring's built-in RestTemplate — no extra dependencies needed.
 *
 * This avoids Render's outbound SMTP restrictions (ports 465/587 blocked).
 *
 * Required env vars:
 *   RESEND_API_KEY    - Resend API key (get from https://resend.com/api-keys)
 *   RESEND_FROM_EMAIL - Verified sender email (e.g. noreply@yourdomain.com
 *                        or a Resend sandbox address like re_xxxx@resend.dev)
 */
@Slf4j
@Service
public class ResendEmailService {

    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    @Value("${resend.api-key:}")
    private String apiKey;

    @Value("${resend.from-email:}")
    private String fromEmail;

    @Value("${resend.from-name:WOODFURNI}")
    private String fromName;

    private RestTemplate restTemplate;

    @PostConstruct
    void init() {
        restTemplate = new RestTemplate();

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("==========================================");
            log.warn("RESEND_API_KEY NOT CONFIGURED — emails will NOT be sent");
            log.warn("Set RESEND_API_KEY env var on Render to enable email");
            log.warn("==========================================");
        } else {
            log.info("==========================================");
            log.info("RESEND EMAIL SERVICE: api-key set, from={}", fromEmail);
            log.info("==========================================");
        }
    }

    /**
     * Sends an HTML email via the Resend API.
     *
     * @param to      recipient email address
     * @param subject email subject
     * @param html    email body in HTML format
     * @return true if sent successfully, false otherwise
     */
    public boolean sendHtmlEmail(String to, String subject, String html) {
        if (!isConfigured()) {
            log.error("Cannot send email: RESEND_API_KEY not configured");
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> payload = Map.of(
                    "from", fromEmail,
                    "to", List.of(to),
                    "subject", subject,
                    "html", html
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    RESEND_API_URL, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object id = response.getBody().get("id");
                log.info("Email sent successfully to {} — Resend ID: {}", to, id);
                return true;
            } else {
                log.error("Resend API returned non-2xx status: {}", response.getStatusCode());
                return false;
            }
        } catch (RestClientException ex) {
            log.error("Failed to send email to {} via Resend: {}", to, ex.toString());
            return false;
        }
    }

    /**
     * @return true if Resend is configured and ready to send
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && fromEmail != null && !fromEmail.isBlank();
    }
}
