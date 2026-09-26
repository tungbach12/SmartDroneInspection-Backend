package com.smartdroneinspection.assets.api.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AssetDocumentResponse(
    UUID id,
    String documentType,
    String fileName,
    String contentType,
    long sizeBytes,
    String checksumSha256,
    LocalDate documentDate,
    Instant createdAt) {}
