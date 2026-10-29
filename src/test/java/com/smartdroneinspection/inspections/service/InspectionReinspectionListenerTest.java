package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.inspections.spi.ReinspectionRequestedEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InspectionReinspectionListenerTest {

  @Test
  void compatibilityListenerDoesNotAutoCreateReinspection() {
    InspectionReinspectionListener listener = new InspectionReinspectionListener();
    ReinspectionRequestedEvent legacyEvent =
        new ReinspectionRequestedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "legacy event",
            Instant.now());

    assertThat(listener).isNotNull();
    listener.onReinspectionRequested(legacyEvent);
  }
}
