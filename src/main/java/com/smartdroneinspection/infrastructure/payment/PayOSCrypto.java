package com.smartdroneinspection.infrastructure.payment;

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
 * Cryptographic utility for PayOS request signing and webhook signature verification using
 * HMAC-SHA256.
 */
public final class PayOSCrypto {

  private static final String HMAC_SHA256 = "HmacSHA256";

  private PayOSCrypto() {}

  /**
   * Generates the HMAC-SHA256 signature for PayOS payment link creation.
   *
   * <p>Canonical string format: {@code
   * amount=$amount&cancelUrl=$cancelUrl&description=$description&orderCode=$orderCode&returnUrl=$returnUrl}
   *
   * @param amount payment amount (VND)
   * @param cancelUrl cancellation callback URL
   * @param description payment description (max 25 chars per PayOS specification)
   * @param orderCode unique numeric order code
   * @param returnUrl return callback URL
   * @param checksumKey merchant checksum key
   * @return lowercase hex HMAC-SHA256 signature
   */
  public static String createPaymentLinkSignature(
      long amount,
      String cancelUrl,
      String description,
      long orderCode,
      String returnUrl,
      String checksumKey) {
    if (checksumKey == null || checksumKey.isBlank()) {
      throw new IllegalArgumentException("PayOS checksum key is required for signing");
    }
    String canonicalString =
        "amount="
            + amount
            + "&cancelUrl="
            + (cancelUrl == null ? "" : cancelUrl)
            + "&description="
            + (description == null ? "" : description)
            + "&orderCode="
            + orderCode
            + "&returnUrl="
            + (returnUrl == null ? "" : returnUrl);
    return hmacSha256Hex(canonicalString, checksumKey);
  }

  /**
   * Verifies the authenticity of an incoming PayOS webhook notification.
   *
   * <p>Sorts the keys of the {@code data} map alphabetically, joins as {@code key=value&...} with
   * null values mapped to empty strings ({@code key=}), computes HMAC-SHA256, and performs a
   * constant-time comparison against the received signature to mitigate timing attacks.
   *
   * @param data the webhook payload data dictionary
   * @param receivedSignature the signature received in the webhook payload
   * @param checksumKey merchant checksum key
   * @return true if authentic, false otherwise
   */
  public static boolean verifyWebhookSignature(
      Map<String, Object> data, String receivedSignature, String checksumKey) {
    if (data == null
        || receivedSignature == null
        || receivedSignature.isBlank()
        || checksumKey == null
        || checksumKey.isBlank()) {
      return false;
    }

    List<String> sortedKeys = new ArrayList<>(data.keySet());
    Collections.sort(sortedKeys);

    StringBuilder canonicalBuilder = new StringBuilder();
    for (int i = 0; i < sortedKeys.size(); i++) {
      if (i > 0) {
        canonicalBuilder.append('&');
      }
      String key = sortedKeys.get(i);
      Object val = data.get(key);
      canonicalBuilder.append(key).append('=').append(val == null ? "" : val.toString());
    }

    String expectedSignature = hmacSha256Hex(canonicalBuilder.toString(), checksumKey);
    return MessageDigest.isEqual(
        expectedSignature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
        receivedSignature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
  }

  /** Computes HMAC-SHA256 hash formatted as a lowercase hex string. */
  public static String hmacSha256Hex(String data, String key) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA256);
      SecretKeySpec secretKeySpec =
          new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
      mac.init(secretKeySpec);
      byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to compute HMAC-SHA256 hash", ex);
    }
  }
}
