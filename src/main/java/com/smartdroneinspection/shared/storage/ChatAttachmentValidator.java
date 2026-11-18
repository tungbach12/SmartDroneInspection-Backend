package com.smartdroneinspection.shared.storage;

import com.smartdroneinspection.shared.exception.BusinessException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

/** Shared validation and SHA-256 hashing for PNG/JPG/WEBP/PDF/DOCX chat attachments. */
public final class ChatAttachmentValidator {

  public static final int MAX_ATTACHMENT_COUNT = 5;
  public static final long MAX_ATTACHMENT_FILE_BYTES = 10L * 1024L * 1024L;
  public static final long MAX_ATTACHMENT_TOTAL_BYTES = 20L * 1024L * 1024L;
  public static final String DOCX_CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

  private ChatAttachmentValidator() {}

  public record ValidatedAttachment(
      String fileName, String contentType, byte[] content, String sha256) {}

  public static List<ValidatedAttachment> validate(List<MultipartFile> files) {
    if (files == null || files.isEmpty()) {
      return List.of();
    }
    if (files.size() > MAX_ATTACHMENT_COUNT) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "MESSAGE_ATTACHMENT_COUNT",
          "A message can include at most 5 files");
    }
    List<ValidatedAttachment> validated = new ArrayList<>();
    long totalBytes = 0;
    for (MultipartFile file : files) {
      if (file == null || file.isEmpty()) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST, "MESSAGE_ATTACHMENT_INVALID", "Attachments must not be empty");
      }
      if (file.getSize() > MAX_ATTACHMENT_FILE_BYTES) {
        throw new BusinessException(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "MESSAGE_ATTACHMENT_TOO_LARGE",
            "Each attachment is limited to 10 MiB");
      }
      totalBytes += file.getSize();
      if (totalBytes > MAX_ATTACHMENT_TOTAL_BYTES) {
        throw new BusinessException(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "MESSAGE_ATTACHMENTS_TOO_LARGE",
            "Attachments are limited to 20 MiB total");
      }
      byte[] content;
      try {
        content = file.getBytes();
      } catch (IOException exception) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST, "MESSAGE_ATTACHMENT_INVALID", "Attachment could not be read");
      }
      String contentType = resolveContentType(content, file.getContentType());
      validated.add(
          new ValidatedAttachment(
              sanitizeFileName(file.getOriginalFilename()), contentType, content, sha256(content)));
    }
    return List.copyOf(validated);
  }

  public static String resolveContentType(byte[] bytes, String declaredType) {
    String declared = declaredType == null ? "" : declaredType.toLowerCase(Locale.ROOT);
    if (startsWith(bytes, new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})
        && "image/png".equals(declared)) {
      return "image/png";
    }
    if (bytes.length >= 3
        && bytes[0] == (byte) 0xff
        && bytes[1] == (byte) 0xd8
        && bytes[2] == (byte) 0xff
        && "image/jpeg".equals(declared)) {
      return "image/jpeg";
    }
    if (bytes.length >= 12
        && asciiAt(bytes, 0, "RIFF")
        && asciiAt(bytes, 8, "WEBP")
        && "image/webp".equals(declared)) {
      return "image/webp";
    }
    if (startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))
        && "application/pdf".equals(declared)) {
      return "application/pdf";
    }
    if (bytes.length >= 4
        && bytes[0] == 'P'
        && bytes[1] == 'K'
        && (bytes[2] == 3 && bytes[3] == 4 || bytes[2] == 5 && bytes[3] == 6)
        && DOCX_CONTENT_TYPE.equals(declared)) {
      return DOCX_CONTENT_TYPE;
    }
    throw new BusinessException(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "MESSAGE_ATTACHMENT_TYPE",
        "Allowed attachments are PNG, JPG, WEBP, PDF, and DOCX files with matching content");
  }

  public static String sanitizeFileName(String name) {
    String sanitized =
        name == null || name.isBlank() ? "attachment" : name.replaceAll("[\\\\/\\r\\n]", "_");
    return sanitized.length() > 255 ? sanitized.substring(0, 255) : sanitized;
  }

  public static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static boolean startsWith(byte[] value, byte[] signature) {
    if (value.length < signature.length) {
      return false;
    }
    for (int index = 0; index < signature.length; index++) {
      if (value[index] != signature[index]) {
        return false;
      }
    }
    return true;
  }

  private static boolean asciiAt(byte[] value, int offset, String expected) {
    if (value.length < offset + expected.length()) {
      return false;
    }
    for (int index = 0; index < expected.length(); index++) {
      if (value[offset + index] != (byte) expected.charAt(index)) {
        return false;
      }
    }
    return true;
  }
}
