package com.smartdroneinspection.infrastructure.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings for an OpenAI-compatible chat endpoint used for advisory image detection. */
@ConfigurationProperties("app.ai.compatible-vision")
public record CompatibleVisionProperties(
    boolean enabled, String baseUrl, String model, String apiKey, Duration timeout) {}
