package com.smartdroneinspection.infrastructure.payment;

import com.smartdroneinspection.contracts.gateway.VnPayGateway;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * VNPay v2.1.0 payment gateway implementation.
 *
 * <p>Constructs standard VNPay redirect URLs signed with HMAC-SHA512 and verifies incoming Return
 * and IPN webhook notifications.
 */
public class VnPayPaymentGateway implements VnPayGateway {

  private static final ZoneId VNPAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final DateTimeFormatter VNPAY_TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private final VnPayProperties properties;

  public VnPayPaymentGateway(VnPayProperties properties) {
    this.properties = properties;
  }

  @Override
  public String resolveReturnUrl(String inspectionId, long orderCode) {
    String base = properties.returnUrl();
    if (base == null || base.isBlank()) {
      base = "http://localhost:8080/api/v1/webhooks/vnpay/return";
    }
    return base.replace("{inspectionId}", inspectionId != null ? inspectionId : "");
  }

  @Override
  public String resolveFrontendReturnUrl(
      String inspectionId, long orderCode, boolean success, String responseCode) {
    String base = properties.frontendReturnUrl();
    if (base == null || base.isBlank()) {
      base = "http://localhost:3000/client/reports/{inspectionId}";
    }
    String resolved =
        base.replace(
            "{inspectionId}",
            (inspectionId != null && !inspectionId.isBlank()) ? inspectionId : "");
    String sep = resolved.contains("?") ? "&" : "?";
    if (success) {
      return resolved + sep + "payment=success&gateway=vnpay&orderCode=" + orderCode;
    }
    return resolved
        + sep
        + "payment=cancelled&gateway=vnpay&orderCode="
        + orderCode
        + (responseCode != null ? "&code=" + responseCode : "");
  }

  @Override
  public VnPayPaymentLink createPaymentUrl(
      long orderCode, long amount, String description, String clientIp, String returnUrl) {
    if (amount <= 0) {
      throw new IllegalArgumentException("VNPay payment amount must be positive");
    }

    String actualReturnUrl =
        (returnUrl != null && !returnUrl.isBlank()) ? returnUrl : properties.returnUrl();
    String cleanOrderInfo = sanitizeOrderInfo(description, orderCode);
    String normalizedIp = normalizeClientIp(clientIp);
    String txnRef = String.valueOf(orderCode);

    ZonedDateTime now = ZonedDateTime.now(VNPAY_ZONE);
    ZonedDateTime expire = now.plusMinutes(properties.expireMinutes());

    Map<String, String> vnpParams = new LinkedHashMap<>();
    vnpParams.put("vnp_Version", properties.version());
    vnpParams.put("vnp_Command", properties.command());
    vnpParams.put("vnp_TmnCode", properties.tmnCode().trim());
    vnpParams.put("vnp_Amount", String.valueOf(amount * 100L));
    vnpParams.put("vnp_CurrCode", properties.currCode());
    vnpParams.put("vnp_TxnRef", txnRef);
    vnpParams.put("vnp_OrderInfo", cleanOrderInfo);
    vnpParams.put("vnp_OrderType", properties.orderType());
    vnpParams.put("vnp_Locale", properties.locale());
    vnpParams.put("vnp_ReturnUrl", actualReturnUrl);
    vnpParams.put("vnp_IpAddr", normalizedIp);
    vnpParams.put("vnp_CreateDate", now.format(VNPAY_TIME_FORMAT));
    vnpParams.put("vnp_ExpireDate", expire.format(VNPAY_TIME_FORMAT));

    String signedQuery = VnPayCrypto.buildSignedQueryString(vnpParams, properties.hashSecret());
    String baseUrl = properties.payUrl();
    String checkoutUrl = baseUrl + (baseUrl.contains("?") ? "&" : "?") + signedQuery;

    return new VnPayPaymentLink(orderCode, checkoutUrl, amount, txnRef);
  }

  @Override
  public boolean verifySecureHash(Map<String, String> params, String receivedSecureHash) {
    if (properties.hashSecret() == null || properties.hashSecret().isBlank()) {
      return false;
    }
    return VnPayCrypto.verifySecureHash(params, receivedSecureHash, properties.hashSecret());
  }

  private static String sanitizeOrderInfo(String description, long orderCode) {
    String raw =
        (description != null && !description.isBlank())
            ? description.trim()
            : ("Thanh toan dich vu kiem tra SDI " + orderCode);
    String ascii = raw.replaceAll("[^a-zA-Z0-9 _.-]", " ").replaceAll("\\s+", " ").trim();
    if (ascii.isEmpty()) {
      ascii = "Thanh toan dich vu kiem tra SDI " + orderCode;
    }
    return ascii.length() > 100 ? ascii.substring(0, 100) : ascii;
  }

  private static String normalizeClientIp(String clientIp) {
    if (clientIp == null || clientIp.isBlank()) {
      return "127.0.0.1";
    }
    String ip = clientIp.split(",")[0].trim();
    if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip) || ip.contains(":")) {
      return "127.0.0.1";
    }
    return ip;
  }
}
