package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AssetDocumentMetadataRequest(
    @NotBlank @Size(max = 64) String documentType, @PastOrPresent LocalDate documentDate) {}
