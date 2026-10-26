package com.smartdroneinspection.assets.api;

import com.smartdroneinspection.assets.api.dto.request.UpdateDroneDocumentStatusRequest;
import com.smartdroneinspection.assets.api.dto.response.DroneDocumentResponse;
import com.smartdroneinspection.assets.service.DroneDocumentService;
import com.smartdroneinspection.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/drones/{droneId}/documents")
public class DroneDocumentController {

  private final DroneDocumentService documents;

  public DroneDocumentController(DroneDocumentService documents) {
    this.documents = documents;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('ORG_MANAGER')")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<DroneDocumentResponse> upload(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID droneId,
      @RequestParam("file") MultipartFile file,
      @RequestParam String documentType,
      @RequestParam(required = false) String issuer,
      @RequestParam(required = false) String documentReference,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant validFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant validUntil) {
    return ApiResponse.success(
        documents.upload(
            UUID.fromString(jwt.getSubject()),
            droneId,
            file,
            documentType,
            issuer,
            documentReference,
            validFrom,
            validUntil));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MANAGER', 'ADMIN', 'PLATFORM_MANAGER')")
  public ApiResponse<List<DroneDocumentResponse>> list(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID droneId) {
    return ApiResponse.success(documents.list(UUID.fromString(jwt.getSubject()), droneId));
  }

  @PatchMapping("/{documentId}/status")
  @PreAuthorize("hasRole('ORG_MANAGER')")
  public ApiResponse<DroneDocumentResponse> updateStatus(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID droneId,
      @PathVariable UUID documentId,
      @Valid @RequestBody UpdateDroneDocumentStatusRequest request) {
    return ApiResponse.success(
        documents.updateStatus(UUID.fromString(jwt.getSubject()), droneId, documentId, request));
  }
}
