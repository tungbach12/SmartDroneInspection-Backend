package com.smartdroneinspection.maintenance.api.dto.request;

/** MF4-07/08: a return or rejection always carries the reason the team must act on. */
public record EstimateDecisionRequest(String reason) {}
