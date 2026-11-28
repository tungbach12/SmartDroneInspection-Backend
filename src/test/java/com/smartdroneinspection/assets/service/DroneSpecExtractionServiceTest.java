package com.smartdroneinspection.assets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.shared.api.DroneSpecVisionPort;
import com.smartdroneinspection.shared.api.DroneSpecVisionPort.ExtractedSpecs;
import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DroneSpecExtractionServiceTest {

  private static final UUID INSPECTOR_ID = UUID.randomUUID();

  @Test
  void extractsTextAndEnrichesCatalogPresetFromPdfWithoutVisionAi() throws Exception {
    UserAccess users = activeInspector();
    DroneSpecExtractionService service = new DroneSpecExtractionService(users, Optional.empty());

    byte[] pdfBytes =
        buildPdf(
            "Drone Model: DJI Matrice 300 RTK",
            "Manufacturer: DJI",
            "Serial Number: 1581F4B0C2301001",
            "Registration No: DK-2026-00892",
            "Valid until: 31/12/2027",
            "MTOW: 6.3 kg",
            "Max Flight Time: 55 min",
            "Optical Zoom: 23x");
    MockMultipartFile pdf =
        new MockMultipartFile("file", "uav-registration.pdf", "application/pdf", pdfBytes);

    var result = service.extract(INSPECTOR_ID, pdf);

    assertThat(result.visionEnabled()).isFalse();
    assertThat(result.extractionSource()).isEqualTo("PDF_TEXT_AND_PRESET");
    assertThat(result.model()).isEqualTo("DJI Matrice 300 RTK");
    assertThat(result.manufacturer()).isEqualTo("DJI");
    assertThat(result.serialNumber()).isEqualTo("1581F4B0C2301001");
    assertThat(result.registrationNumber()).isEqualTo("DK-2026-00892");
    assertThat(result.registrationExpiresAt()).isEqualTo(LocalDate.of(2027, 12, 31));
    assertThat(result.mtowKg()).isEqualByComparingTo("6.3");
    assertThat(result.maxFlightTimeMinutes()).isEqualTo(55);
    assertThat(result.opticalZoomLevel()).isEqualTo("23");
    assertThat(result.features()).contains("RTK_POSITIONING", "THERMAL_CAMERA");
    assertThat(result.suggestedFields())
        .contains("droneName", "serialNumber", "registrationNumber", "registrationExpiresAt");
  }

  @Test
  void mergesVisionOutputWithCatalogAndNeverFakesSerialOrExpiry() throws Exception {
    UserAccess users = activeInspector();
    DroneSpecVisionPort vision = mock(DroneSpecVisionPort.class);
    when(vision.extract(any(), anyString(), anyString()))
        .thenReturn(
            new ExtractedSpecs(
                "DJI Mavic 3 Thermal",
                "DJI",
                "1581X999ABC123",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("THERMAL_CAMERA")));
    DroneSpecExtractionService service = new DroneSpecExtractionService(users, Optional.of(vision));

    byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0};
    MockMultipartFile image = new MockMultipartFile("file", "spec.png", "image/png", png);

    var result = service.extract(INSPECTOR_ID, image);

    assertThat(result.visionEnabled()).isTrue();
    assertThat(result.extractionSource()).isEqualTo("VISION_AND_PRESET");
    assertThat(result.serialNumber()).isEqualTo("1581X999ABC123");
    assertThat(result.registrationNumber()).isNull();
    assertThat(result.registrationExpiresAt()).isNull();
    assertThat(result.mtowKg()).isEqualByComparingTo("0.920");
    assertThat(result.maxFlightTimeMinutes()).isEqualTo(45);
    assertThat(result.features()).contains("THERMAL_CAMERA", "RTK_POSITIONING");
  }

  @Test
  void degradesGracefullyWhenVisionThrowsAndExplainsMissingVisionOnImage() throws Exception {
    UserAccess users = activeInspector();
    DroneSpecVisionPort failingVision = mock(DroneSpecVisionPort.class);
    when(failingVision.extract(any(), anyString(), anyString()))
        .thenThrow(new IOException("timeout"));
    DroneSpecExtractionService withFailingVision =
        new DroneSpecExtractionService(users, Optional.of(failingVision));

    byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0};
    MockMultipartFile image =
        new MockMultipartFile("file", "dji-mini-4-pro-box.png", "image/png", png);

    var result = withFailingVision.extract(INSPECTOR_ID, image);

    assertThat(result.extractionSource()).isEqualTo("FILENAME_PRESET");
    assertThat(result.model()).isEqualTo("DJI Mini 4 Pro");
    assertThat(result.mtowKg()).isEqualByComparingTo("0.249");
    assertThat(result.notices()).anyMatch(n -> n.contains("fell back"));

    DroneSpecExtractionService withoutVision =
        new DroneSpecExtractionService(users, Optional.empty());
    MockMultipartFile unknownImage = new MockMultipartFile("file", "scan001.png", "image/png", png);
    var noVisionResult = withoutVision.extract(INSPECTOR_ID, unknownImage);
    assertThat(noVisionResult.extractionSource()).isEqualTo("NONE");
    assertThat(noVisionResult.notices()).anyMatch(n -> n.contains("COMPATIBLE_VISION_ENABLED"));

    var ocrResult =
        withoutVision.extract(
            INSPECTOR_ID,
            unknownImage,
            """
            Drone Model: DJI Matrice 350 RTK
            Serial Number: 1581F4B0C2301999
            Registration Number: DK-2026-888
            Expiry Date: 2027-12-31
            """);
    assertThat(ocrResult.extractionSource()).isEqualTo("OCR_TEXT_AND_PRESET");
    assertThat(ocrResult.model()).isEqualTo("DJI Matrice 350 RTK");
    assertThat(ocrResult.serialNumber()).isEqualTo("1581F4B0C2301999");
    assertThat(ocrResult.registrationNumber()).isEqualTo("DK-2026-888");
    assertThat(ocrResult.mtowKg()).isEqualByComparingTo("9.20");
  }

  @Test
  void rejectsNonInspectorsAndInvalidMediaTypes() {
    UserAccess users = mock(UserAccess.class);
    when(users.findActiveUser(INSPECTOR_ID))
        .thenReturn(Optional.of(new ActiveUser(INSPECTOR_ID, Set.of("CUSTOMER"), "Cust")));
    DroneSpecExtractionService service = new DroneSpecExtractionService(users, Optional.empty());

    MockMultipartFile badFile =
        new MockMultipartFile(
            "file", "bad.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(() -> service.extract(INSPECTOR_ID, badFile))
        .isInstanceOf(AuthException.class);

    when(users.findActiveUser(INSPECTOR_ID))
        .thenReturn(Optional.of(new ActiveUser(INSPECTOR_ID, Set.of("INSPECTOR"), "Pilot")));
    assertThatThrownBy(() -> service.extract(INSPECTOR_ID, badFile))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void listsCatalogPresetsForInspectors() {
    DroneSpecExtractionService service =
        new DroneSpecExtractionService(activeInspector(), Optional.empty());
    var presets = service.listPresets(INSPECTOR_ID);
    assertThat(presets).hasSizeGreaterThanOrEqualTo(6);
    assertThat(presets).anyMatch(p -> p.model().equals("DJI Matrice 350 RTK"));
  }

  private static UserAccess activeInspector() {
    UserAccess users = mock(UserAccess.class);
    when(users.findActiveUser(INSPECTOR_ID))
        .thenReturn(Optional.of(new ActiveUser(INSPECTOR_ID, Set.of("INSPECTOR"), "Pilot")));
    return users;
  }

  private static byte[] buildPdf(String... lines) throws IOException {
    try (PDDocument doc = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      doc.addPage(page);
      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, 12);
        cs.newLineAtOffset(50, 720);
        for (String line : lines) {
          cs.showText(line);
          cs.newLineAtOffset(0, -18);
        }
        cs.endText();
      }
      doc.save(out);
      return out.toByteArray();
    }
  }
}
