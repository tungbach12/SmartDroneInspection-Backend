package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.FlightPermitVerificationStatus;
import com.smartdroneinspection.shared.api.AirspaceZoneInfo;
import com.smartdroneinspection.shared.api.CustomerAirspaceStatus;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;

/** Shared validation and readiness rules for regulatory flight permits. */
final class FlightPermitPolicy {

  static final long MAX_PERMIT_SIZE_BYTES = 25L * 1024 * 1024;
  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("application/pdf", "image/png", "image/jpeg", "image/webp");

  private FlightPermitPolicy() {}

  record ValidatedPermitFile(String contentType, String sha256, long sizeBytes) {}

  static ValidatedPermitFile validateFile(byte[] content, String rawContentType, String fileName) {
    if (content == null || content.length == 0) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Flight permit file must not be empty.");
    }
    if (content.length > MAX_PERMIT_SIZE_BYTES) {
      throw new BusinessException(
          HttpStatus.PAYLOAD_TOO_LARGE,
          "PAYLOAD_TOO_LARGE",
          "Flight permit file must not exceed 25 MB.");
    }
    String normalizedType = resolveContentType(rawContentType, fileName);
    if (!ALLOWED_CONTENT_TYPES.contains(normalizedType)) {
      throw new BusinessException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "UNSUPPORTED_MEDIA_TYPE",
          "Flight permit must be a PDF, PNG, JPG, or WEBP file.");
    }
    verifyFileSignature(content, normalizedType);
    return new ValidatedPermitFile(normalizedType, sha256Hex(content), content.length);
  }

  static void validateMetadataAndCrossChecks(
      Inspection inspection,
      Drone assignedDrone,
      String permitNumber,
      String issuingAuthority,
      Instant validFrom,
      Instant validTo,
      String droneSerial,
      Integer maxAltitudeM,
      Instant now) {
    if (permitNumber == null || permitNumber.isBlank()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "PERMIT_NUMBER_REQUIRED", "Flight permit number is required.");
    }
    if (issuingAuthority == null || issuingAuthority.isBlank()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PERMIT_AUTHORITY_REQUIRED",
          "Issuing aviation authority is required.");
    }
    if (validFrom == null || validTo == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PERMIT_DATES_REQUIRED",
          "Flight permit effective start and expiration timestamps are required.");
    }
    if (!validTo.isAfter(validFrom)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_PERMIT_WINDOW",
          "Flight permit expiration must be after its effective start time.");
    }
    if (!validTo.isAfter(now)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "FLIGHT_PERMIT_EXPIRED", "Flight permit has already expired.");
    }
    if (maxAltitudeM != null && (maxAltitudeM <= 0 || maxAltitudeM > 2000)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_PERMIT_ALTITUDE",
          "Permitted max altitude must be between 1 and 2000 meters.");
    }
    if (inspection.getPlannedStartAt() != null
        && validFrom.isAfter(inspection.getPlannedStartAt())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PERMIT_SCHEDULE_MISMATCH",
          "Flight permit effective start must cover the scheduled inspection start time.");
    }
    if (inspection.getPlannedEndAt() != null && validTo.isBefore(inspection.getPlannedEndAt())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PERMIT_SCHEDULE_MISMATCH",
          "Flight permit expiration must cover the scheduled inspection end time.");
    }
    validateDroneSerialMatch(droneSerial, assignedDrone);
  }

  static void validateRestrictedAirspaceCeiling(Integer maxAltitudeM, AirspaceZoneInfo zoneInfo) {
    if (zoneInfo == null || zoneInfo.status() != CustomerAirspaceStatus.RESTRICTED_ZONE) {
      return;
    }
    Integer zoneCeiling = zoneInfo.maxAltitudeMeters();
    if (maxAltitudeM != null
        && zoneCeiling != null
        && zoneCeiling > 0
        && maxAltitudeM > zoneCeiling) {
      String zoneLabel =
          zoneInfo.zoneName() != null && !zoneInfo.zoneName().isBlank()
              ? zoneInfo.zoneName()
              : "restricted airspace";
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PERMIT_ALTITUDE_EXCEEDS_ZONE_LIMIT",
          "Permitted max altitude ("
              + maxAltitudeM
              + " m) exceeds the restricted airspace ceiling ("
              + zoneCeiling
              + " m AGL) for "
              + zoneLabel
              + ".");
    }
  }

  static void validateDroneSerialMatch(String permitDroneSerial, Drone assignedDrone) {
    if (permitDroneSerial == null
        || permitDroneSerial.isBlank()
        || assignedDrone == null
        || assignedDrone.getSerialNumber() == null
        || assignedDrone.getSerialNumber().isBlank()) {
      return;
    }
    if (!permitDroneSerial.trim().equalsIgnoreCase(assignedDrone.getSerialNumber().trim())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "PERMIT_DRONE_MISMATCH",
          "Flight permit drone serial number does not match the drone assigned to this inspection.");
    }
  }

  static void requireApprovedForFlight(Inspection inspection, Drone assignedDrone, Instant now) {
    if (!inspection.hasFlightPermit()) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FLIGHT_PERMIT_REQUIRED",
          "The assigned inspector must upload a valid flight permit before starting the field session.");
    }
    FlightPermitVerificationStatus verificationStatus =
        inspection.getFlightPermitVerificationStatus();
    if (verificationStatus == FlightPermitVerificationStatus.PENDING_REVIEW) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FLIGHT_PERMIT_PENDING_REVIEW",
          "The flight permit no longer matches the assigned drone or schedule. The assigned inspector must upload an updated permit.");
    }
    if (verificationStatus == FlightPermitVerificationStatus.REJECTED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FLIGHT_PERMIT_REJECTED",
          "The flight permit is no longer valid. The assigned inspector must upload a corrected permit.");
    }
    if (verificationStatus != FlightPermitVerificationStatus.APPROVED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FLIGHT_PERMIT_NOT_APPROVED",
          "A valid flight permit uploaded by the assigned inspector is required before field work can start.");
    }
    if (inspection.getFlightPermitValidTo() != null
        && !inspection.getFlightPermitValidTo().isAfter(now)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "FLIGHT_PERMIT_EXPIRED",
          "Cannot start field work because the flight permit has expired.");
    }
    if (inspection.getFlightPermitValidFrom() != null
        && now.isBefore(inspection.getFlightPermitValidFrom())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "FLIGHT_PERMIT_NOT_YET_VALID",
          "Cannot start field work before the flight permit effective start time.");
    }
    validateDroneSerialMatch(inspection.getFlightPermitDroneSerial(), assignedDrone);
  }

  static String resolveContentType(String rawContentType, String fileName) {
    String normalized =
        rawContentType == null
            ? ""
            : rawContentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    if ("image/jpg".equals(normalized)) {
      normalized = "image/jpeg";
    }
    if (!normalized.isBlank() && !"application/octet-stream".equals(normalized)) {
      return normalized;
    }
    String lowerName = fileName == null ? "" : fileName.trim().toLowerCase(Locale.ROOT);
    if (lowerName.endsWith(".pdf")) return "application/pdf";
    if (lowerName.endsWith(".png")) return "image/png";
    if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) return "image/jpeg";
    if (lowerName.endsWith(".webp")) return "image/webp";
    return normalized;
  }

  private static void verifyFileSignature(byte[] content, String contentType) {
    switch (contentType) {
      case "application/pdf" -> {
        String header =
            new String(content, 0, Math.min(content.length, 1024), StandardCharsets.US_ASCII);
        if (!header.contains("%PDF-")) {
          throw new BusinessException(
              HttpStatus.BAD_REQUEST,
              "INVALID_PERMIT_FILE",
              "The uploaded file is not a valid PDF document.");
        }
      }
      case "image/png" -> {
        if (!hasPngHeader(content) || !isDecodableImage(content)) {
          throw new BusinessException(
              HttpStatus.BAD_REQUEST,
              "INVALID_PERMIT_FILE",
              "The uploaded file is not a readable PNG image.");
        }
      }
      case "image/jpeg" -> {
        if (!hasJpegHeader(content) || !isDecodableImage(content)) {
          throw new BusinessException(
              HttpStatus.BAD_REQUEST,
              "INVALID_PERMIT_FILE",
              "The uploaded file is not a readable JPEG image.");
        }
      }
      case "image/webp" -> {
        if (!hasWebpHeader(content)) {
          throw new BusinessException(
              HttpStatus.BAD_REQUEST,
              "INVALID_PERMIT_FILE",
              "The uploaded file is not a valid WEBP image.");
        }
      }
      default ->
          throw new BusinessException(
              HttpStatus.UNSUPPORTED_MEDIA_TYPE,
              "UNSUPPORTED_MEDIA_TYPE",
              "Flight permit must be a PDF, PNG, JPG, or WEBP file.");
    }
  }

  private static boolean hasPngHeader(byte[] bytes) {
    return bytes.length >= 8
        && (bytes[0] & 0xFF) == 0x89
        && bytes[1] == 0x50
        && bytes[2] == 0x4E
        && bytes[3] == 0x47
        && bytes[4] == 0x0D
        && bytes[5] == 0x0A
        && bytes[6] == 0x1A
        && bytes[7] == 0x0A;
  }

  private static boolean hasJpegHeader(byte[] bytes) {
    return bytes.length >= 3
        && (bytes[0] & 0xFF) == 0xFF
        && (bytes[1] & 0xFF) == 0xD8
        && (bytes[2] & 0xFF) == 0xFF;
  }

  private static boolean hasWebpHeader(byte[] bytes) {
    return bytes.length >= 12
        && bytes[0] == 'R'
        && bytes[1] == 'I'
        && bytes[2] == 'F'
        && bytes[3] == 'F'
        && bytes[8] == 'W'
        && bytes[9] == 'E'
        && bytes[10] == 'B'
        && bytes[11] == 'P';
  }

  private static boolean isDecodableImage(byte[] bytes) {
    try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
      return ImageIO.read(input) != null;
    } catch (IOException ex) {
      return false;
    }
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
    }
  }
}
