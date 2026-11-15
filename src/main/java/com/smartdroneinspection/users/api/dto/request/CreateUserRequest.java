package com.smartdroneinspection.users.api.dto.request;

import com.smartdroneinspection.users.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateUserRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 200) String fullName,
    @Size(max = 32) String phoneNumber,
    @Size(max = 500) String avatarUrl,
    @NotEmpty Set<UserRole> roles) {

  public CreateUserRequest(String email, String fullName, Set<UserRole> roles) {
    this(email, fullName, null, null, roles);
  }
}
