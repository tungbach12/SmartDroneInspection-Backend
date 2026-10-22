package com.smartdroneinspection.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestLocaleTest {

  @Test
  void missingOrUnsupportedHeaderDefaultsToEnglishRegardlessOfHostLocale() {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("vi-VN"));
      MockHttpServletRequest request = new MockHttpServletRequest();
      assertThat(RequestLocale.from(request)).isEqualTo(Locale.ENGLISH);
      request.addHeader("Accept-Language", "fr");
      assertThat(RequestLocale.from(request)).isEqualTo(Locale.ENGLISH);
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  void vietnameseRegionalPreferenceSelectsVietnamese() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Accept-Language", "vi-VN");
    assertThat(RequestLocale.from(request)).isEqualTo(Locale.forLanguageTag("vi"));
  }

  @Test
  void choosesSupportedLanguageByPreferenceQualityAndIgnoresWildcard() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Accept-Language", "fr, vi;q=0.7, en;q=0.9");
    assertThat(RequestLocale.from(request)).isEqualTo(Locale.ENGLISH);

    request = new MockHttpServletRequest();
    request.addHeader("Accept-Language", "*;q=1, vi;q=0.8");
    assertThat(RequestLocale.from(request)).isEqualTo(Locale.forLanguageTag("vi"));
  }

  @Test
  void fallsBackToEnglishForMalformedOrExplicitlyRejectedLocales() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Accept-Language", "bad language header");
    assertThat(RequestLocale.from(request)).isEqualTo(Locale.ENGLISH);

    request = new MockHttpServletRequest();
    request.addHeader("Accept-Language", "vi;q=0");
    assertThat(RequestLocale.from(request)).isEqualTo(Locale.ENGLISH);
  }
}
