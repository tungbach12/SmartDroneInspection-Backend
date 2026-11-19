package com.smartdroneinspection.users.api;

import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.users.api.dto.response.AuditLogPageResponse;
import com.smartdroneinspection.users.service.AdminAuditLogService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditLogController {

  private final AdminAuditLogService auditLogs;

  public AdminAuditLogController(AdminAuditLogService auditLogs) {
    this.auditLogs = auditLogs;
  }

  @GetMapping
  public ApiResponse<AuditLogPageResponse> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String outcome,
      @RequestParam(required = false) Integer days) {
    return ApiResponse.success(auditLogs.list(page, size, action, query, outcome, days));
  }
}
