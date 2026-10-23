package com.smartdroneinspection.infrastructure.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.maintenance-advisor")
public record MaintenanceScopeAdvisorProperties(
    boolean enabled, String baseUrl, String apiKey, String model, Duration timeout) {}
