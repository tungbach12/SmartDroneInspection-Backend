package com.smartdroneinspection.users.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.shared.storage.EvidenceUploadValidator;
import com.smartdroneinspection.users.domain.InspectorProfile;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.security.AuthCrypto;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class InspectorPartnerLicenseDocumentService {

  private static final Pattern LICENSE_KEY =
      Pattern.compile(
          "inspector-partner-applications/licenses/[A-Za-z0-9][A-Za-z0-9_-]{0,200}\\.(pdf|png|jpg|webp)");

  private static final Pattern CONTRACT_KEY =
      Pattern.compile(
          "inspector-partner-applications/contracts/[A-Za-z0-9][A-Za-z0-9_-]{0,200}\\.(pdf|png|jpg|webp)");

  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

  private final Optional<EvidenceObjectStore> objectStore;

  public InspectorPartnerLicenseDocumentService(Optional<EvidenceObjectStore> objectStore) {
    this.objectStore = objectStore;
  }

  public UploadResponse upload(MultipartFile file) {
    var validated = EvidenceUploadValidator.validate(file);
    String key =
        "inspector-partner-applications/licenses/" + UUID.randomUUID() + validated.extension();
    try {
      requireObjectStore()
          .put(
              key,
              validated.contentType(),
              validated.content().length,
              new ByteArrayInputStream(validated.content()));
      return new UploadResponse(key);
    } catch (IOException ex) {
      throw storageUnavailable();
    }
  }

  public ContractUploadResponse uploadContract(MultipartFile file) {
    var validated = EvidenceUploadValidator.validate(file);
    String sha256Hex = AuthCrypto.sha256Hex(validated.content());
    String key =
        "inspector-partner-applications/contracts/" + UUID.randomUUID() + validated.extension();
    try {
      requireObjectStore()
          .put(
              key,
              validated.contentType(),
              validated.content().length,
              new ByteArrayInputStream(validated.content()));
      return new ContractUploadResponse(key, sha256Hex);
    } catch (IOException ex) {
      throw storageUnavailable();
    }
  }

  public void validateReference(String objectKey) {
    if (objectKey == null || !LICENSE_KEY.matcher(objectKey).matches()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_REFERENCE", "Invalid license file key");
    }
  }

  public String uploadApplicationLicenseScan(MultipartFile file) {
    var validated = EvidenceUploadValidator.validate(file);
    String key =
        "inspector-partner-applications/pending/" + UUID.randomUUID() + validated.extension();
    try {
      requireObjectStore()
          .put(
              key,
              validated.contentType(),
              validated.content().length,
              new ByteArrayInputStream(validated.content()));
      return key;
    } catch (IOException ex) {
      throw storageUnavailable();
    }
  }

  public void validateApplicationLicenseReference(String objectKey) {
    if (objectKey == null
        || !objectKey.matches(
            "inspector-partner-applications/pending/[A-Za-z0-9_-]{1,200}\\.(pdf|png|jpg|webp)")) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_REFERENCE", "Invalid application license scan key");
    }
  }

  public InputStreamContent openApplicationLicenseScan(String objectKey) {
    validateApplicationLicenseReference(objectKey);
    String extension = objectKey.substring(objectKey.lastIndexOf('.') + 1);
    try {
      return new InputStreamContent(
          EvidenceUploadValidator.contentType(extension), requireObjectStore().open(objectKey));
    } catch (IOException ex) {
      throw storageUnavailable();
    }
  }

  public void deleteApplicationLicenseScan(String objectKey) throws IOException {
    if (objectKey == null || objectKey.isBlank()) return;
    validateApplicationLicenseReference(objectKey);
    requireObjectStore().delete(objectKey);
  }

  public InputStreamContent open(String objectKey) {
    if (objectKey == null || !LICENSE_KEY.matcher(objectKey).matches()) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "License document not found");
    }
    String extension = objectKey.substring(objectKey.lastIndexOf('.') + 1);
    try {
      return new InputStreamContent(
          EvidenceUploadValidator.contentType(extension), requireObjectStore().open(objectKey));
    } catch (IOException ex) {
      throw storageUnavailable();
    }
  }

  public InputStreamContent openContract(String objectKey) {
    if (objectKey == null || !CONTRACT_KEY.matcher(objectKey).matches()) {
      throw new BusinessException(
          HttpStatus.NOT_FOUND, "NOT_FOUND", "Partner contract document not found");
    }
    String extension = objectKey.substring(objectKey.lastIndexOf('.') + 1);
    try {
      return new InputStreamContent(
          EvidenceUploadValidator.contentType(extension), requireObjectStore().open(objectKey));
    } catch (IOException ex) {
      throw storageUnavailable();
    }
  }

  public byte[] renderDraftPartnerContractPdf(User user, InspectorProfile profile) {
    String contractCode =
        "HD-DT-"
            + user.getId().toString().substring(0, 8).toUpperCase()
            + "-"
            + LocalDate.now().getYear();
    String today = DATE_FMT.format(LocalDate.now().atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")));
    String fullName = escapeXml(user.getFullName());
    String email = escapeXml(user.getEmail());
    String phone =
        escapeXml(
            user.getPhoneNumber() != null
                ? user.getPhoneNumber()
                : "........................................");
    String citizenId =
        escapeXml(
            profile.getCitizenId() != null
                ? profile.getCitizenId()
                : "........................................");
    String address =
        escapeXml(
            profile.getAddress() != null
                ? profile.getAddress()
                : "........................................................");
    String licenseNo =
        escapeXml(
            profile.getPilotLicenseNumber() != null
                ? profile.getPilotLicenseNumber()
                : "........................................");
    String licenseClass =
        escapeXml(
            profile.getPilotLicenseClass() != null
                ? profile.getPilotLicenseClass()
                : "UAV / Drone Inspection");
    String licenseExp =
        profile.getPilotLicenseExpiresAt() != null
            ? DATE_FMT.format(profile.getPilotLicenseExpiresAt())
            : "....................";

    String html =
        """
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml">
        <head>
          <meta charset="UTF-8" />
          <style>
            @page { size: A4; margin: 20mm 18mm; }
            body { font-family: sans-serif; font-size: 11pt; line-height: 1.5; color: #111827; }
            .header { text-align: center; margin-bottom: 18px; }
            .header h2 { margin: 0; font-size: 13pt; text-transform: uppercase; }
            .header h1 { margin: 8px 0 4px 0; font-size: 15pt; text-transform: uppercase; color: #0f172a; }
            .meta { text-align: center; font-size: 10pt; color: #374151; margin-bottom: 16px; }
            .section-title { font-weight: bold; font-size: 11.5pt; margin-top: 14px; margin-bottom: 6px; text-transform: uppercase; color: #1e293b; }
            .box { border: 1px solid #cbd5e1; padding: 10px 14px; margin-bottom: 12px; background-color: #f8fafc; }
            p { margin: 4px 0; }
            table.sig { width: 100%%; margin-top: 28px; border-collapse: collapse; }
            table.sig td { width: 50%%; text-align: center; vertical-align: top; padding-bottom: 70px; }
            .footer-note { margin-top: 20px; font-size: 9pt; color: #475569; border-top: 1px dashed #94a3b8; padding-top: 8px; }
          </style>
        </head>
        <body>
          <div class="header">
            <h2>SMART DRONE INSPECTION PLATFORM</h2>
            <h1>PARTNER INSPECTOR COOPERATION AGREEMENT</h1>
            <div class="meta">Contract No: <strong>%s</strong> | Issue Date: <strong>%s</strong></div>
          </div>

          <div class="section-title">1. PARTY A — PLATFORM OPERATOR</div>
          <div class="box">
            <p><strong>Organization:</strong> SmartDroneInspection Infrastructure Platform</p>
            <p><strong>Role:</strong> Inspection Dispatch, AI Defect Analysis &amp; Settlement Operator</p>
            <p><strong>Representative:</strong> Platform Operations Manager</p>
          </div>

          <div class="section-title">2. PARTY B — PARTNER UAV INSPECTOR</div>
          <div class="box">
            <p><strong>Full Name:</strong> %s</p>
            <p><strong>Account ID:</strong> %s</p>
            <p><strong>Email:</strong> %s &nbsp;&nbsp;|&nbsp;&nbsp; <strong>Phone:</strong> %s</p>
            <p><strong>Citizen ID / Passport (CCCD):</strong> %s</p>
            <p><strong>Registered Address:</strong> %s</p>
            <p><strong>UAV Pilot License No:</strong> %s &nbsp;&nbsp;|&nbsp;&nbsp; <strong>Class:</strong> %s</p>
            <p><strong>License Expiration Date:</strong> %s</p>
          </div>

          <div class="section-title">3. TERMS OF COOPERATION &amp; SAFETY COMPLIANCE</div>
          <p><strong>3.1. Scope of Services:</strong> Party B agrees to perform on-site infrastructure drone inspection missions dispatched by Party A, capture checkpoint evidence, verify AI-detected defects, and submit technical inspection report drafts.</p>
          <p><strong>3.2. Airspace &amp; Flight Permit Gate:</strong> Party B must maintain a valid UAV Pilot License, use only approved drones with valid Registration, Ownership, and Technical Inspection certificates, and comply strictly with Flight Permit and No-Fly Zone regulations.</p>
          <p><strong>3.3. Revenue Settlement &amp; Payout:</strong> For each completed and customer-accepted inspection occurrence, Party B receives <strong>85%% Net Payout</strong> of the settled service fee (after 15%% platform commission).</p>
          <p><strong>3.4. Data Confidentiality &amp; Integrity:</strong> All infrastructure imagery, telemetry logs, and inspection reports belong to the platform and client organization. Once signed by both parties, the scanned contract is uploaded to MinIO storage and locked with a cryptographic <strong>SHA-256 checksum</strong>.</p>

          <table class="sig">
            <tr>
              <td>
                <strong>FOR PARTY A (PLATFORM MANAGER)</strong><br/>
                <em>(Signature, Full Name &amp; Official Seal)</em>
              </td>
              <td>
                <strong>FOR PARTY B (PARTNER INSPECTOR)</strong><br/>
                <em>(Signature &amp; Full Name)</em><br/><br/><br/><br/>
                <strong>%s</strong>
              </td>
            </tr>
          </table>

          <div class="footer-note">
            <strong>Hybrid Onboarding Verification:</strong> Please sign this agreement (via physical signature or legally recognized CA digital signature) and upload the signed PDF/Image scan to the SmartDroneInspection Portal. Your account will be activated for mission dispatch once both your Pilot License Scan and Signed Partner Contract are verified by the Platform Manager.
          </div>
        </body>
        </html>
        """
            .formatted(
                contractCode,
                today,
                fullName,
                user.getId(),
                email,
                phone,
                citizenId,
                address,
                licenseNo,
                licenseClass,
                licenseExp,
                fullName);

    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PdfRendererBuilder builder = new PdfRendererBuilder();
      builder.useFastMode();
      builder.withHtmlContent(html, null);
      builder.toStream(out);
      builder.run();
      return out.toByteArray();
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "PDF_RENDER_FAILED",
          "Could not generate draft partner contract PDF");
    }
  }

  private static String escapeXml(String input) {
    if (input == null) return "";
    return input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  private EvidenceObjectStore requireObjectStore() {
    return objectStore.orElseThrow(this::storageUnavailable);
  }

  private BusinessException storageUnavailable() {
    return new BusinessException(
        HttpStatus.BAD_GATEWAY, "STORAGE_UNAVAILABLE", "Object storage is unavailable");
  }

  public record UploadResponse(String fileKey) {}

  public record ContractUploadResponse(String fileKey, String sha256Hex) {}

  public record InputStreamContent(String contentType, InputStream stream) {}
}
