package com.smartdroneinspection.assets.api;

import com.smartdroneinspection.assets.api.dto.response.DronePresetResponse;
import com.smartdroneinspection.assets.api.dto.response.DroneSpecExtractionResponse;
import com.smartdroneinspection.assets.service.DroneSpecExtractionService;
import com.smartdroneinspection.shared.api.ApiResponse;
import com.smartdroneinspection.shared.auth.Roles;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/drone-registration-applications")
public class DroneSpecExtractionController {

  private final DroneSpecExtractionService extraction;

  public DroneSpecExtractionController(DroneSpecExtractionService extraction) {
    this.extraction = extraction;
  }

  @GetMapping("/presets")
  @PreAuthorize("hasRole('" + Roles.INSPECTOR + "')")
  public ApiResponse<List<DronePresetResponse>> presets(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(extraction.listPresets(UUID.fromString(jwt.getSubject())));
  }

  @PostMapping(path = "/extract-specs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('" + Roles.INSPECTOR + "')")
  public ApiResponse<DroneSpecExtractionResponse> extractSpecs(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "ocrText", required = false) String ocrText) {
    return ApiResponse.success(
        extraction.extract(UUID.fromString(jwt.getSubject()), file, ocrText));
  }
}
