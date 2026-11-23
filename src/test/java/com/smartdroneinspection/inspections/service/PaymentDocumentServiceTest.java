package com.smartdroneinspection.inspections.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.assets.reportcontext.ReportContextAccess;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.render.PaymentDocumentRenderer;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.api.InspectionReportAcceptancePort;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Covers the tenant and assignment scoping of the payment-document endpoint, plus the state gate
 * that decides between "not available yet", notice, and receipt.
 *
 * <p>The renderer is mocked: what is under test here is who may read the document and which kind is
 * produced, not the PDF layout (covered by {@code PaymentDocumentRendererTest}).
 */
@ExtendWith(MockitoExtension.class)
class PaymentDocumentServiceTest {

  private static final UUID INSPECTION_ID = UUID.randomUUID();
  private static final UUID OWNER_CUSTOMER_ID = UUID.randomUUID();
  private static final UUID FOREIGN_CUSTOMER_ID = UUID.randomUUID();
  private static final UUID ASSIGNED_INSPECTOR_ID = UUID.randomUUID();
  private static final UUID OTHER_INSPECTOR_ID = UUID.randomUUID();
  private static final UUID MANAGER_ID = UUID.randomUUID();
  private static final UUID ADMIN_ID = UUID.randomUUID();
  private static final UUID ASSET_ID = UUID.randomUUID();
  private static final UUID DRONE_ID = UUID.randomUUID();
  private static final Instant ACCEPTED_AT = Instant.parse("2026-11-10T03:00:00Z");
  private static final Instant DUE_AT = Instant.parse("2026-11-25T03:00:00Z");
  private static final byte[] PDF_BYTES = "%PDF-1.7 fake".getBytes();

  @Mock private InspectionRepository inspections;
  @Mock private InspectionReportRepository reports;
  @Mock private ReportContextAccess reportContextAccess;
  @Mock private InspectionReportAcceptancePort acceptancePort;
  @Mock private PaymentDocumentRenderer renderer;
  @Mock private UserAccess userAccess;

  private PaymentDocumentService service;

  @BeforeEach
  void setUp() {
    service =
        new PaymentDocumentService(
            inspections,
            reports,
            reportContextAccess,
            Optional.of(acceptancePort),
            renderer,
            userAccess);
  }

  // --- organization and assignment scope -------------------------------------------------

  @Test
  @DisplayName("A customer from another organization gets not-found, never another org's notice")
  void foreignOrganizationCustomerGetsNotFound() {
    when(userAccess.findActiveUser(FOREIGN_CUSTOMER_ID))
        .thenReturn(Optional.of(activeUser(FOREIGN_CUSTOMER_ID, Roles.CUSTOMER)));
    when(inspections.findByIdAndOwnerCustomerId(INSPECTION_ID, FOREIGN_CUSTOMER_ID))
        .thenReturn(Optional.empty());

    assertNotFound(() -> service.render(FOREIGN_CUSTOMER_ID, INSPECTION_ID));

    verify(acceptancePort, never()).getPaymentStatus(any(), any());
    verifyNoInteractions(renderer);
  }

  @Test
  @DisplayName("An Inspector who is not assigned to the mission gets not-found")
  void unassignedInspectorGetsNotFound() {
    when(userAccess.findActiveUser(OTHER_INSPECTOR_ID))
        .thenReturn(Optional.of(activeUser(OTHER_INSPECTOR_ID, Roles.INSPECTOR)));
    when(inspections.findAssignedByInspector(INSPECTION_ID, OTHER_INSPECTOR_ID))
        .thenReturn(Optional.empty());

    assertNotFound(() -> service.render(OTHER_INSPECTOR_ID, INSPECTION_ID));

    // The unscoped finder must not be used as a fallback, or assignment would not gate anything.
    verify(inspections, never()).findById(any());
    verifyNoInteractions(renderer);
  }

