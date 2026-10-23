package com.smartdroneinspection.infrastructure.payment;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the PayPal settlement channel.
 *
 * <p>The credentials are deployment secrets and are never committed. Leave {@code enabled} off to
 * keep the platform in wire-transfer-only mode, which is the safe default for a deployment that has
 * not been issued PayPal credentials.
 */
@ConfigurationProperties("app.payment.paypal")
public record PayPalProperties(
    boolean enabled,
    String mode,
    String clientId,
    String clientSecret,
    String baseUrl,
    String returnUrl,
    String cancelUrl,
    String webhookId,
    Duration connectTimeout,
    Duration readTimeout) {}
