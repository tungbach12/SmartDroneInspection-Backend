package com.smartdroneinspection.shared.api;

import java.util.UUID;

/**
 * Shared boundary interface to determine if an inspection requires payment confirmation before
 * releasing downloadable deliverable artifacts.
 */
public interface InspectionPaymentGate {

  /**
   * Returns true if the inspection has an associated commercial service occurrence whose customer
   * payment is not yet confirmed. Returns false for operational inspections or occurrences that
   * have already been paid.
   */
  boolean isPaymentDue(UUID inspectionId);

  /**
   * Returns true if the inspection's linked occurrence has already been accepted by the customer
   * (reportAcceptedAt != null) or paid (paymentStatus == PAID).
   */
  default boolean isAcceptedOrPaid(UUID inspectionId) {
    return false;
  }
}
