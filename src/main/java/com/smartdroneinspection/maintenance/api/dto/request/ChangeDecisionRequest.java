package com.smartdroneinspection.maintenance.api.dto.request;

/** MF4-13: the decision on a change proposal. Rejection and return both require a reason. */
public record ChangeDecisionRequest(String reason) {}
