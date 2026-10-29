package com.smartdroneinspection.contracts.api;

import com.smartdroneinspection.contracts.api.dto.request.RegisterPartnerInspectorRequest;
import com.smartdroneinspection.contracts.api.dto.request.RegisterPartnerResourceRequest;
import com.smartdroneinspection.contracts.api.dto.request.SetPartnerResourceStatusRequest;
import com.smartdroneinspection.contracts.api.dto.response.PartnerDroneResponse;
import com.smartdroneinspection.contracts.api.dto.response.PartnerInspectorResponse;
import com.smartdroneinspection.contracts.service.PartnerRegistryService;
import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.shared.auth.Roles;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contracts/partners")
@PreAuthorize("hasAnyRole('" + Roles.MANAGER + "', '" + Roles.ADMIN + "')")
public class PartnerRegistryController {

  private final PartnerRegistryService partners;

  public PartnerRegistryController(PartnerRegistryService partners) {
    this.partners = partners;
  }

  @GetMapping("/inspectors")
  public ApiResponse<List<PartnerInspectorResponse>> listInspectors(
      @AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(
        partners.listInspectors(actorId(jwt)).stream()
            .map(PartnerInspectorResponse::from)
            .toList());
  }

  @PostMapping("/inspectors")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<PartnerInspectorResponse> registerInspector(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody RegisterPartnerInspectorRequest request) {
    var inspector = partners.registerInspector(actorId(jwt), request.inspectorUserId());
    return ApiResponse.success(
        new PartnerInspectorResponse(
            inspector.getInspectorUserId(), null, inspector.getOrganizationId(), null));
  }

  @PatchMapping("/inspectors/{inspectorId}")
  public ApiResponse<Void> setInspectorActive(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID inspectorId,
      @Valid @RequestBody SetPartnerResourceStatusRequest request) {
    partners.setInspectorActive(actorId(jwt), inspectorId, request.active());
    return ApiResponse.success(null);
  }

  @GetMapping("/drones")
  public ApiResponse<List<PartnerDroneResponse>> listDrones(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(
        partners.listDrones(actorId(jwt)).stream().map(PartnerDroneResponse::from).toList());
  }

  @PostMapping("/drones")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<PartnerDroneResponse> registerDrone(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody RegisterPartnerResourceRequest request) {
    var drone = partners.registerDrone(actorId(jwt), request.droneId());
    return ApiResponse.success(
        partners.listDrones(actorId(jwt)).stream()
            .filter(option -> option.id().equals(drone.getDroneId()))
            .findFirst()
            .map(PartnerDroneResponse::from)
            .orElseThrow());
  }

  @PatchMapping("/drones/{droneId}")
  public ApiResponse<Void> setDroneActive(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID droneId,
      @Valid @RequestBody SetPartnerResourceStatusRequest request) {
    partners.setDroneActive(actorId(jwt), droneId, request.active());
    return ApiResponse.success(null);
  }

  private static UUID actorId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
