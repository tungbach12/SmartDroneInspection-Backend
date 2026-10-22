package com.smartdroneinspection.shared;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;

/** Supported API response locales; English is the explicit default. */
public final class RequestLocale {

  private RequestLocale() {}

  public static Locale from(HttpServletRequest request) {
    String acceptLanguage = request.getHeader("Accept-Language");
    if (acceptLanguage == null || acceptLanguage.isBlank()) {
      return Locale.ENGLISH;
    }
    try {
      for (Locale.LanguageRange preference : Locale.LanguageRange.parse(acceptLanguage)) {
        if (preference.getWeight() == 0 || "*".equals(preference.getRange())) {
          continue;
        }
        String language = Locale.forLanguageTag(preference.getRange()).getLanguage();
        if ("vi".equalsIgnoreCase(language)) {
          return Locale.forLanguageTag("vi");
        }
        if ("en".equalsIgnoreCase(language)) {
          return Locale.ENGLISH;
        }
      }
    } catch (IllegalArgumentException ignored) {
      return Locale.ENGLISH;
    }
    return Locale.ENGLISH;
  }
}
