package com.smartdroneinspection.maintenance.api.dto.request;

import com.smartdroneinspection.maintenance.domain.enums.AssessmentMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateMaintenanceAssessmentQuotationRequest(
    @NotNull AssessmentMode assessmentMode,
    @NotBlank @Size(max = 4000) String requiredWork,
    @NotBlank String materialsEstimate,
    @NotNull @DecimalMin("0.01") BigDecimal laborHoursEstimate,
    @NotNull @DecimalMin("0.01") BigDecimal durationHoursEstimate,
    @Size(max = 4000) String riskNotes,
    @Size(max = 4000) String assumptions,
    @NotNull @DecimalMin("0.0") BigDecimal subtotal,
    @NotNull @DecimalMin("0.0") BigDecimal taxAmount,
    @NotNull @DecimalMin("0.0") BigDecimal totalAmount,
    @NotBlank String pricingDetails,
    @NotBlank String scopeSnapshot,
    @NotBlank @Size(max = 2000) String paymentTerms,
    @NotNull Integer lockedWarrantyDays) {}
