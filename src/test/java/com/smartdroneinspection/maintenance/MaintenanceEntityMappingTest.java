package com.smartdroneinspection.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.maintenance.domain.MaintenanceOrder;
import com.smartdroneinspection.maintenance.domain.MaintenanceTicket;
import com.smartdroneinspection.maintenance.domain.MaintenanceWorkLog;
import com.smartdroneinspection.maintenance.domain.enums.MaintenanceOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaintenanceEntityMappingTest {

  @Test
  void maintenanceOrderStatusContainsDirectTransferValues() {
    assertThat(MaintenanceOrderStatus.valueOf("AWAITING_PAYMENT")).isNotNull();
    assertThat(MaintenanceOrderStatus.valueOf("PAID")).isNotNull();
    assertThat(MaintenanceOrderStatus.valueOf("DISPUTED")).isNotNull();
  }

  @Test
  void maintenanceOrderSupportsProviderAndWarrantyAndPaymentFields() {
    MaintenanceOrder order =
        new MaintenanceOrder(
            UUID.randomUUID(),
            "ORD-001",
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            1,
            null,
            "{\"scope\":\"repair\"}",
            BigDecimal.valueOf(1000000),
            "VND",
            "Direct transfer",
            UUID.randomUUID(),
            Instant.now());

    UUID providerId = UUID.randomUUID();
    order.setProviderId(providerId);
    order.setLockedWarrantyDays(180);
    Instant warrantyEnd = Instant.now().plusSeconds(180 * 86400L);
    order.setWarrantyEndDate(warrantyEnd);
    order.setProviderBankAccountNumber("123456789");
    order.setProviderBankName("Vietcombank");
    Instant paidAt = Instant.now();
    order.markPaid(paidAt);

    assertThat(order.getProviderId()).isEqualTo(providerId);
    assertThat(order.getLockedWarrantyDays()).isEqualTo(180);
    assertThat(order.getWarrantyEndDate()).isEqualTo(warrantyEnd);
    assertThat(order.getProviderBankAccountNumber()).isEqualTo("123456789");
    assertThat(order.getProviderBankName()).isEqualTo("Vietcombank");
    assertThat(order.getPaidAt()).isEqualTo(paidAt);
    assertThat(order.getStatus()).isEqualTo(MaintenanceOrderStatus.PAID);
  }

  @Test
  void maintenanceTicketSupportsWarrantyClock() {
    MaintenanceTicket ticket =
        new MaintenanceTicket(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            com.smartdroneinspection.maintenance.domain.enums.MaintenancePriority.HIGH,
            Instant.now().plusSeconds(86400),
            "Repair crack on pier 2");

    Instant acceptedAt = Instant.now();
    ticket.recordAcceptance(acceptedAt, 180);

    assertThat(ticket.getAcceptedAt()).isEqualTo(acceptedAt);
    assertThat(ticket.getWarrantyStartedAt()).isEqualTo(acceptedAt);
    assertThat(ticket.getWarrantyEndsAt()).isEqualTo(acceptedAt.plusSeconds(180 * 86400L));
  }

  @Test
  void maintenanceWorkLogSupportsBeforeAfterEvidencePair() {
    MaintenanceWorkLog log =
        new MaintenanceWorkLog(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.now(),
            BigDecimal.valueOf(100),
            "Crack injected with epoxy",
            "{\"epoxy_kg\": 2}",
            BigDecimal.valueOf(4.5),
            com.smartdroneinspection.maintenance.domain.enums.WorkLogStatus.SUBMITTED);

    UUID beforeId = UUID.randomUUID();
    UUID afterId = UUID.randomUUID();
    log.setEvidencePair(beforeId, afterId);

    assertThat(log.getBeforeEvidenceId()).isEqualTo(beforeId);
    assertThat(log.getAfterEvidenceId()).isEqualTo(afterId);
  }
}
