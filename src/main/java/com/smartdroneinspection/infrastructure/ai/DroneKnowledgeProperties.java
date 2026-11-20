package com.smartdroneinspection.infrastructure.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.knowledge-ai")
public record DroneKnowledgeProperties(
    boolean enabled,
    String baseUrl,
    String apiKey,
    String embeddingBaseUrl,
    String embeddingApiKey,
    String embeddingModel,
    String chatModel,
    Duration timeout) {}
