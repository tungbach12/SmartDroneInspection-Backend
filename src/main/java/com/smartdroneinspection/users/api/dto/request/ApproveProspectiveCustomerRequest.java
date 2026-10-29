package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.Size;

public record ApproveProspectiveCustomerRequest(@Size(max = 2000) String reviewNote) {}
