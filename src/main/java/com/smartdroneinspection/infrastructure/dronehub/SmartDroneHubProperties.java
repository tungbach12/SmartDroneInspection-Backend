package com.smartdroneinspection.infrastructure.dronehub;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.smartdronehub")
public record SmartDroneHubProperties(
    String baseUrl, Duration connectTimeout, Duration readTimeout) {}
