package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.reportcontext.ReportContextAccess;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.render.PaymentDocumentRenderer;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.api.InspectionReportAcceptancePort;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates the customer payment notice and the paid receipt.
 *
 * <p>The notice is only meaningful once the customer accepted a published report and owes the
 * occurrence fee; the receipt is only meaningful once the payment was verified. Both are derived
 * from stored state, so the document cannot disagree with the payment record. Neither is a VAT
 * invoice, and generating one never changes the payment state.
 */
@Service
public class PaymentDocumentService {

  private static final MediaType PDF = MediaType.APPLICATION_PDF;

  private final InspectionRepository inspections;
  private final InspectionReportRepository reports;
  private final ReportContextAccess reportContextAccess;
  private final Optional<InspectionReportAcceptancePort> acceptancePort;
  private final PaymentDocumentRenderer renderer;
  private final UserAccess userAccess;

  public PaymentDocumentService(
      InspectionRepository inspections,
      InspectionReportRepository reports,
      ReportContextAccess reportContextAccess,
      Optional<InspectionReportAcceptancePort> acceptancePort,
      PaymentDocumentRenderer renderer,
      UserAccess userAccess) {
    this.inspections = inspections;
    this.reports = reports;
    this.reportContextAccess = reportContextAccess;
    this.acceptancePort = acceptancePort;
    this.renderer = renderer;
    this.userAccess = userAccess;
  }

  @Transactional(readOnly = true)
  public ResponseEntity<byte[]> render(UUID actorId, UUID inspectionId) {
    Inspection inspection = requireScopedInspection(actorId, inspectionId);

    if (acceptancePort.isEmpty()) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "PAYMENT_SERVICE_UNAVAILABLE",
          "Payment documents are unavailable because the payment service is not configured");
    }
    InspectionReportAcceptancePort.PaymentStatusDetails status =
        acceptancePort.get().getPaymentStatus(actorId, inspectionId);

    boolean paid = status.paid();
    boolean accepted = status.reportAccepted();
    if (!paid && !accepted) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "PAYMENT_DOCUMENT_NOT_AVAILABLE",
          "A payment document is available only after the customer accepts the published report");
    }

    PaymentDocumentRenderer.Kind kind =
        paid ? PaymentDocumentRenderer.Kind.RECEIPT : PaymentDocumentRenderer.Kind.NOTICE;
    ReportContextAccess.Identity identity =
        reportContextAccess
            .find(inspection.getOwnerCustomerId(), inspection.getAssetId(), inspection.getDroneId())
            .orElse(null);

    String reportReference = latestReportReference(inspectionId);
    String documentRef = "SDI-PAY-" + inspectionId.toString().substring(0, 8).toUpperCase();

    PaymentDocumentRenderer.Details details =
        new PaymentDocumentRenderer.Details(
            kind,
            documentRef,
            reportReference,
            identity == null ? null : assetLabel(identity),
            identity == null ? null : identity.organizationName(),
            status.serviceBaseAmount(),
            status.vatRate(),
            status.vatAmount(),
            status.amount() == null ? null : java.math.BigDecimal.valueOf(status.amount()),
            status.paymentStatus(),
            status.paymentMethod(),
            kind == PaymentDocumentRenderer.Kind.RECEIPT ? paymentReference(status) : null,
            kind == PaymentDocumentRenderer.Kind.RECEIPT
                ? (status.paymentConfirmedAt() != null
                    ? status.paymentConfirmedAt()
                    : java.time.Instant.now())
                : null,
            null,
            status.paymentDueAt(),
            status.paymentDueAt() != null
                && java.time.Instant.now().isAfter(status.paymentDueAt()));

    try {
      byte[] content = renderer.render(details);
      String suffix = kind == PaymentDocumentRenderer.Kind.RECEIPT ? "receipt" : "notice";
      return ResponseEntity.ok()
          .contentType(PDF)
          .contentLength(content.length)
          .header(
              HttpHeaders.CONTENT_DISPOSITION,
              ContentDisposition.attachment()
                  .filename(documentRef + "-" + suffix + ".pdf", StandardCharsets.UTF_8)
                  .build()
                  .toString())
          .body(content);
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "PAYMENT_DOCUMENT_RENDER_UNAVAILABLE",
          "The payment document could not be produced; retry the download later");
    }
  }

  /**
   * Resolves the inspection through a scoped repository query, so a caller with the right role but
   * the wrong organization receives not-found rather than another customer's payment document. A
   * Manager or Admin may read a payment document for review; an Inspector assigned to the mission
   * may read it because it is part of their own payout record.
   */
  private Inspection requireScopedInspection(UUID actorId, UUID inspectionId) {
    UserAccess.ActiveUser actor = userAccess.findActiveUser(actorId).orElseThrow(this::notFound);
    if (actor.hasRole(Roles.ADMIN) || actor.hasRole(Roles.MANAGER)) {
      return inspections.findById(inspectionId).orElseThrow(this::notFound);
    }
    if (actor.hasRole(Roles.INSPECTOR)) {
      return inspections.findAssignedByInspector(inspectionId, actorId).orElseThrow(this::notFound);
    }
    if (actor.ownerCustomerId() == null) {
      throw notFound();
    }
    return inspections
        .findByIdAndOwnerCustomerId(inspectionId, actor.ownerCustomerId())
        .orElseThrow(this::notFound);
  }

  private BusinessException notFound() {
    return new BusinessException(
        HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection not found");
  }

  private String latestReportReference(UUID inspectionId) {
    return reports
        .findFirstByInspectionIdOrderByVersionNoDesc(inspectionId)
        .filter(InspectionReport::isImmutable)
        .map(
            version ->
                "SDI-RPT-"
                    + version.getId().toString().substring(0, 8).toUpperCase()
                    + "-V"
                    + version.getVersionNo())
        .orElse(null);
  }

  private static String assetLabel(ReportContextAccess.Identity identity) {
    if (identity.assetCode() == null || identity.assetCode().isBlank()) {
      return identity.assetName();
    }
    return identity.assetCode()
        + (identity.assetName() == null || identity.assetName().isBlank()
            ? ""
            : " · " + identity.assetName());
  }

  private static String paymentReference(
      InspectionReportAcceptancePort.PaymentStatusDetails status) {
    if (status.orderCode() == null || status.orderCode() <= 0) {
      return null;
    }
    return status.paymentMethod() == null
        ? status.orderCode().toString()
        : status.paymentMethod() + " #" + status.orderCode();
  }
}
