package com.smartdroneinspection.assets.api.dto.response;

import com.smartdroneinspection.assets.domain.ChecklistTemplate;
import com.smartdroneinspection.assets.domain.enums.ChecklistResponseType;
import com.smartdroneinspection.assets.domain.enums.ChecklistTemplateStatus;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** System-managed checklist text localized at the HTTP response boundary. */
public record ChecklistTemplateResponse(
    UUID id,
    String templateKey,
    int versionNumber,
    String name,
    String description,
    ChecklistTemplateStatus status,
    Instant publishedAt,
    List<Item> items) {

  public static ChecklistTemplateResponse from(ChecklistTemplate template, Locale locale) {
    return new ChecklistTemplateResponse(
        template.getId(),
        template.getTemplateKey(),
        template.getVersionNumber(),
        template.localizedName(locale),
        template.localizedDescription(locale),
        template.getStatus(),
        template.getPublishedAt(),
        template.getItems().stream().map(item -> Item.from(item, locale)).toList());
  }

  public record Item(
      UUID id,
      String itemCode,
      String sectionName,
      String prompt,
      ChecklistResponseType responseType,
      boolean required,
      int displayOrder,
      String guidance,
      String validationConfig) {

    private static Item from(
        com.smartdroneinspection.assets.domain.ChecklistItem item, Locale locale) {
      return new Item(
          item.getId(),
          item.getItemCode(),
          item.localizedSectionName(locale),
          item.localizedPrompt(locale),
          item.getResponseType(),
          item.isRequired(),
          item.getDisplayOrder(),
          item.localizedGuidance(locale),
          item.getValidationConfig());
    }
  }
}
