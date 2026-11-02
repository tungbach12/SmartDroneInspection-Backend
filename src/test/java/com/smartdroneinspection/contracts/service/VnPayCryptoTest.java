package com.smartdroneinspection.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.infrastructure.payment.VnPayCrypto;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VnPayCryptoTest {

  private static final String TEST_HASH_SECRET = "VNPAY_SANDBOX_SECRET_KEY_1234567890";

  @Test
  void buildSignedQueryStringGeneratesDeterministicHmacSha512Signature() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("vnp_Version", "2.1.0");
    params.put("vnp_Command", "pay");
    params.put("vnp_TmnCode", "DEMOTMN1");
    params.put("vnp_Amount", "500000000");
    params.put("vnp_CurrCode", "VND");
    params.put("vnp_TxnRef", "100001");
    params.put("vnp_OrderInfo", "Thanh toan dich vu kiem tra SDI 100001");
    params.put("vnp_OrderType", "other");
    params.put("vnp_Locale", "vn");
    params.put("vnp_ReturnUrl", "http://localhost:8080/api/v1/webhooks/vnpay/return");
    params.put("vnp_IpAddr", "127.0.0.1");
    params.put("vnp_CreateDate", "20261102100000");
    params.put("vnp_ExpireDate", "20261102101500");

    String query1 = VnPayCrypto.buildSignedQueryString(params, TEST_HASH_SECRET);
    String query2 = VnPayCrypto.buildSignedQueryString(params, TEST_HASH_SECRET);

    assertThat(query1).isEqualTo(query2);
    assertThat(query1).contains("&vnp_SecureHash=");

    String secureHash = query1.substring(query1.indexOf("&vnp_SecureHash=") + 16);
    assertThat(secureHash).matches("^[0-9a-f]{128}$");

    // Verify that the generated signature passes verifySecureHash
    assertThat(VnPayCrypto.verifySecureHash(params, secureHash, TEST_HASH_SECRET)).isTrue();
  }

  @Test
  void buildSignedQueryStringThrowsWhenSecretOrParamsMissing() {
    Map<String, String> params = Map.of("vnp_TxnRef", "100001");
    assertThatThrownBy(() -> VnPayCrypto.buildSignedQueryString(params, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> VnPayCrypto.buildSignedQueryString(params, "   "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> VnPayCrypto.buildSignedQueryString(Map.of(), TEST_HASH_SECRET))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void verifySecureHashReturnsTrueRegardlessOfInsertionOrderAndIgnoresHashFields() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("vnp_TxnRef", "100001");
    params.put("vnp_Amount", "500000000");
    params.put("vnp_ResponseCode", "00");
    params.put("vnp_TransactionStatus", "00");
    params.put("vnp_TransactionNo", "14226112");
    params.put("vnp_BankCode", "NCB");
    params.put("vnp_PayDate", "20261102100500");
    params.put("vnp_TmnCode", "DEMOTMN1");
    params.put("vnp_OrderInfo", "Thanh toan SDI 100001");

    String canonical =
        "vnp_Amount=500000000"
            + "&vnp_BankCode=NCB"
            + "&vnp_OrderInfo="
            + URLEncoder.encode("Thanh toan SDI 100001", StandardCharsets.US_ASCII)
            + "&vnp_PayDate=20261102100500"
            + "&vnp_ResponseCode=00"
            + "&vnp_TmnCode=DEMOTMN1"
            + "&vnp_TransactionNo=14226112"
            + "&vnp_TransactionStatus=00"
            + "&vnp_TxnRef=100001";
    String validSignature = VnPayCrypto.hmacSha512Hex(canonical, TEST_HASH_SECRET);

    Map<String, String> callbackParams = new HashMap<>(params);
    callbackParams.put("vnp_SecureHashType", "HmacSHA512");
    callbackParams.put("vnp_SecureHash", validSignature);

    assertThat(VnPayCrypto.verifySecureHash(callbackParams, validSignature, TEST_HASH_SECRET))
        .isTrue();
  }

  @Test
  void verifySecureHashRejectsTamperedParametersOrInvalidSignature() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("vnp_TxnRef", "100001");
    params.put("vnp_Amount", "500000000");
    params.put("vnp_ResponseCode", "00");

    String query = VnPayCrypto.buildSignedQueryString(params, TEST_HASH_SECRET);
    String validHash = query.substring(query.indexOf("&vnp_SecureHash=") + 16);

    Map<String, String> tampered = new LinkedHashMap<>(params);
    tampered.put("vnp_Amount", "10000");

    assertThat(VnPayCrypto.verifySecureHash(tampered, validHash, TEST_HASH_SECRET)).isFalse();
    assertThat(VnPayCrypto.verifySecureHash(params, "forged_hash", TEST_HASH_SECRET)).isFalse();
    assertThat(VnPayCrypto.verifySecureHash(params, validHash, "wrong_secret")).isFalse();
  }
}
