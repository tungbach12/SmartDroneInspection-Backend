package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.Size;

/** Request payload for cancelling a service request or undispatched occurrence. */
public record CancelServiceRequestRequest(@Size(max = 500) String reason) {}
