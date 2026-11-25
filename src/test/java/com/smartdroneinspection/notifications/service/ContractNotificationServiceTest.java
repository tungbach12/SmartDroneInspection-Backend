package com.smartdroneinspection.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.contracts.events.CustomerReportAcceptedEvent;
import com.smartdroneinspection.contracts.events.InspectorPayoutSettledEvent;
import com.smartdroneinspection.contracts.events.InspectorReceivableRecoveryEvent;
import com.smartdroneinspection.contracts.events.OccurrenceRefundEvent;
import com.smartdroneinspection.contracts.events.ServiceQuoteAcceptedEvent;
import com.smartdroneinspection.contracts.events.ServiceQuoteSavedEvent;
import com.smartdroneinspection.contracts.events.ServiceRequestManagerChangedEvent;
import com.smartdroneinspection.contracts.events.SignedContractUploadedEvent;
import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.users.UserAccess.ActiveUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ContractNotificationServiceTest {

  private NotificationRepository notificationRepository;
  private UserAccess userAccess;
  private ContractNotificationService service;

  @BeforeEach
  void setUp() {
    notificationRepository = mock(NotificationRepository.class);
    userAccess = mock(UserAccess.class);
    service = new ContractNotificationService(notificationRepository, userAccess);
  }

  @Test
  void managerChangedNotifiesNewAndPreviousManager() {
    UUID requestId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID prevMgr = UUID.randomUUID();
    UUID newMgr = UUID.randomUUID();

    service.recordManagerChanged(
        new ServiceRequestManagerChangedEvent(
            requestId, customerId, prevMgr, newMgr, "REASSIGNED", "Bridge survey", Instant.now()));

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, times(2)).saveAndFlush(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(Notification::getRecipientUserId, Notification::getEventType)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(newMgr, "SERVICE_REQUEST_ASSIGNED"),
            org.assertj.core.groups.Tuple.tuple(prevMgr, "SERVICE_REQUEST_UNASSIGNED"));
  }

  @Test
  void quoteSavedAcceptedAndContractUploadedSendBilingualNotifications() {
    UUID requestId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    when(userAccess.findActiveUser(managerId))
        .thenReturn(Optional.of(new ActiveUser(managerId, Set.of("MANAGER"), "Mgr")));

    service.recordQuoteSaved(
        new ServiceQuoteSavedEvent(
            requestId,
            customerId,
            "Roof scan",
            false,
            new BigDecimal("5000000"),
            "VND",
            Instant.now()));
    service.recordQuoteAccepted(
        new ServiceQuoteAcceptedEvent(
            requestId, customerId, managerId, "Roof scan", Instant.now()));
    service.recordSignedContractUploaded(
        new SignedContractUploadedEvent(requestId, customerId, "Roof scan", Instant.now()));

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, times(3)).saveAndFlush(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(Notification::getEventType)
        .containsExactly("QUOTE_ISSUED", "QUOTE_ACCEPTED", "SIGNED_CONTRACT_UPLOADED");
    assertThat(captor.getAllValues()).allSatisfy(n -> assertThat(n.getTitle()).contains(" | "));
  }

  @Test
  void customerReportAcceptedPayoutSettledRefundAndRecoveryNotifyRecipients() {
    UUID requestId = UUID.randomUUID();
    UUID occurrenceId = UUID.randomUUID();
    UUID inspectionId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    UUID refundId = UUID.randomUUID();
    UUID receivableId = UUID.randomUUID();

    when(userAccess.findActiveUser(managerId))
        .thenReturn(Optional.of(new ActiveUser(managerId, Set.of("MANAGER"), "Mgr")));

    service.recordCustomerReportAccepted(
        new CustomerReportAcceptedEvent(
            requestId,
            occurrenceId,
            inspectionId,
            customerId,
            managerId,
            Instant.now().plusSeconds(86400),
            Instant.now()));
    service.recordInspectorPayoutSettled(
        new InspectorPayoutSettledEvent(
            occurrenceId,
            requestId,
            inspectionId,
            inspectorId,
            managerId,
            "PAY-01",
            new BigDecimal("3500000"),
            "SEPAY",
            true,
            Instant.now()));
    service.recordOccurrenceRefund(
        new OccurrenceRefundEvent(
            refundId,
            occurrenceId,
            requestId,
            inspectionId,
            customerId,
            inspectorId,
            managerId,
            "INITIATED",
            new BigDecimal("1000000"),
            "REF-1",
            "Weather",
            Instant.now()));
    service.recordInspectorReceivableRecovery(
        new InspectorReceivableRecoveryEvent(
            receivableId,
            occurrenceId,
            requestId,
            inspectorId,
            new BigDecimal("200000"),
            "REC-1",
            Instant.now()));

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository, times(6)).saveAndFlush(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(Notification::getEventType)
        .containsExactlyInAnyOrder(
            "REPORT_ACCEPTED_BY_CUSTOMER",
            "PAYOUT_SETTLED",
            "PAYOUT_SETTLED",
            "REFUND_INITIATED",
            "REFUND_INITIATED",
            "RECEIVABLE_RECOVERY_RECORDED");
  }
}
