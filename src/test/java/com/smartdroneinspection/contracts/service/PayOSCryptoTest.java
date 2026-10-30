package com.smartdroneinspection.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.infrastructure.payment.PayOSCrypto;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PayOSCryptoTest {

  private static final String TEST_CHECKSUM_KEY = "test_checksum_key_1234567890";

  @Test
  void createPaymentLinkSignatureGeneratesDeterministicHmacSha256Hex() {
    long amount = 500000;
    String cancelUrl = "http://localhost:5173/cancel";
    String description = "SDI OCC 1";
    long orderCode = 100001;
    String returnUrl = "http://localhost:5173/return";

    String signature1 =
        PayOSCrypto.createPaymentLinkSignature(
            amount, cancelUrl, description, orderCode, returnUrl, TEST_CHECKSUM_KEY);
    String signature2 =
        PayOSCrypto.createPaymentLinkSignature(
            amount, cancelUrl, description, orderCode, returnUrl, TEST_CHECKSUM_KEY);

    assertThat(signature1).isNotNull().matches("^[0-9a-f]{64}$");
    assertThat(signature1).isEqualTo(signature2);

    // Verify canonical string composition matches expected HMAC
    String canonical =
        "amount="
            + amount
            + "&cancelUrl="
            + cancelUrl
            + "&description="
            + description
            + "&orderCode="
            + orderCode
            + "&returnUrl="
            + returnUrl;
    assertThat(signature1).isEqualTo(PayOSCrypto.hmacSha256Hex(canonical, TEST_CHECKSUM_KEY));
  }

  @Test
  void createPaymentLinkSignatureThrowsWhenChecksumKeyIsMissing() {
    assertThatThrownBy(
            () ->
                PayOSCrypto.createPaymentLinkSignature(1000, "cancel", "desc", 123, "return", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                PayOSCrypto.createPaymentLinkSignature(
                    1000, "cancel", "desc", 123, "return", "   "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void verifyWebhookSignatureReturnsTrueForValidSignatureRegardlessOfKeyOrder() {
    // Deliberately unsorted insertion order
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("orderCode", 100001L);
    data.put("amount", 500000L);
    data.put("description", "SDI OCC 1");
    data.put("accountNumber", "0123456789");
    data.put("reference", "PAYOS-REF-999");
    data.put("transactionDateTime", "2026-03-30 10:00:00");
    data.put("currency", "VND");
    data.put("code", "00");

    // Canonical sorted representation
    String canonical =
        "accountNumber=0123456789&amount=500000&code=00&currency=VND&description=SDI OCC 1&orderCode=100001&reference=PAYOS-REF-999&transactionDateTime=2026-03-30 10:00:00";
    String validSignature = PayOSCrypto.hmacSha256Hex(canonical, TEST_CHECKSUM_KEY);

    boolean verified = PayOSCrypto.verifyWebhookSignature(data, validSignature, TEST_CHECKSUM_KEY);
    assertThat(verified).isTrue();
  }

  @Test
  void verifyWebhookSignatureHandlesNullValuesAsEmptyStrings() {
    Map<String, Object> data = new HashMap<>();
    data.put("orderCode", 100001L);
    data.put("amount", 250000L);
    data.put("counterAccountBankId", null);
    data.put("code", "00");

    String canonical = "amount=250000&code=00&counterAccountBankId=&orderCode=100001";
    String signature = PayOSCrypto.hmacSha256Hex(canonical, TEST_CHECKSUM_KEY);

    assertThat(PayOSCrypto.verifyWebhookSignature(data, signature, TEST_CHECKSUM_KEY)).isTrue();
  }

  @Test
  void verifyWebhookSignatureRejectsTamperedOrInvalidSignatures() {
    Map<String, Object> data = Map.of("orderCode", 100001L, "amount", 500000L);
    String validSig =
        PayOSCrypto.hmacSha256Hex("amount=500000&orderCode=100001", TEST_CHECKSUM_KEY);

    // Tampered signature
    assertThat(PayOSCrypto.verifyWebhookSignature(data, validSig + "tamper", TEST_CHECKSUM_KEY))
        .isFalse();
    // Tampered data
    Map<String, Object> tamperedData = Map.of("orderCode", 100001L, "amount", 100000L);
    assertThat(PayOSCrypto.verifyWebhookSignature(tamperedData, validSig, TEST_CHECKSUM_KEY))
        .isFalse();
    // Null or blank inputs
    assertThat(PayOSCrypto.verifyWebhookSignature(null, validSig, TEST_CHECKSUM_KEY)).isFalse();
    assertThat(PayOSCrypto.verifyWebhookSignature(data, null, TEST_CHECKSUM_KEY)).isFalse();
    assertThat(PayOSCrypto.verifyWebhookSignature(data, "", TEST_CHECKSUM_KEY)).isFalse();
    assertThat(PayOSCrypto.verifyWebhookSignature(data, validSig, null)).isFalse();
  }
}
