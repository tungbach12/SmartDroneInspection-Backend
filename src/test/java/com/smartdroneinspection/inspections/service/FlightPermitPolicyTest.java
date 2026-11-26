package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.FlightPermitVerificationStatus;
import com.smartdroneinspection.shared.api.AirspaceZoneInfo;
import com.smartdroneinspection.shared.api.CustomerAirspaceStatus;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FlightPermitPolicyTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant VALID_FROM = NOW.minusSeconds(3600);
  private static final Instant VALID_TO = NOW.plusSeconds(86400);

  @Test
  void validatesPdfSignatureAndComputesDigest() {
    byte[] pdf = "%PDF-1.7 permit".getBytes(StandardCharsets.US_ASCII);

    var validated = FlightPermitPolicy.validateFile(pdf, "application/pdf", "permit.pdf");

    assertThat(validated.contentType()).isEqualTo("application/pdf");
    assertThat(validated.sizeBytes()).isEqualTo(pdf.length);
    assertThat(validated.sha256()).hasSize(64);

    assertBusinessCode(
        () ->
            FlightPermitPolicy.validateFile(
                "not a pdf".getBytes(StandardCharsets.US_ASCII), "application/pdf", "permit.pdf"),
        "INVALID_PERMIT_FILE");
  }

  @Test
  void validatesPngJpegAndWebpSignatures() throws Exception {
    byte[] png = image("png");
    byte[] jpeg = image("jpg");
    byte[] webp = "RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII);

    assertThat(FlightPermitPolicy.validateFile(png, "image/png", "permit.png").contentType())
        .isEqualTo("image/png");
    assertThat(FlightPermitPolicy.validateFile(jpeg, "image/jpeg", "permit.jpg").contentType())
        .isEqualTo("image/jpeg");
    assertThat(FlightPermitPolicy.validateFile(webp, "image/webp", "permit.webp").contentType())
        .isEqualTo("image/webp");
  }

  @Test
  void derivesContentTypeFromFileNameWhenHeaderIsGeneric() throws Exception {
    byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
    byte[] png = image("png");
    byte[] jpeg = image("jpg");

    assertThat(FlightPermitPolicy.validateFile(pdf, null, "PERMIT.PDF").contentType())
        .isEqualTo("application/pdf");
    assertThat(
            FlightPermitPolicy.validateFile(png, "application/octet-stream", "permit.png")
                .contentType())
        .isEqualTo("image/png");
    assertThat(FlightPermitPolicy.validateFile(jpeg, "image/jpg", "permit.jpeg").contentType())
        .isEqualTo("image/jpeg");
  }

  @Test
  void rejectsEmptyOversizedAndUnsupportedFiles() {
    assertBusinessCode(
        () -> FlightPermitPolicy.validateFile(new byte[0], "application/pdf", "permit.pdf"),
        "EMPTY_FILE");
    byte[] oversized = new byte[(int) FlightPermitPolicy.MAX_PERMIT_SIZE_BYTES + 1];
    assertBusinessCode(
        () -> FlightPermitPolicy.validateFile(oversized, "application/pdf", "permit.pdf"),
        "PAYLOAD_TOO_LARGE");
    assertBusinessCode(
        () ->
            FlightPermitPolicy.validateFile(
                "text".getBytes(StandardCharsets.US_ASCII), "text/plain", "permit.txt"),
        "UNSUPPORTED_MEDIA_TYPE");
  }

  @Test
  void validatesPermitMetadataAndScheduleWindow() {
    Inspection inspection = inspection();

    assertBusinessCode(
        () ->
            FlightPermitPolicy.validateMetadataAndCrossChecks(
                inspection, null, " ", "CAA", VALID_FROM, VALID_TO, null, null, NOW),
        "PERMIT_NUMBER_REQUIRED");
    assertBusinessCode(
        () ->
            FlightPermitPolicy.validateMetadataAndCrossChecks(
                inspection, null, "FP-1", " ", VALID_FROM, VALID_TO, null, null, NOW),
        "PERMIT_AUTHORITY_REQUIRED");
    assertBusinessCode(
        () ->
            FlightPermitPolicy.validateMetadataAndCrossChecks(
                inspection, null, "FP-1", "CAA", null, VALID_TO, null, null, NOW),
        "PERMIT_DATES_REQUIRED");
    assertBusinessCode(
        () ->
            FlightPermitPolicy.validateMetadataAndCrossChecks(
                inspection, null, "FP-1", "CAA", VALID_TO, VALID_FROM, null, null, NOW),
        "INVALID_PERMIT_WINDOW");
    assertBusinessCode(
        () ->
            FlightPermitPolicy.validateMetadataAndCrossChecks(
                inspection, null, "FP-1", "CAA", VALID_FROM, NOW.minusSeconds(1), null, null, NOW),
        "FLIGHT_PERMIT_EXPIRED");
    assertBusinessCode(
        () ->
            FlightPermitPolicy.validateMetadataAndCrossChecks(
                inspection, null, "FP-1", "CAA", VALID_FROM, VALID_TO, null, 0, NOW),
        "INVALID_PERMIT_ALTITUDE");
    assertBusinessCode(
        () ->
            FlightPermitPolicy.validateMetadataAndCrossChecks(
                inspection, null, "FP-1", "CAA", NOW.plusSeconds(7300), VALID_TO, null, null, NOW),
        "PERMIT_SCHEDULE_MISMATCH");
    assertBusinessCode(
        () ->
            FlightPermitPolicy.validateMetadataAndCrossChecks(
                inspection, null, "FP-1", "CAA", VALID_FROM, NOW.plusSeconds(60), null, null, NOW),
        "PERMIT_SCHEDULE_MISMATCH");
  }

  @Test
  void validatesDroneSerialMatch() {
    Drone drone = mock(Drone.class);
    when(drone.getSerialNumber()).thenReturn("SN-1");

    FlightPermitPolicy.validateDroneSerialMatch(null, drone);
    FlightPermitPolicy.validateDroneSerialMatch("SN-1", drone);
    FlightPermitPolicy.validateDroneSerialMatch("sn-1", drone);

    assertBusinessCode(
        () -> FlightPermitPolicy.validateDroneSerialMatch("SN-2", drone), "PERMIT_DRONE_MISMATCH");
  }

  @Test
  void requiresAnApprovedCurrentPermitBeforeFlight() {
    Inspection inspection = inspection();
    Drone drone = mock(Drone.class);
    when(drone.getSerialNumber()).thenReturn("SN-1");

    assertBusinessCode(
        () -> FlightPermitPolicy.requireApprovedForFlight(inspection, null, NOW),
        "FLIGHT_PERMIT_REQUIRED");

    inspection.recordFlightPermit("permits/fp.pdf", "fp.pdf", NOW);
    Object permit = ReflectionTestUtils.getField(inspection, "flightPermit");
    ReflectionTestUtils.setField(
        permit, "verificationStatus", FlightPermitVerificationStatus.PENDING_REVIEW);
    assertBusinessCode(
        () -> FlightPermitPolicy.requireApprovedForFlight(inspection, null, NOW),
        "FLIGHT_PERMIT_PENDING_REVIEW");

    ReflectionTestUtils.setField(
        permit, "verificationStatus", FlightPermitVerificationStatus.REJECTED);
    assertBusinessCode(
        () -> FlightPermitPolicy.requireApprovedForFlight(inspection, null, NOW),
        "FLIGHT_PERMIT_REJECTED");

    ReflectionTestUtils.setField(
        permit, "verificationStatus", FlightPermitVerificationStatus.NOT_UPLOADED);
    assertBusinessCode(
        () -> FlightPermitPolicy.requireApprovedForFlight(inspection, null, NOW),
        "FLIGHT_PERMIT_NOT_APPROVED");

    ReflectionTestUtils.setField(
        permit, "verificationStatus", FlightPermitVerificationStatus.APPROVED);
    ReflectionTestUtils.setField(permit, "validTo", NOW.minusSeconds(1));
    assertBusinessCode(
        () -> FlightPermitPolicy.requireApprovedForFlight(inspection, null, NOW),
        "FLIGHT_PERMIT_EXPIRED");

    ReflectionTestUtils.setField(permit, "validTo", VALID_TO);
    ReflectionTestUtils.setField(permit, "validFrom", NOW.plusSeconds(3600));
    assertBusinessCode(
        () -> FlightPermitPolicy.requireApprovedForFlight(inspection, null, NOW),
        "FLIGHT_PERMIT_NOT_YET_VALID");

    ReflectionTestUtils.setField(permit, "validFrom", VALID_FROM);
    ReflectionTestUtils.setField(permit, "droneSerial", "SN-2");
    assertBusinessCode(
        () -> FlightPermitPolicy.requireApprovedForFlight(inspection, drone, NOW),
        "PERMIT_DRONE_MISMATCH");

    ReflectionTestUtils.setField(permit, "droneSerial", "SN-1");
    FlightPermitPolicy.requireApprovedForFlight(inspection, drone, NOW);
  }

  @Test
  void rejectsPermitAltitudeExceedingRestrictedAirspaceCeiling() {
    AirspaceZoneInfo restricted =
        new AirspaceZoneInfo(
            CustomerAirspaceStatus.RESTRICTED_ZONE,
            "VN-RES-SGN-TSN-CTR",
            "Vùng Kiểm Soát Tiếp Cận Tân Sơn Nhất (CTR)",
            "Cục Tác chiến & Sư đoàn Không quân 370",
            50,
            "Verify permission");
    AirspaceZoneInfo clear =
        new AirspaceZoneInfo(
            CustomerAirspaceStatus.CLEAR_ZONE, null, null, null, null, "Outside zones");

    FlightPermitPolicy.validateRestrictedAirspaceCeiling(50, restricted);
    FlightPermitPolicy.validateRestrictedAirspaceCeiling(null, clear);
    FlightPermitPolicy.validateRestrictedAirspaceCeiling(120, clear);

    assertBusinessCode(
        () -> FlightPermitPolicy.validateRestrictedAirspaceCeiling(null, restricted),
        "PERMIT_ALTITUDE_REQUIRED");
    assertBusinessCode(
        () -> FlightPermitPolicy.validateRestrictedAirspaceCeiling(80, restricted),
        "PERMIT_ALTITUDE_EXCEEDS_ZONE_LIMIT");
  }

  private static Inspection inspection() {
    Inspection inspection =
        new Inspection(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            null,
            NOW.plusSeconds(7200),
            NOW.plusSeconds(10800));
    ReflectionTestUtils.setField(inspection, "id", UUID.randomUUID());
    return inspection;
  }

  private static byte[] image(String format) throws Exception {
    BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, format, out);
    return out.toByteArray();
  }

  private static void assertBusinessCode(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
    assertThatThrownBy(callable)
        .isInstanceOfSatisfying(
            BusinessException.class, exception -> assertThat(exception.code()).isEqualTo(code));
  }
}
