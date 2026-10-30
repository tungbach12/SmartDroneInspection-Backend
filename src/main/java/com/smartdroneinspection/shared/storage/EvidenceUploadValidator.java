package com.smartdroneinspection.shared.storage;

import com.smartdroneinspection.shared.exception.BusinessException;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

/** Validates supported evidence formats from their declared media type and file signature. */
public final class EvidenceUploadValidator {

  private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
  private static final byte[] PDF_SIGNATURE =
      "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  private static final byte[] PNG_SIGNATURE =
      new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

  private EvidenceUploadValidator() {}

  public static ValidatedUpload validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "A non-empty document is required");
    }
    if (file.getSize() > MAX_SIZE_BYTES) {
      throw new BusinessException(
          HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Maximum document size is 10 MB");
    }
    byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException ex) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Unreadable file");
    }
    String contentType = file.getContentType() == null ? "" : file.getContentType();
    String extension;
    if (contentType.equals("application/pdf") && startsWith(content, PDF_SIGNATURE)) {
      extension = ".pdf";
    } else if (contentType.equals("image/png") && startsWith(content, PNG_SIGNATURE)) {
      extension = ".png";
    } else if (contentType.equals("image/jpeg")
        && content.length >= 3
        && content[0] == (byte) 0xff
        && content[1] == (byte) 0xd8
        && content[2] == (byte) 0xff) {
      extension = ".jpg";
    } else if (contentType.equals("image/webp")
        && content.length >= 12
        && ascii(content, 0, "RIFF")
        && ascii(content, 8, "WEBP")) {
      extension = ".webp";
    } else {
      throw new BusinessException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "UNSUPPORTED_MEDIA_TYPE",
          "Allowed document types: png, jpeg, webp, pdf with matching file content");
    }
    return new ValidatedUpload(contentType, extension, content);
  }

  public static String contentType(String extension) {
    return switch (extension) {
      case "pdf" -> "application/pdf";
      case "png" -> "image/png";
      case "jpg" -> "image/jpeg";
      case "webp" -> "image/webp";
      default -> throw new IllegalArgumentException("Unsupported evidence extension");
    };
  }

  private static boolean startsWith(byte[] content, byte[] signature) {
    if (content.length < signature.length) return false;
    for (int index = 0; index < signature.length; index++) {
      if (content[index] != signature[index]) return false;
    }
    return true;
  }

  private static boolean ascii(byte[] content, int offset, String expected) {
    if (content.length < offset + expected.length()) return false;
    for (int index = 0; index < expected.length(); index++) {
      if (content[offset + index] != expected.charAt(index)) return false;
    }
    return true;
  }

  public record ValidatedUpload(String contentType, String extension, byte[] content) {}
}
