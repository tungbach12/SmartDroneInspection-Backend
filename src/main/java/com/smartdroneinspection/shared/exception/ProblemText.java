package com.smartdroneinspection.shared.exception;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Resolves localized text for stable API problems while preserving their code and status. */
@Component
public class ProblemText {

  private final MessageSource messages;

  public ProblemText(MessageSource messages) {
    this.messages = messages;
  }

  public String title(HttpStatus status, Locale locale) {
    String fallback = status.getReasonPhrase();
    if (isVietnamese(locale)) {
      fallback =
          status.is4xxClientError()
              ? "Yêu cầu không thành công"
              : status.is5xxServerError() ? "Lỗi máy chủ" : "Lỗi yêu cầu";
    }
    return messages.getMessage("problem.title." + status.value(), null, fallback, locale);
  }

  public String detail(String code, String englishDetail, Locale locale) {
    if (!isVietnamese(locale)) {
      return englishDetail;
    }
    return messages.getMessage("problem.detail." + code, null, englishDetail, locale);
  }

  private static boolean isVietnamese(Locale locale) {
    return locale != null && "vi".equalsIgnoreCase(locale.getLanguage());
  }
}
