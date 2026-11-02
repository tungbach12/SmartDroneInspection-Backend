package com.smartdroneinspection.infrastructure.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cryptographic utility for VNPay v2.1.0 request signing and Return/IPN signature verification
 * using HMAC-SHA512.
 */
public final class VnPayCrypto {

  private static final String HMAC_SHA512 = "HmacSHA512";

  private VnPayCrypto() {}

  /**
   * Builds the URL-encoded query string and appends {@code vnp_SecureHash} computed via HMAC-SHA512
   * over the alphabetically sorted parameters per VNPay v2.1.0 specification.
   *
   * @param params map of vnp_* parameters
   * @param hashSecret merchant vnp_HashSecret
   * @return signed query string including {@code &vnp_SecureHash=...}
   */
  public static String buildSignedQueryString(Map<String, String> params, String hashSecret) {
    if (hashSecret == null || hashSecret.isBlank()) {
      throw new IllegalArgumentException("VNPay hashSecret is required for signing");
    }
    if (params == null || params.isEmpty()) {
      throw new IllegalArgumentException("VNPay parameters are required for signing");
    }

    List<String> fieldNames = new ArrayList<>();
    for (Map.Entry<String, String> entry : params.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (key != null
          && !key.isBlank()
          && !"vnp_SecureHash".equalsIgnoreCase(key)
          && !"vnp_SecureHashType".equalsIgnoreCase(key)
          && value != null
          && !value.isEmpty()) {
        fieldNames.add(key);
      }
    }
    Collections.sort(fieldNames);

    StringBuilder hashData = new StringBuilder();
    StringBuilder query = new StringBuilder();

    for (int i = 0; i < fieldNames.size(); i++) {
      String fieldName = fieldNames.get(i);
      String fieldValue = params.get(fieldName);
      String encodedName = URLEncoder.encode(fieldName, StandardCharsets.US_ASCII);
      String encodedValue = URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII);

      if (i > 0) {
        hashData.append('&');
        query.append('&');
      }
      hashData.append(fieldName).append('=').append(encodedValue);
      query.append(encodedName).append('=').append(encodedValue);
    }

    String secureHash = hmacSha512Hex(hashData.toString(), hashSecret.trim());
    query.append("&vnp_SecureHash=").append(secureHash);
    return query.toString();
  }

  /**
   * Verifies the authenticity of incoming VNPay Return/IPN parameters against {@code
   * vnp_SecureHash}.
   *
   * @param params decoded request parameter map from HttpServletRequest
   * @param receivedSecureHash the signature provided in {@code vnp_SecureHash}
   * @param hashSecret merchant vnp_HashSecret
   * @return true if authentic, false otherwise
   */
  public static boolean verifySecureHash(
      Map<String, String> params, String receivedSecureHash, String hashSecret) {
    if (params == null
        || params.isEmpty()
        || receivedSecureHash == null
        || receivedSecureHash.isBlank()
        || hashSecret == null
        || hashSecret.isBlank()) {
      return false;
    }

    List<String> fieldNames = new ArrayList<>();
    for (Map.Entry<String, String> entry : params.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (key != null
          && key.startsWith("vnp_")
          && !"vnp_SecureHash".equalsIgnoreCase(key)
          && !"vnp_SecureHashType".equalsIgnoreCase(key)
          && value != null
          && !value.isEmpty()) {
        fieldNames.add(key);
      }
    }
    if (fieldNames.isEmpty()) {
      return false;
    }
    Collections.sort(fieldNames);

    StringBuilder hashData = new StringBuilder();
    for (int i = 0; i < fieldNames.size(); i++) {
      String fieldName = fieldNames.get(i);
      String fieldValue = params.get(fieldName);
      String encodedName = URLEncoder.encode(fieldName, StandardCharsets.US_ASCII);
      String encodedValue = URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII);
      if (i > 0) {
        hashData.append('&');
      }
      hashData.append(encodedName).append('=').append(encodedValue);
    }

    String expectedHash = hmacSha512Hex(hashData.toString(), hashSecret.trim());
    return MessageDigest.isEqual(
        expectedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
        receivedSecureHash.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
  }

  public static String hmacSha512Hex(String data, String key) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA512);
      SecretKeySpec secretKeySpec =
          new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA512);
      mac.init(secretKeySpec);
      byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(rawHmac);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to compute HMAC-SHA512 signature for VNPay", ex);
    }
  }
}
