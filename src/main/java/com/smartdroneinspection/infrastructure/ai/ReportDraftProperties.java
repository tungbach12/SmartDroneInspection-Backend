package com.smartdroneinspection.infrastructure.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.report-draft")
public record ReportDraftProperties(
    boolean enabled, String baseUrl, String apiKey, String model, Duration timeout) {}
