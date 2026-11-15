package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.response.MediaTicketResponse;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Issues and validates short-lived, inspection/evidence-scoped HMAC tickets for browser {@code
 * <video>} Range playback so login/refresh JWTs are never placed in URLs.
 */
@Service
@EnableConfigurationProperties(EvidenceUploadProperties.class)
public class MediaTicketService {

  private static final Logger LOG = LoggerFactory.getLogger(MediaTicketService.class);
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final Base64.Encoder B64_URL_ENC = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder B64_URL_DEC = Base64.getUrlDecoder();

  private final EvidenceUploadProperties properties;
  private final SecretKeySpec secretKey;

  public MediaTicketService(
      EvidenceUploadProperties properties, @Value("${app.auth.jwt-secret:}") String jwtSecret) {
    this.properties = properties;
    this.secretKey = deriveKey(properties.getMediaTicketSecret(), jwtSecret);
  }

  public MediaTicketResponse issueTicket(
      UUID actorId, UUID inspectionId, UUID evidenceId, String rawVariant) {
    String variant = normalizeVariant(rawVariant);
    Instant expiresAt = Instant.now().plus(properties.getMediaTicketTtl());
    String payload =
        inspectionId
            + "|"
            + evidenceId
            + "|"
            + actorId
            + "|"
            + variant
            + "|"
            + expiresAt.getEpochSecond();
    String encodedPayload = B64_URL_ENC.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    String signature = B64_URL_ENC.encodeToString(sign(encodedPayload));
    String token = encodedPayload + "." + signature;
    String streamUrl =
        "/api/v1/inspections/"
            + inspectionId
            + "/evidence/"
            + evidenceId
            + "/stream?ticket="
            + token;
    return new MediaTicketResponse(token, streamUrl, expiresAt);
  }

  public VerifiedTicket verifyTicket(
      String token, UUID expectedInspectionId, UUID expectedEvidenceId) {
    if (token == null || token.isBlank()) {
      throw unauthorized("A media playback ticket is required");
    }
    int dot = token.indexOf('.');
    if (dot <= 0 || dot == token.length() - 1) {
      throw unauthorized("Invalid media playback ticket");
    }
    String encodedPayload = token.substring(0, dot);
    String encodedSignature = token.substring(dot + 1);

    byte[] actualSig;
    try {
      actualSig = B64_URL_DEC.decode(encodedSignature);
    } catch (IllegalArgumentException ex) {
      throw unauthorized("Invalid media playback ticket");
    }
    byte[] expectedSig = sign(encodedPayload);
    if (!MessageDigest.isEqual(actualSig, expectedSig)) {
      throw unauthorized("Invalid media playback ticket");
    }

    String payload;
    try {
      payload = new String(B64_URL_DEC.decode(encodedPayload), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException ex) {
      throw unauthorized("Invalid media playback ticket");
    }
    String[] parts = payload.split("\\|", -1);
    if (parts.length != 5) {
      throw unauthorized("Invalid media playback ticket");
    }
    UUID inspectionId;
    UUID evidenceId;
    UUID actorId;
    Instant expiresAt;
    try {
      inspectionId = UUID.fromString(parts[0]);
      evidenceId = UUID.fromString(parts[1]);
      actorId = UUID.fromString(parts[2]);
      expiresAt = Instant.ofEpochSecond(Long.parseLong(parts[4]));
    } catch (RuntimeException ex) {
      throw unauthorized("Invalid media playback ticket");
    }
    if (!inspectionId.equals(expectedInspectionId) || !evidenceId.equals(expectedEvidenceId)) {
      throw unauthorized("Media ticket scope mismatch");
    }
    if (Instant.now().isAfter(expiresAt)) {
      throw unauthorized("Media playback ticket expired");
    }
    return new VerifiedTicket(
        actorId, inspectionId, evidenceId, normalizeVariant(parts[3]), expiresAt);
  }

  public static String normalizeVariant(String rawVariant) {
    if (rawVariant == null || rawVariant.isBlank()) {
      return "primary";
    }
    String normalized = rawVariant.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "primary", "thumb" -> normalized;
      default ->
          throw new BusinessException(
              HttpStatus.BAD_REQUEST,
              "EVIDENCE_INVALID",
              "Unsupported media variant: " + rawVariant);
    };
  }

  private byte[] sign(String encodedPayload) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(secretKey);
      return mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Failed to sign media ticket", ex);
    }
  }

  private static SecretKeySpec deriveKey(String explicitSecret, String jwtSecret) {
    String source =
        explicitSecret != null && !explicitSecret.isBlank()
            ? explicitSecret
            : (jwtSecret != null && !jwtSecret.isBlank() ? jwtSecret : null);
    if (source == null) {
      LOG.warn("EVIDENCE_MEDIA_TICKET_SECRET is not configured; using ephemeral key");
      byte[] random = new byte[32];
      new SecureRandom().nextBytes(random);
      return new SecretKeySpec(random, HMAC_ALGORITHM);
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] keyBytes =
          digest.digest(("sdi-media-ticket-v1:" + source).getBytes(StandardCharsets.UTF_8));
      return new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  private static BusinessException unauthorized(String message) {
    return new BusinessException(HttpStatus.UNAUTHORIZED, "MEDIA_TICKET_INVALID", message);
  }

  public record VerifiedTicket(
      UUID actorId, UUID inspectionId, UUID evidenceId, String variant, Instant expiresAt) {}
}
