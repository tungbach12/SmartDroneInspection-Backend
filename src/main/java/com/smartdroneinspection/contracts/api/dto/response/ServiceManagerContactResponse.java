package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.users.UserAccess;
import java.util.UUID;

/** Minimal manager contact information shown only to the owning Customer of a request. */
public record ServiceManagerContactResponse(
    UUID id, String fullName, String email, String phoneNumber, String avatarUrl) {

  public static ServiceManagerContactResponse from(UserAccess.ServiceManagerContact manager) {
    return new ServiceManagerContactResponse(
        manager.id(),
        manager.fullName(),
        manager.email(),
        manager.phoneNumber(),
        manager.avatarUrl());
  }
}
