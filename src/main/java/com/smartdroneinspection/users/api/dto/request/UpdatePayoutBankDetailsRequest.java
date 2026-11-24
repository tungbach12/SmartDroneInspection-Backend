package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePayoutBankDetailsRequest(
    @NotBlank @Size(max = 120) String bankName,
    @NotBlank @Size(max = 64) String accountNumber,
    @NotBlank @Size(max = 200) String accountHolder,
    @Size(max = 200) String bankBranch) {}
