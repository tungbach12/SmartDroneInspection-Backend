package com.smartdroneinspection.users.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class AuthCrypto {

  private static final SecureRandom RANDOM = new SecureRandom();

  private AuthCrypto() {}

  public static String randomToken() {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
  }

  public static byte[] randomBytes(int length) {
    byte[] bytes = new byte[length];
    RANDOM.nextBytes(bytes);
    return bytes;
  }

  public static String hmacSha256Hex(String value, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return hex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to hash authentication material", ex);
    }
  }

  private static String hex(byte[] value) {
    return HexFormat.of().formatHex(value);
  }
}