  @Test
  @DisplayName("An unknown or deactivated actor gets not-found rather than leaking existence")
  void unknownActorGetsNotFound() {
    UUID gone = UUID.randomUUID();
    when(userAccess.findActiveUser(gone)).thenReturn(Optional.empty());

    assertNotFound(() -> service.render(gone, INSPECTION_ID));

    verifyNoInteractions(inspections, acceptancePort, renderer);
  }

  @Test
  @DisplayName("An authenticated actor without an owner scope gets not-found")
  void actorWithoutOwnerScopeGetsNotFound() {
    // Not ADMIN, MANAGER or INSPECTOR, and without CUSTOMER, so ownerCustomerId() is null.
    UUID engineer = UUID.randomUUID();
    when(userAccess.findActiveUser(engineer))
        .thenReturn(
            Optional.of(new UserAccess.ActiveUser(engineer, Set.of("MAINTENANCE_ENGINEER"))));

    assertNotFound(() -> service.render(engineer, INSPECTION_ID));

    verifyNoInteractions(renderer);
  }

  @Test
  @DisplayName("An assigned Inspector may read the document for their own payout record")
  void assignedInspectorMayRead() throws Exception {
    stubRenderedPdf();
    when(userAccess.findActiveUser(ASSIGNED_INSPECTOR_ID))
        .thenReturn(Optional.of(activeUser(ASSIGNED_INSPECTOR_ID, Roles.INSPECTOR)));
    when(inspections.findAssignedByInspector(INSPECTION_ID, ASSIGNED_INSPECTOR_ID))
        .thenReturn(Optional.of(inspection()));
    when(acceptancePort.getPaymentStatus(ASSIGNED_INSPECTOR_ID, INSPECTION_ID))
        .thenReturn(status(true, true, "PAID", 9901L, "VNPAY", DUE_AT));

    ResponseEntity<byte[]> response = service.render(ASSIGNED_INSPECTOR_ID, INSPECTION_ID);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(captureDetails().kind()).isEqualTo(PaymentDocumentRenderer.Kind.RECEIPT);
  }

