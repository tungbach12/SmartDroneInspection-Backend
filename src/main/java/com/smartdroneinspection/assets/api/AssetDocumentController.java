package com.smartdroneinspection.assets.api;

import com.smartdroneinspection.assets.api.dto.response.AssetDocumentResponse;
import com.smartdroneinspection.assets.service.AssetDocumentService;
import com.smartdroneinspection.shared.api.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/assets/{assetId}/documents")
public class AssetDocumentController {

  private final AssetDocumentService documents;

  public AssetDocumentController(AssetDocumentService documents) {
    this.documents = documents;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
  public ApiResponse<AssetDocumentResponse> upload(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID assetId,
      @RequestParam("file") MultipartFile file,
      @RequestParam String documentType,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate documentDate) {
    return ApiResponse.success(
        documents.upload(
            UUID.fromString(jwt.getSubject()), assetId, file, documentType, documentDate));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN', 'SERVICE_MANAGER')")
  public ApiResponse<List<AssetDocumentResponse>> list(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID assetId) {
    return ApiResponse.success(documents.list(UUID.fromString(jwt.getSubject()), assetId));
  }

  @GetMapping("/{documentId}/content")
  @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN', 'SERVICE_MANAGER')")
  public ResponseEntity<InputStreamResource> content(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID assetId, @PathVariable UUID documentId) {
    AssetDocumentService.DocumentContent content =
        documents.open(UUID.fromString(jwt.getSubject()), assetId, documentId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(content.contentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(content.fileName()).build().toString())
        .body(new InputStreamResource(content.stream()));
  }
}
