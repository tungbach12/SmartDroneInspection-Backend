package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.response.InspectionChatThreadResponse;
import com.smartdroneinspection.inspections.service.InspectionChatService;
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
@RequestMapping("/api/v1/inspections")
public class InspectionChatController {

  private final InspectionChatService chat;

  public InspectionChatController(InspectionChatService chat) {
    this.chat = chat;
  }

  @GetMapping("/messages/threads")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER')")
  public ApiResponse<List<InspectionChatThreadResponse>> listThreads(
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(chat.listThreads(actorId(jwt)));
  }

  @GetMapping("/{inspectionId}/messages")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER')")
  public ApiResponse<InspectionChatThreadResponse> getThread(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(chat.getThread(actorId(jwt), inspectionId));
  }

  @PostMapping(path = "/{inspectionId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER')")
  public ApiResponse<InspectionChatThreadResponse> postMessage(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @RequestParam(required = false) String body,
      @RequestPart(name = "files", required = false) List<MultipartFile> files) {
    return ApiResponse.success(chat.postMessage(actorId(jwt), inspectionId, body, files));
  }

  @PostMapping("/{inspectionId}/messages/read")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER')")
  public ApiResponse<InspectionChatThreadResponse> markMessagesRead(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(chat.markMessagesRead(actorId(jwt), inspectionId));
  }

  @GetMapping("/{inspectionId}/messages/{messageId}/attachments/{attachmentId}")
  @PreAuthorize("hasAnyRole('INSPECTOR', 'CUSTOMER')")
  public ResponseEntity<InputStreamResource> downloadMessageAttachment(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectionId,
      @PathVariable UUID messageId,
      @PathVariable UUID attachmentId) {
    InspectionChatService.AttachmentFile file =
        chat.openMessageAttachment(actorId(jwt), inspectionId, messageId, attachmentId);
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
