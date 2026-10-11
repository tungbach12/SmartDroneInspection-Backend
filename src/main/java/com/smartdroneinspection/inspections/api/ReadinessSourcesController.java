package com.smartdroneinspection.inspections.api;

import com.smartdroneinspection.inspections.api.dto.response.MyCredentialResponse;
import com.smartdroneinspection.inspections.api.dto.response.ReadinessSourcesResponse;
import com.smartdroneinspection.inspections.service.ReadinessSourcesService;
import com.smartdroneinspection.shared.api.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MF2-07 sources: what a reviewer must look at before deciding.
 *
 * <p>Approval requires naming a reviewer credential, the assigned Inspector's credentials and the
 * assigned Drone's documents. Those ids cannot be invented, so these routes read them. Without them
 * the only way to approve over HTTP would be to type UUIDs, which is not a workflow anyone
 * completes.
 *
 * <p>Only a same-organization ORG_ADMIN reaches these. The reviewer's own credentials are a
 * separate route because they describe the caller rather than the inspection, and folding them in
 * would force a choice: either repeat them per inspection or return one inspection's records to
 * every caller.
 */
@RestController
@RequestMapping("/api/v1")
public class ReadinessSourcesController {

  private final ReadinessSourcesService sources;

  public ReadinessSourcesController(ReadinessSourcesService sources) {
    this.sources = sources;
  }

  /** The caller's own credentials, which an approval must reference. */
  @GetMapping("/workforce/credentials/me")
  @PreAuthorize("hasRole('ORG_ADMIN')")
  public ApiResponse<List<MyCredentialResponse>> myCredentials(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(
        sources.ownCredentials(subject(jwt)).stream()
            .map(
                credential ->
                    new MyCredentialResponse(
                        credential.id(),
                        credential.credentialType(),
                        credential.issuer(),
                        credential.credentialReference(),
                        credential.issuedAt(),
                        credential.expiresAt(),
                        credential.status().name(),
                        credential.verifiedByUserId(),
                        credential.verifiedAt(),
                        credential.verificationReason()))
            .toList());
  }

  /**
   * The assigned Inspector's credentials and the assigned Drone's documents.
   *
   * <p>A cross-tenant caller receives {@code 404 INSPECTION_NOT_FOUND}, not an empty list: an empty
   * one would confirm the inspection exists.
   */
  @GetMapping("/inspections/{inspectionId}/readiness/sources")
  @PreAuthorize("hasRole('ORG_ADMIN')")
  public ApiResponse<ReadinessSourcesResponse> inspectionSources(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID inspectionId) {
    return ApiResponse.success(sources.sources(subject(jwt), inspectionId));
  }

  private UUID subject(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
