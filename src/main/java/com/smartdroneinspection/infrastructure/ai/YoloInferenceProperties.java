package com.smartdroneinspection.infrastructure.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.yolo")
public record YoloInferenceProperties(
    boolean enabled,
    String baseUrl,
    String predictPath,
    Duration connectTimeout,
    Duration readTimeout) {}
