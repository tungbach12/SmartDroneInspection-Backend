package com.smartdroneinspection.shared;

import java.util.Optional;

public sealed interface Result<T> permits Result.Ok, Result.Err {

  record Ok<T>(T value) implements Result<T> {}

  record Err<T>(String error) implements Result<T> {}

  static <T> Result<T> ok(T value) {
    return new Ok<>(value);
  }

  static <T> Result<T> err(String error) {
    return new Err<>(error);
  }

  default boolean isOk() {
    return this instanceof Ok<T>;
  }

  default Optional<T> getValue() {
    return switch (this) {
      case Ok<T> ok -> Optional.of(ok.value());
      case Err<T> e -> Optional.empty();
    };
  }
}
