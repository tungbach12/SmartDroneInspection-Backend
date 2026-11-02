package com.smartdroneinspection.users.service;

import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

  private static final int MIN_LENGTH = 8;
  private static final int MAX_LENGTH = 128;
  private static final Set<String> COMMON_PASSWORDS =
      Set.of(
          "password",
          "password123",
          "12345678",
          "qwertyuiop",
          "passwordpassword",
          "password123456",
          "123456789012345",
          "qwertyuiopasdfgh",
          "letmeinletmein");

  public void validate(String password, String email, String fullName) {
    if (password == null) {
      throw new IllegalArgumentException("Password is required.");
    }
    int length = password.codePointCount(0, password.length());
    if (length < MIN_LENGTH || length > MAX_LENGTH) {
      throw new IllegalArgumentException("Password must contain between 8 and 128 characters.");
    }

    boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
    boolean hasDigit = password.chars().anyMatch(Character::isDigit);
    boolean hasSpecial = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));

    if (length < 20 && (!hasUpper || !hasDigit || !hasSpecial)) {
      throw new IllegalArgumentException(
          "Password must contain at least one uppercase letter, one number, and one special character.");
    }

    String normalized = password.toLowerCase(Locale.ROOT);
    if (COMMON_PASSWORDS.contains(normalized)) {
      throw new IllegalArgumentException("Choose a less common password.");
    }

    if (email != null && email.contains("@")) {
      String emailIdentity = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
      if (emailIdentity.length() >= 4 && normalized.contains(emailIdentity)) {
        throw new IllegalArgumentException("Password must not contain account information.");
      }
    }

    if (fullName != null) {
      for (String namePart : fullName.toLowerCase(Locale.ROOT).split("\\s+")) {
        if (namePart.length() >= 4 && normalized.contains(namePart)) {
          throw new IllegalArgumentException("Password must not contain account information.");
        }
      }
    }
  }
}
