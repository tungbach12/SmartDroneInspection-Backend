package com.smartdroneinspection.inspections.api.dto.response;

public record RatingCriterionDefinitionResponse(
    String code,
    String raterRole,
    String labelEn,
    String labelVi,
    String descriptionEn,
    String descriptionVi,
    int displayOrder,
    boolean active) {}
