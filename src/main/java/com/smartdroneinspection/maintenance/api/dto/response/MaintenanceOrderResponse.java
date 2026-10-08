package com.smartdroneinspection.maintenance.api.dto.response;

import com.smartdroneinspection.maintenance.domain.enums.MaintenanceOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MaintenanceOrderResponse(
    UUID id,
    UUID orderSeriesId,
    String orderNumber,
    UUID maintenanceTicketId,
    UUID approvedQuotationId,
    UUID changeRequestId,
    int versionNumber,
    UUID previousVersionId,
    String scopeSnapshot,
    BigDecimal approvedAmount,
    String currency,
    String paymentTerms,
    MaintenanceOrderStatus status,
    UUID approvedByUserId,
    Instant approvedAt,
    UUID providerId,
    Integer lockedWarrantyDays,
    Instant warrantyEndDate,
    Instant paymentInvoiceIssuedAt,
    Instant paidAt,
    String providerBankAccountNumber,
    String providerBankName,
    Instant startedAt,
    Instant completedAt,
    Instant createdAt) {}
