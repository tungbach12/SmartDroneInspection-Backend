package com.smartdroneinspection.contracts.api.dto.response;

import java.util.List;

public record FinanceHistoryPageResponse(
    List<FinanceHistoryItemResponse> content, long totalElements, int page, int size) {

  public FinanceHistoryPageResponse {
    content = content == null ? List.of() : List.copyOf(content);
  }

  public static FinanceHistoryPageResponse of(
      List<FinanceHistoryItemResponse> content, long totalElements, int page, int size) {
    return new FinanceHistoryPageResponse(content, totalElements, page, size);
  }
}
