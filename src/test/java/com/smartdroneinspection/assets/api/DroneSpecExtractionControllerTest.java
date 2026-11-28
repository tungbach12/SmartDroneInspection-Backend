package com.smartdroneinspection.assets.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.api.dto.response.DronePresetResponse;
import com.smartdroneinspection.assets.api.dto.response.DroneSpecExtractionResponse;
import com.smartdroneinspection.assets.service.DroneSpecExtractionService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;

class DroneSpecExtractionControllerTest {

  private static final UUID INSPECTOR_ID = UUID.randomUUID();

  @Test
  void returnsPresetsAndExtractedSpecsForInspector() {
    DroneSpecExtractionService service = mock(DroneSpecExtractionService.class);
    DroneSpecExtractionController controller = new DroneSpecExtractionController(service);
    Jwt jwt = mock(Jwt.class);
    when(jwt.getSubject()).thenReturn(INSPECTOR_ID.toString());

    DronePresetResponse preset =
        new DronePresetResponse(
            "dji-m300-rtk",
            "DJI Matrice 300 RTK",
            "DJI",
            new BigDecimal("6.30"),
            55,
            new BigDecimal("15.0"),
            new BigDecimal("7000"),
            "23",
            "Zenmuse H20T",
            List.of("RTK_POSITIONING"));
    when(service.listPresets(INSPECTOR_ID)).thenReturn(List.of(preset));

    MockMultipartFile file =
        new MockMultipartFile("file", "spec.png", "image/png", new byte[] {1, 2, 3});
    DroneSpecExtractionResponse extracted =
        new DroneSpecExtractionResponse(
            "DJI Matrice 300 RTK",
            "DJI",
            "1581F",
            null,
            null,
            new BigDecimal("6.30"),
            55,
            new BigDecimal("15.0"),
            new BigDecimal("7000"),
            "23",
            "Zenmuse H20T",
            List.of("RTK_POSITIONING"),
            true,
            "VISION_AND_PRESET",
            "DJI Matrice 300 RTK",
            List.of("droneName"),
            List.of());
    when(service.extract(INSPECTOR_ID, file, null)).thenReturn(extracted);

    assertThat(controller.presets(jwt).data()).containsExactly(preset);
    assertThat(controller.extractSpecs(jwt, file, null).data()).isSameAs(extracted);
  }
}
