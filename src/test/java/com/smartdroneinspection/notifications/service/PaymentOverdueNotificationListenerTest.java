package com.smartdroneinspection.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.contracts.events.PaymentOverdueEvent;
import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.domain.enums.NotificationChannel;
import com.smartdroneinspection.notifications.repository.NotificationRepository;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Covers the overdue reminder fan-out: one notification for the owning customer and one per active
 * manager, each carrying a deduplication key so a repeated scan cannot notify twice.
 */
@ExtendWith(MockitoExtension.class)
class PaymentOverdueNotificationListenerTest {

  private static final UUID OCCURRENCE_ID = UUID.randomUUID();
  private static final UUID REQUEST_ID = UUID.randomUUID();
  private static final UUID CUSTOMER_ID = UUID.randomUUID();
  private static final Instant DUE_AT = Instant.parse("2026-11-10T03:00:00Z");

  @Mock private NotificationRepository notifications;
  @Mock private UserAccess userAccess;

  private PaymentOverdueNotificationListener listener;

  @BeforeEach
  void setUp() {
    listener = new PaymentOverdueNotificationListener(notifications, userAccess);
  }

  @Test
  @DisplayName("The owning customer and every active manager each receive one reminder")
  void notifiesCustomerAndEveryManager() {
    UUID managerA = UUID.randomUUID();
    UUID managerB = UUID.randomUUID();
    when(userAccess.listActiveManagers())
        .thenReturn(
            List.of(
                new UserAccess.ActiveUser(managerA, Set.of("MANAGER")),
                new UserAccess.ActiveUser(managerB, Set.of("MANAGER"))));

    listener.onPaymentOverdue(event(CUSTOMER_ID));

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notifications, times(3)).saveAndFlush(captor.capture());
    List<Notification> saved = captor.getAllValues();

    assertThat(saved)
        .extracting(Notification::getRecipientUserId)
        .containsExactlyInAnyOrder(CUSTOMER_ID, managerA, managerB);
    assertThat(saved)
        .allSatisfy(
            n -> {
              assertThat(n.getEventType()).isEqualTo("PAYMENT_OVERDUE");
              assertThat(n.getChannel()).isEqualTo(NotificationChannel.IN_APP);
            });
  }

  @Test
  @DisplayName("Each recipient's target path points at their own surface")
  void targetPathsDifferPerRole() {
    UUID manager = UUID.randomUUID();
    when(userAccess.listActiveManagers())
        .thenReturn(List.of(new UserAccess.ActiveUser(manager, Set.of("MANAGER"))));

    listener.onPaymentOverdue(event(CUSTOMER_ID));

    verify(notifications, times(1))
        .saveAndFlush(
            argThat(
                n ->
                    n.getRecipientUserId().equals(CUSTOMER_ID)
                        && n.getTargetPath().equals("/client/inspections/" + OCCURRENCE_ID)));
    verify(notifications, times(1))
        .saveAndFlush(
            argThat(
                n ->
                    n.getRecipientUserId().equals(manager)
                        && n.getTargetPath()
                            .equals("/manager/inspections/" + OCCURRENCE_ID + "/finance")));
  }

  @Test
  @DisplayName("The deduplication key is unique per recipient so a re-scan cannot double-notify")
  void deduplicationKeyIsPerRecipient() {
    UUID manager = UUID.randomUUID();
    when(userAccess.listActiveManagers())
        .thenReturn(List.of(new UserAccess.ActiveUser(manager, Set.of("MANAGER"))));

    listener.onPaymentOverdue(event(CUSTOMER_ID));

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notifications, times(2)).saveAndFlush(captor.capture());

    assertThat(captor.getAllValues())
        .extracting(Notification::getDeduplicationKey)
        .containsExactlyInAnyOrder(
            "PAYMENT_OVERDUE:" + OCCURRENCE_ID + ":" + CUSTOMER_ID,
            "PAYMENT_OVERDUE:" + OCCURRENCE_ID + ":" + manager);
  }

  @Test
  @DisplayName("A duplicate blocked by the deduplication constraint is swallowed, not propagated")
  void duplicateConstraintViolationIsIgnored() {
    UUID manager = UUID.randomUUID();
    when(userAccess.listActiveManagers())
        .thenReturn(List.of(new UserAccess.ActiveUser(manager, Set.of("MANAGER"))));
    when(notifications.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("duplicate key"));

    // The listener must not rethrow: a second scan is a normal, expected no-op.
    listener.onPaymentOverdue(event(CUSTOMER_ID));

    verify(notifications, times(2)).saveAndFlush(any());
  }

  @Test
  @DisplayName("The reminder prints the payment deadline in Vietnam time")
  void bodyMentionsTheDueDate() {
    when(userAccess.listActiveManagers()).thenReturn(List.of());

    listener.onPaymentOverdue(event(CUSTOMER_ID));

    verify(notifications).saveAndFlush(argThat(n -> n.getBody().contains("payment was due")));
  }

  @Test
  @DisplayName("An occasion with no due date omits the deadline clause rather than printing null")
  void missingDueDateOmitsTheClause() {
    when(userAccess.listActiveManagers()).thenReturn(List.of());

    listener.onPaymentOverdue(
        new PaymentOverdueEvent(OCCURRENCE_ID, REQUEST_ID, CUSTOMER_ID, null, Instant.now()));

    verify(notifications)
        .saveAndFlush(
            argThat(
                n -> n.getBody().contains("has not been paid") && !n.getBody().contains("null")));
  }

  @Test
  @DisplayName("An occurrence without an owning customer still notifies the managers")
  void missingOwnerStillNotifiesManagers() {
    UUID manager = UUID.randomUUID();
    when(userAccess.listActiveManagers())
        .thenReturn(List.of(new UserAccess.ActiveUser(manager, Set.of("MANAGER"))));

    listener.onPaymentOverdue(
        new PaymentOverdueEvent(OCCURRENCE_ID, REQUEST_ID, null, DUE_AT, Instant.now()));

    verify(notifications, times(1)).saveAndFlush(any());
    verify(notifications).saveAndFlush(argThat(n -> n.getRecipientUserId().equals(manager)));
  }

  @Test
  @DisplayName("A manager projection without an id is skipped instead of writing a null recipient")
  void managerWithoutIdIsSkipped() {
    UUID manager = UUID.randomUUID();
    when(userAccess.listActiveManagers())
        .thenReturn(
            List.of(
                new UserAccess.ActiveUser(null, Set.of("MANAGER")),
                new UserAccess.ActiveUser(manager, Set.of("MANAGER"))));

    listener.onPaymentOverdue(event(CUSTOMER_ID));

    verify(notifications, times(2)).saveAndFlush(any()); // customer + the manager with an id
    verify(notifications, never()).saveAndFlush(argThat(n -> n.getRecipientUserId() == null));
  }

  @Test
  @DisplayName("A null or occurrence-less event is ignored without touching storage")
  void nullEventIsIgnored() {
    listener.onPaymentOverdue(null);
    listener.onPaymentOverdue(
        new PaymentOverdueEvent(null, REQUEST_ID, CUSTOMER_ID, DUE_AT, Instant.now()));

    verifyNoInteractions(notifications, userAccess);
  }

  private static PaymentOverdueEvent event(UUID ownerCustomerId) {
    return new PaymentOverdueEvent(
        OCCURRENCE_ID, REQUEST_ID, ownerCustomerId, DUE_AT, Instant.now());
  }
}
