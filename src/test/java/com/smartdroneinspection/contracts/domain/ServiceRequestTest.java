package com.smartdroneinspection.contracts.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServiceRequestTest {

  @Test
  void recurringRequestMustBeBoundedByExactlyOneLimit() {
    assertThatThrownBy(() -> recurringRequest(4, LocalDate.of(2027, 1, 1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exactly one");
  }

  @Test
  void oneTimeRequestCannotIncludeRecurrenceLimit() {
    assertThatThrownBy(
            () ->
                new ServiceRequest(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Bridge inspection",
                    LocalDate.of(2027, 1, 1),
                    LocalDate.of(2027, 1, 2),
                    "Asia/Ho_Chi_Minh",
                    LocalTime.of(8, 0),
                    LocalTime.of(17, 0),
                    ServiceRequest.RequestType.ONE_TIME,
                    null,
                    null,
                    3,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("one-time");
  }

  private static ServiceRequest recurringRequest(Integer count, LocalDate endDate) {
    return new ServiceRequest(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Bridge inspection",
        LocalDate.of(2027, 1, 1),
        LocalDate.of(2027, 1, 5),
        "Asia/Ho_Chi_Minh",
        LocalTime.of(8, 0),
        LocalTime.of(17, 0),
        ServiceRequest.RequestType.RECURRING,
        "MONTH",
        1,
        count,
        endDate);
  }
}
