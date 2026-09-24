package com.smartdroneinspection.shared.api;

/** Common JSON envelope for successful API responses. */
public record ApiResponse<T>(boolean success, String message, T data) {

  private static final String DEFAULT_SUCCESS_MESSAGE = "Success";

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, DEFAULT_SUCCESS_MESSAGE, data);
  }

  public static <T> ApiResponse<T> success(String message, T data) {
    return new ApiResponse<>(true, message, data);
  }
}