  @Test
  @DisplayName("A Manager or Admin may read any inspection's payment document for review")
  void managerAndAdminMayReadAnyInspection() throws Exception {
    stubRenderedPdf();
    when(userAccess.findActiveUser(MANAGER_ID))
        .thenReturn(Optional.of(activeUser(MANAGER_ID, Roles.MANAGER)));
    when(userAccess.findActiveUser(ADMIN_ID))
        .thenReturn(Optional.of(activeUser(ADMIN_ID, Roles.ADMIN)));
    when(inspections.findById(INSPECTION_ID)).thenReturn(Optional.of(inspection()));
    when(acceptancePort.getPaymentStatus(any(), eq(INSPECTION_ID)))
        .thenReturn(status(true, false, "PAYMENT_DUE", null, null, DUE_AT));

    assertThat(service.render(MANAGER_ID, INSPECTION_ID).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(service.render(ADMIN_ID, INSPECTION_ID).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  // --- availability and kind --------------------------------------------------------------

  @Test
  @DisplayName("Without a configured payment service the document is unavailable, not an error 500")
  void unavailableWhenPaymentServiceNotConfigured() {
    PaymentDocumentService withoutPort =
        new PaymentDocumentService(
            inspections, reports, reportContextAccess, Optional.empty(), renderer, userAccess);
    when(userAccess.findActiveUser(OWNER_CUSTOMER_ID))
        .thenReturn(Optional.of(activeUser(OWNER_CUSTOMER_ID, Roles.CUSTOMER)));
    when(inspections.findByIdAndOwnerCustomerId(INSPECTION_ID, OWNER_CUSTOMER_ID))
        .thenReturn(Optional.of(inspection()));

    assertThatThrownBy(() -> withoutPort.render(OWNER_CUSTOMER_ID, INSPECTION_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> {
              assertThat(ex.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
              assertThat(ex.code()).isEqualTo("PAYMENT_SERVICE_UNAVAILABLE");
            });

    verifyNoInteractions(renderer);
  }

  @Test
  @DisplayName("Before acceptance and before payment there is no document yet (409)")
  void conflictBeforeAcceptance() {
    when(userAccess.findActiveUser(OWNER_CUSTOMER_ID))
        .thenReturn(Optional.of(activeUser(OWNER_CUSTOMER_ID, Roles.CUSTOMER)));
    when(inspections.findByIdAndOwnerCustomerId(INSPECTION_ID, OWNER_CUSTOMER_ID))
        .thenReturn(Optional.of(inspection()));
    when(acceptancePort.getPaymentStatus(OWNER_CUSTOMER_ID, INSPECTION_ID))
        .thenReturn(status(false, false, "PENDING", null, null, null));

    assertThatThrownBy(() -> service.render(OWNER_CUSTOMER_ID, INSPECTION_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> {
              assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(ex.code()).isEqualTo("PAYMENT_DOCUMENT_NOT_AVAILABLE");
            });

    verifyNoInteractions(renderer);
  }

  @Test
  @DisplayName("An accepted but unpaid occurrence produces a notice carrying the due date")
  void acceptedUnpaidProducesNotice() throws Exception {
    stubRenderedPdf();
    stubOwningCustomer();
    when(acceptancePort.getPaymentStatus(OWNER_CUSTOMER_ID, INSPECTION_ID))
        .thenReturn(status(true, false, "PAYMENT_DUE", null, null, DUE_AT));

    ResponseEntity<byte[]> response = service.render(OWNER_CUSTOMER_ID, INSPECTION_ID);

    PaymentDocumentRenderer.Details details = captureDetails();
    assertThat(details.kind()).isEqualTo(PaymentDocumentRenderer.Kind.NOTICE);
    assertThat(details.dueAt()).isEqualTo(DUE_AT);
    assertThat(details.paidAt()).isNull();
    assertThat(details.paymentReference()).isNull();
    assertThat(details.totalAmount()).isEqualByComparingTo("1100000");
    assertThat(details.organizationLabel()).isEqualTo("Acme Towers");
    assertThat(details.assetLabel()).isEqualTo("AST-001 · Rooftop Tower");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName(
      "A verified payment produces a receipt with method, transaction reference, and confirmed timestamp")
  void paidProducesReceipt() throws Exception {
    stubRenderedPdf();
    stubOwningCustomer();
    Instant confirmedAt = Instant.parse("2026-11-12T08:15:30Z");
    when(acceptancePort.getPaymentStatus(OWNER_CUSTOMER_ID, INSPECTION_ID))
        .thenReturn(
            new InspectionReportAcceptancePort.PaymentStatusDetails(
                true,
                true,
                "PAID",
                9901L,
                1100000L,
                null,
                new BigDecimal("1000000.00"),
                new BigDecimal("10.00"),
                new BigDecimal("100000.00"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "VNPAY",
                DUE_AT,
                confirmedAt));

    service.render(OWNER_CUSTOMER_ID, INSPECTION_ID);

    PaymentDocumentRenderer.Details details = captureDetails();
    assertThat(details.kind()).isEqualTo(PaymentDocumentRenderer.Kind.RECEIPT);
    assertThat(details.paymentMethod()).isEqualTo("VNPAY");
    assertThat(details.paymentReference()).isEqualTo("VNPAY #9901");
    assertThat(details.paidAt()).isEqualTo(confirmedAt);
  }

  @Test
  @DisplayName("A paid occurrence without a gateway order code has no fabricated reference")
  void paidWithoutOrderCodeHasNoReference() throws Exception {
    stubRenderedPdf();
    stubOwningCustomer();
    when(acceptancePort.getPaymentStatus(OWNER_CUSTOMER_ID, INSPECTION_ID))
        .thenReturn(status(true, true, "PAID", null, "MANUAL", null));

    service.render(OWNER_CUSTOMER_ID, INSPECTION_ID);

    assertThat(captureDetails().paymentReference()).isNull();
  }

  // --- response shape and failure mapping --------------------------------------------------

  @Test
  @DisplayName("The response is an attachment PDF whose name distinguishes notice from receipt")
  void responseIsAttachmentPdf() throws Exception {
    stubRenderedPdf();
    stubOwningCustomer();
    when(acceptancePort.getPaymentStatus(OWNER_CUSTOMER_ID, INSPECTION_ID))
        .thenReturn(status(true, false, "PAYMENT_DUE", null, null, DUE_AT));

    ResponseEntity<byte[]> response = service.render(OWNER_CUSTOMER_ID, INSPECTION_ID);

    assertThat(response.getHeaders().getContentType())
        .isEqualTo(org.springframework.http.MediaType.APPLICATION_PDF);
    assertThat(response.getHeaders().getContentLength()).isEqualTo(PDF_BYTES.length);
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .contains("attachment")
        .contains("-notice.pdf");
    assertThat(response.getBody()).isEqualTo(PDF_BYTES);
  }

  @Test
  @DisplayName("A rendering failure is reported as retryable unavailability, never as success")
  void renderFailureBecomesServiceUnavailable() throws Exception {
    stubOwningCustomer();
    when(acceptancePort.getPaymentStatus(OWNER_CUSTOMER_ID, INSPECTION_ID))
        .thenReturn(status(true, false, "PAYMENT_DUE", null, null, DUE_AT));
    when(renderer.render(any())).thenThrow(new IOException("font cache missing"));

    assertThatThrownBy(() -> service.render(OWNER_CUSTOMER_ID, INSPECTION_ID))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> {
              assertThat(ex.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
              assertThat(ex.code()).isEqualTo("PAYMENT_DOCUMENT_RENDER_UNAVAILABLE");
            });
  }

  // --- helpers ---------------------------------------------------------------------------

  private void stubOwningCustomer() {
    when(userAccess.findActiveUser(OWNER_CUSTOMER_ID))
        .thenReturn(Optional.of(activeUser(OWNER_CUSTOMER_ID, Roles.CUSTOMER)));
    when(inspections.findByIdAndOwnerCustomerId(INSPECTION_ID, OWNER_CUSTOMER_ID))
        .thenReturn(Optional.of(inspection()));
    when(reports.findFirstByInspectionIdOrderByVersionNoDesc(INSPECTION_ID))
        .thenReturn(Optional.empty());
    when(reportContextAccess.find(OWNER_CUSTOMER_ID, ASSET_ID, DRONE_ID))
        .thenReturn(
            Optional.of(
                new ReportContextAccess.Identity(
                    "Acme Towers",
                    "AST-001",
                    "Rooftop Tower",
                    "District 1",
                    "BUILDING",
                    null,
                    null,
                    null)));
  }

  private void stubRenderedPdf() throws IOException {
    when(renderer.render(any())).thenReturn(PDF_BYTES);
  }

  private PaymentDocumentRenderer.Details captureDetails() throws IOException {
    ArgumentCaptor<PaymentDocumentRenderer.Details> captor =
        ArgumentCaptor.forClass(PaymentDocumentRenderer.Details.class);
    verify(renderer).render(captor.capture());
    return captor.getValue();
  }

  private static Inspection inspection() {
    return new Inspection(OWNER_CUSTOMER_ID, ASSET_ID, ASSIGNED_INSPECTOR_ID, DRONE_ID, null, null);
  }

  private static UserAccess.ActiveUser activeUser(UUID id, String role) {
    return new UserAccess.ActiveUser(id, Set.of(role));
  }

  private static void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> {
              assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(ex.code()).isEqualTo("INSPECTION_NOT_FOUND");
            });
  }

  /**
   * The status projection is wide; this keeps the call sites readable while still exercising the
   * fields the document actually prints.
   */
  private static InspectionReportAcceptancePort.PaymentStatusDetails status(
      boolean accepted,
      boolean paid,
      String paymentStatus,
      Long orderCode,
      String method,
      Instant dueAt) {
    return new InspectionReportAcceptancePort.PaymentStatusDetails(
        accepted,
        paid,
        paymentStatus,
        orderCode,
        1100000L,
        null,
        new BigDecimal("1000000.00"),
        new BigDecimal("10.00"),
        new BigDecimal("100000.00"),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        method,
        dueAt);
  }
}
