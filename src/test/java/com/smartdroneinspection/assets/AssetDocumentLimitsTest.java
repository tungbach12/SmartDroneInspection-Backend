package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.assets.service.AssetDocumentService;
import com.smartdroneinspection.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

/**
 * The asset document upload cap is an operational threshold, so it must be configurable rather than
 * a literal in the service.
 */
class AssetDocumentLimitsTest {

  private static final long TEN_MEBIBYTES = 10L * 1024 * 1024;

  @Test
  void theDefaultMaximumIsTenMebibytes() {
    assertThat(AssetDocumentService.defaultMaxSizeBytes()).isEqualTo(TEN_MEBIBYTES);
  }

  @Test
  void aDocumentAtExactlyTheConfiguredLimitIsAccepted() {
    assertThat(AssetDocumentService.requireWithinSizeLimit(4096L, 4096L)).isEqualTo(4096L);
  }

  @Test
  void aDocumentOneByteOverTheConfiguredLimitIsRejected() {
    assertThatThrownBy(() -> AssetDocumentService.requireWithinSizeLimit(4097L, 4096L))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void aTightenedLimitRejectsWhatTheDefaultWouldHaveAccepted() {
    assertThatThrownBy(() -> AssetDocumentService.requireWithinSizeLimit(8192L, 1024L))
        .isInstanceOf(BusinessException.class);
    assertThat(AssetDocumentService.requireWithinSizeLimit(1024L, 1024L)).isEqualTo(1024L);
  }

  @Test
  void theAllowedContentTypesAreAFiniteSetRatherThanOpenEnded() {
    assertThat(AssetDocumentService.allowedContentTypes())
        .containsExactlyInAnyOrder("image/png", "image/jpeg", "image/webp", "application/pdf");
  }
}
