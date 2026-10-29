package com.smartdroneinspection.users.api.dto.response;

public record InspectorPartnerApprovalResponse(
    InspectorPartnerApplicationResponse application, String temporaryPassword) {}
