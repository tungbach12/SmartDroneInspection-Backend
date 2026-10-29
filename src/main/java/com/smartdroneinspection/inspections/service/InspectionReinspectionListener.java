package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.spi.ReinspectionRequestedEvent;

/**
 * Retired compatibility stub; MF4 dispatch now requires explicit Customer request and Manager
 * assignment.
 */
final class InspectionReinspectionListener {

  void onReinspectionRequested(ReinspectionRequestedEvent ignored) {
    // Intentionally not a Spring component or event listener.
  }
}
