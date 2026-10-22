package com.smartdroneinspection.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerLocalizationTest {

  private final ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
  private final GlobalExceptionHandler handler =
      new GlobalExceptionHandler(new ProblemText(messages));

  GlobalExceptionHandlerLocalizationTest() {
    messages.setBasename("messages");
    messages.setDefaultEncoding("UTF-8");
  }

  @Test
  void usesVietnameseProblemTitleAndDetailWhenVietnameseIsRequested() {
    HttpServletRequest request = requestWithLanguage("vi");

    var problem =
        handler.handleBusiness(
            new BusinessException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access is denied."),
            request);

    assertThat(problem.getTitle()).isEqualTo("Bị từ chối truy cập");
    assertThat(problem.getDetail()).isEqualTo("Bạn không có quyền truy cập.");
    assertThat(problem.getProperties()).containsEntry("code", "ACCESS_DENIED");
  }

  @Test
  void keepsEnglishDetailWhenVietnameseTranslationIsMissing() {
    HttpServletRequest request = requestWithLanguage("vi");
    var problem =
        handler.handleBusiness(
            new BusinessException(
                HttpStatus.CONFLICT, "UNTRANSLATED_CODE", "English fallback detail."),
            request);

    assertThat(problem.getDetail()).isEqualTo("English fallback detail.");
  }

  @Test
  void usesEnglishForDefaultAndUnsupportedLanguages() {
    for (String language : new String[] {null, "fr"}) {
      HttpServletRequest request = requestWithLanguage(language);
      var problem =
          handler.handleBusiness(
              new BusinessException(HttpStatus.FORBIDDEN, "CUSTOM_ERROR", "Custom English detail."),
              request);

      assertThat(problem.getTitle()).isEqualTo("Forbidden");
      assertThat(problem.getDetail()).isEqualTo("Custom English detail.");
      assertThat(problem.getProperties()).containsEntry("code", "CUSTOM_ERROR");
    }
  }

  private static HttpServletRequest requestWithLanguage(String language) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/test");
    if (language != null) request.addHeader("Accept-Language", language);
    return request;
  }
}
