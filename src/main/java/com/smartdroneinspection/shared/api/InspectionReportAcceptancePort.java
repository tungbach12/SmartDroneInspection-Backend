package com.smartdroneinspection.shared.api;

import java.util.UUID;

/**
 * Shared boundary interface for customer acceptance and payment of a published inspection report.
 */
public interface InspectionReportAcceptancePort {

  /** Accepts the published report version for the occurrence associated with this inspection. */
  void acceptReport(UUID actorId, UUID inspectionId, UUID reportVersionId);

  /** Creates a PayOS checkout link for the occurrence associated with this inspection. */
  PayOSCheckoutDetails createPayOSPaymentLink(UUID actorId, UUID inspectionId);

  record PayOSCheckoutDetails(
      long orderCode, long amount, String paymentLinkId, String checkoutUrl, String qrCode) {}
}
