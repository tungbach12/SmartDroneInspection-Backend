package com.smartdroneinspection.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.infrastructure.payment.SepayPaymentGateway;
import com.smartdroneinspection.infrastructure.payment.SepayProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the Sepay VietQR gateway: QR URL construction, merchant account fallbacks, and
 * constant-time API key verification.
 */
class SepayGatewayTest {

  private static final String API_KEY = "sepay-test-api-key-123456";

  private static SepayPaymentGateway gateway(String apiKey, String acc, String bank) {
    return new SepayPaymentGateway(
        new SepayProperties(
            false,
            "https://vietqr.app",
            apiKey,
            acc,
            bank,
            "NGUYEN VAN A",
            null,
            null,
            Duration.ofSeconds(5),
            Duration.ofSeconds(15)));
  }

  @Test
  @DisplayName("createPaymentLink embeds account, bank, amount and SDI order code")
  void createPaymentLinkBuildsVietQrUrl() {
    SepayPaymentGateway gw = gateway(API_KEY, "0123456789", "MB");

    SepayPaymentGateway.SepayPaymentLink link =
        gw.createPaymentLink(123456789L, 500000L, "SDI OCC 1", null, null, null);

    assertThat(link.checkoutUrl())
        .contains("https://vietqr.app/img?acc=0123456789")
        .contains("bank=MB")
        .contains("amount=500000")
        .contains("des=SDI123456789")
        .contains("template=compact");
    assertThat(link.qrCode()).isEqualTo("SDI123456789");
    assertThat(link.paymentLinkId()).isEqualTo("123456789");
    assertThat(link.amount()).isEqualTo(500000L);
  }

  @Test
  @DisplayName("createPaymentLink falls back to configured merchant account")
  void createPaymentLinkUsesConfiguredAccountWhenArgsMissing() {
    SepayPaymentGateway gw = gateway(API_KEY, "0123456789", "MB");

    SepayPaymentGateway.SepayPaymentLink link = gw.createPaymentLink(1L, 100L, "", "", "", "");

    assertThat(link.checkoutUrl()).contains("acc=0123456789").contains("bank=MB");
  }

  @Test
  @DisplayName("createPaymentLink throws when no merchant account is configured")
  void createPaymentLinkFailsWhenMerchantAccountMissing() {
    SepayPaymentGateway gw = gateway(API_KEY, "", "");

    assertThatThrownBy(() -> gw.createPaymentLink(1L, 100L, "desc", null, null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Sepay merchant account number and bank name are required");
  }

  @Test
  @DisplayName("verifyWebhookApiKey accepts the exact configured key")
  void verifyWebhookApiKeyAcceptsExactKey() {
    SepayPaymentGateway gw = gateway(API_KEY, "0123456789", "MB");
    assertThat(gw.verifyWebhookApiKey(API_KEY)).isTrue();
  }

  @Test
  @DisplayName("verifyWebhookApiKey rejects wrong, missing, and null keys")
  void verifyWebhookApiKeyRejectsInvalid() {
    SepayPaymentGateway gw = gateway(API_KEY, "0123456789", "MB");
    assertThat(gw.verifyWebhookApiKey("wrong-key")).isFalse();
    assertThat(gw.verifyWebhookApiKey(null)).isFalse();
    assertThat(gw.verifyWebhookApiKey("")).isFalse();
  }

  @Test
  @DisplayName("verifyWebhookApiKey fails closed when no key is configured")
  void verifyWebhookApiKeyFailsClosedWithoutConfig() {
    SepayPaymentGateway gw = gateway(null, "0123456789", "MB");
    assertThat(gw.verifyWebhookApiKey(API_KEY)).isFalse();
  }
}
