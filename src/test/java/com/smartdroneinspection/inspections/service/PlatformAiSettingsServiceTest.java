package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.smartdroneinspection.inspections.service.PlatformAiSettingsService.AiThresholdConfig;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PlatformAiSettingsServiceTest {

  private final PlatformAiSettingsService service =
      new PlatformAiSettingsService(mock(JdbcTemplate.class));

  @Test
  @DisplayName("auto-confirms detections at or above the configured percentage threshold")
  void shouldAutoConfirmWhenAboveThreshold() {
    AiThresholdConfig config =
        new AiThresholdConfig(true, new BigDecimal("85.00"), 0L, 0L, Instant.now());

    assertThat(service.shouldAutoConfirm(new BigDecimal("0.85"), config)).isTrue();
    assertThat(service.shouldAutoConfirm(new BigDecimal("0.9234"), config)).isTrue();
    assertThat(service.shouldAutoConfirm(new BigDecimal("88.5"), config)).isTrue();
    assertThat(service.shouldAutoConfirm(new BigDecimal("0.849"), config)).isFalse();
    assertThat(service.shouldAutoConfirm(null, config)).isFalse();
  }

  @Test
  @DisplayName("does not auto-confirm when auto-confirm is disabled by Admin")
  void shouldNotAutoConfirmWhenDisabled() {
    AiThresholdConfig disabledConfig =
        new AiThresholdConfig(false, new BigDecimal("70.00"), 0L, 0L, Instant.now());

    assertThat(service.shouldAutoConfirm(new BigDecimal("0.99"), disabledConfig)).isFalse();
  }
}
