package com.smartdroneinspection.assets.api;

import com.smartdroneinspection.assets.api.dto.response.ChecklistTemplateResponse;
import com.smartdroneinspection.assets.service.ChecklistTemplateService;
import com.smartdroneinspection.shared.RequestLocale;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checklist-templates")
public class ChecklistTemplateController {

  private final ChecklistTemplateService checklistTemplates;

  public ChecklistTemplateController(ChecklistTemplateService checklistTemplates) {
    this.checklistTemplates = checklistTemplates;
  }

  @GetMapping("/{templateId}")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<ChecklistTemplateResponse> get(
      @PathVariable UUID templateId, HttpServletRequest request) {
    return ApiResponse.success(
        checklistTemplates.getActiveTemplate(templateId, RequestLocale.from(request)));
  }
}
