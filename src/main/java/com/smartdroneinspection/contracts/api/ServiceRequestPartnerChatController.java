package com.smartdroneinspection.contracts.api;

import com.smartdroneinspection.contracts.api.dto.response.PartnerChatThreadResponse;
import com.smartdroneinspection.contracts.service.ServiceRequestPartnerChatService;
import com.smartdroneinspection.shared.api.ApiResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/contracts/partner-messages")
@PreAuthorize("hasAnyRole('MANAGER', 'SERVICE_MANAGER', 'PLATFORM_MANAGER', 'INSPECTOR')")
public class ServiceRequestPartnerChatController {

  private final ServiceRequestPartnerChatService chat;

  public ServiceRequestPartnerChatController(ServiceRequestPartnerChatService chat) {
    this.chat = chat;
  }

  @GetMapping("/threads")
  public ApiResponse<List<PartnerChatThreadResponse>> listThreads(
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(chat.listThreads(actorId(jwt)));
  }

  @GetMapping("/{requestId}/inspectors/{inspectorId}")
  public ApiResponse<PartnerChatThreadResponse> getThread(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @PathVariable UUID inspectorId) {
    return ApiResponse.success(chat.getThread(actorId(jwt), requestId, inspectorId));
  }

  @PostMapping(
      path = "/{requestId}/inspectors/{inspectorId}",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<PartnerChatThreadResponse> postMessage(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @PathVariable UUID inspectorId,
      @RequestParam(required = false) String body,
      @RequestPart(name = "files", required = false) List<MultipartFile> files) {
    return ApiResponse.success(chat.postMessage(actorId(jwt), requestId, inspectorId, body, files));
  }

  @PostMapping("/{requestId}/inspectors/{inspectorId}/read")
  public ApiResponse<PartnerChatThreadResponse> markMessagesRead(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @PathVariable UUID inspectorId) {
    return ApiResponse.success(chat.markMessagesRead(actorId(jwt), requestId, inspectorId));
  }

  @GetMapping("/{requestId}/inspectors/{inspectorId}/attachments/{messageId}/{attachmentId}")
  public ResponseEntity<InputStreamResource> downloadMessageAttachment(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID requestId,
      @PathVariable UUID inspectorId,
      @PathVariable UUID messageId,
      @PathVariable UUID attachmentId) {
    ServiceRequestPartnerChatService.AttachmentFile file =
        chat.openMessageAttachment(actorId(jwt), requestId, inspectorId, messageId, attachmentId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.contentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            (file.contentType().startsWith("image/")
                    ? ContentDisposition.inline()
                    : ContentDisposition.attachment())
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .contentLength(file.sizeBytes())
        .body(new InputStreamResource(file.stream()));
  }

  private static UUID actorId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
