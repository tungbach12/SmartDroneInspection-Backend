package com.smartdroneinspection.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.storage.minio")
public record MinioProperties(String endpoint, String bucket, String accessKey, String secretKey) {}
