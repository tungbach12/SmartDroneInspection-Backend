package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.response.ChecklistTemplateResponse;
import com.smartdroneinspection.assets.domain.enums.ChecklistTemplateStatus;
import com.smartdroneinspection.assets.repository.ChecklistTemplateRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChecklistTemplateService {

  private final ChecklistTemplateRepository templates;

  public ChecklistTemplateService(ChecklistTemplateRepository templates) {
    this.templates = templates;
  }

  @Transactional(readOnly = true)
  public ChecklistTemplateResponse getActiveTemplate(UUID id, Locale locale) {
    var template =
        templates
            .findDetailedById(id)
            .filter(value -> value.getStatus() == ChecklistTemplateStatus.ACTIVE)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "CHECKLIST_TEMPLATE_NOT_FOUND",
                        "Checklist template was not found."));
    return ChecklistTemplateResponse.from(template, locale);
  }
}
