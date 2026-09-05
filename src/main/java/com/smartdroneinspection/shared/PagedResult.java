package com.smartdroneinspection.shared;

import java.util.List;

public record PagedResult<T>(List<T> content, long totalElements, int page, int size) {

  public static <T> PagedResult<T> of(List<T> content, long total, int page, int size) {
    return new PagedResult<>(List.copyOf(content), total, page, size);
  }
}
