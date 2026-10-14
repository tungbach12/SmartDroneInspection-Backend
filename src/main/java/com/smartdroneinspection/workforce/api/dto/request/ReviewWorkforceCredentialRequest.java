package com.smartdroneinspection.workforce.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An internal review decision on a credential record. The decision is a human act inside the
 * customer organization; it records who decided, when and why, and it is not an external or
 * government verification of the document.
 */
public record ReviewWorkforceCredentialRequest(
    @NotNull Decision decision, @NotBlank @Size(max = 2000) String reason) {

  public enum Decision {
    APPROVE,
    REJECT
  }
}
