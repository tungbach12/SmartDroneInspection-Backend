package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.domain.InspectorProfile;
import com.smartdroneinspection.users.domain.User;
import com.smartdroneinspection.users.domain.enums.UserStatus;
import com.smartdroneinspection.users.security.AuthCrypto;
import com.smartdroneinspection.users.service.InspectorPartnerLicenseDocumentService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class InspectorPartnerLicenseDocumentServiceTest {

  private static final byte[] PDF = "%PDF-1.7\nDocument".getBytes(StandardCharsets.US_ASCII);

  private static final String LICENSE_KEY =
      "inspector-partner-applications/licenses/legacy-license.pdf";
  private static final String PENDING_KEY = "inspector-partner-applications/pending/scan.png";
  private static final String CONTRACT_KEY =
      "inspector-partner-applications/contracts/contract.pdf";

  @Test
  void adminDocumentReadUsesStoredApplicationObjectKey() throws Exception {
    EvidenceObjectStore store = mock(EvidenceObjectStore.class);
    when(store.open(anyString())).thenReturn(new ByteArrayInputStream(PDF));
    var service = new InspectorPartnerLicenseDocumentService(Optional.of(store));

    var content = service.open(LICENSE_KEY);

    assertThat(content.contentType()).isEqualTo("application/pdf");
    assertThat(content.stream().readAllBytes()).isEqualTo(PDF);
    verify(store).open(LICENSE_KEY);
  }

  @Test
  void rejectsUnsupportedLegacyLicenseDocumentExtension() {
    var service =
        new InspectorPartnerLicenseDocumentService(Optional.of(mock(EvidenceObjectStore.class)));

    assertThatThrownBy(
            () -> service.open("inspector-partner-applications/licenses/legacy-license.exe"))
        .isInstanceOfSatisfying(
            BusinessException.class, error -> assertThat(error.code()).isEqualTo("NOT_FOUND"));
  }

  @Test
  void validatesLicenseReferencesBeforeReading() {
    var service =
        new InspectorPartnerLicenseDocumentService(Optional.of(mock(EvidenceObjectStore.class)));

    service.validateReference(LICENSE_KEY);

    assertThatThrownBy(
            () -> service.validateReference("inspector-partner-applications/licenses/scan.exe"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.code()).isEqualTo("INVALID_REFERENCE");
              assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    assertThatThrownBy(() -> service.validateReference(null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("INVALID_REFERENCE"));
  }

  @Test
  void uploadsLicenseScanUnderTheLicensesPrefix() throws Exception {
    EvidenceObjectStore store = mock(EvidenceObjectStore.class);
    var service = new InspectorPartnerLicenseDocumentService(Optional.of(store));
    var file = new MockMultipartFile("file", "license.pdf", "application/pdf", PDF);

    var response = service.upload(file);

    assertThat(response.fileKey())
        .startsWith("inspector-partner-applications/licenses/")
        .endsWith(".pdf");
    verify(store).put(eq(response.fileKey()), eq("application/pdf"), eq((long) PDF.length), any());
  }

  @Test
  void uploadsContractAndReturnsSha256Checksum() throws Exception {
    EvidenceObjectStore store = mock(EvidenceObjectStore.class);
    var service = new InspectorPartnerLicenseDocumentService(Optional.of(store));
    var file = new MockMultipartFile("file", "contract.pdf", "application/pdf", PDF);

    var response = service.uploadContract(file);

    assertThat(response.fileKey())
        .startsWith("inspector-partner-applications/contracts/")
        .endsWith(".pdf");
    assertThat(response.sha256Hex()).isEqualTo(AuthCrypto.sha256Hex(PDF));
    verify(store).put(eq(response.fileKey()), eq("application/pdf"), eq((long) PDF.length), any());
  }

  @Test
  void uploadsPendingApplicationScan() throws Exception {
    EvidenceObjectStore store = mock(EvidenceObjectStore.class);
    var service = new InspectorPartnerLicenseDocumentService(Optional.of(store));
    var file =
        new MockMultipartFile(
            "file",
            "scan.png",
            "image/png",
            new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});

    String key = service.uploadApplicationLicenseScan(file);

    assertThat(key).startsWith("inspector-partner-applications/pending/").endsWith(".png");
    verify(store).put(eq(key), eq("image/png"), anyLong(), any());
  }

  @Test
  void uploadWithoutStorageFailsWithBadGateway() {
    var service = new InspectorPartnerLicenseDocumentService(Optional.empty());
    var file = new MockMultipartFile("file", "license.pdf", "application/pdf", PDF);

    assertThatThrownBy(() -> service.upload(file))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> {
              assertThat(error.code()).isEqualTo("STORAGE_UNAVAILABLE");
              assertThat(error.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
            });
  }

  @Test
  void uploadReportsStorageIoFailuresAsUnavailable() throws Exception {
    EvidenceObjectStore store = mock(EvidenceObjectStore.class);
    var service = new InspectorPartnerLicenseDocumentService(Optional.of(store));
    var file = new MockMultipartFile("file", "license.pdf", "application/pdf", PDF);
    doThrow(new IOException("minio down"))
        .when(store)
        .put(anyString(), anyString(), anyLong(), any());

    assertThatThrownBy(() -> service.upload(file))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("STORAGE_UNAVAILABLE"));
  }

  @Test
  void opensPendingApplicationScansWithResolvedContentType() throws Exception {
    EvidenceObjectStore store = mock(EvidenceObjectStore.class);
    when(store.open(PENDING_KEY)).thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));
    var service = new InspectorPartnerLicenseDocumentService(Optional.of(store));

    var content = service.openApplicationLicenseScan(PENDING_KEY);

    assertThat(content.contentType()).isEqualTo("image/png");
    assertThat(content.stream().readAllBytes()).containsExactly(1, 2, 3);
  }

  @Test
  void rejectsInvalidPendingApplicationScans() {
    var service =
        new InspectorPartnerLicenseDocumentService(Optional.of(mock(EvidenceObjectStore.class)));

    assertThatThrownBy(
            () ->
                service.openApplicationLicenseScan(
                    "inspector-partner-applications/pending/scan.exe"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("INVALID_REFERENCE"));
  }

  @Test
  void deletesPendingApplicationScansOnlyForValidKeys() throws Exception {
    EvidenceObjectStore store = mock(EvidenceObjectStore.class);
    var service = new InspectorPartnerLicenseDocumentService(Optional.of(store));

    service.deleteApplicationLicenseScan(" ");
    verifyNoInteractions(store);

    service.deleteApplicationLicenseScan(PENDING_KEY);
    verify(store).delete(PENDING_KEY);

    assertThatThrownBy(
            () ->
                service.deleteApplicationLicenseScan(
                    "inspector-partner-applications/pending/scan.exe"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("INVALID_REFERENCE"));
    verify(store, never()).delete("inspector-partner-applications/pending/scan.exe");
  }

  @Test
  void opensPartnerContractsOnlyForContractKeys() throws Exception {
    EvidenceObjectStore store = mock(EvidenceObjectStore.class);
    when(store.open(CONTRACT_KEY)).thenReturn(new ByteArrayInputStream(PDF));
    var service = new InspectorPartnerLicenseDocumentService(Optional.of(store));

    var content = service.openContract(CONTRACT_KEY);

    assertThat(content.contentType()).isEqualTo("application/pdf");
    assertThatThrownBy(() -> service.openContract(LICENSE_KEY))
        .isInstanceOfSatisfying(
            BusinessException.class, error -> assertThat(error.code()).isEqualTo("NOT_FOUND"));
  }

  @Test
  void storageReadFailuresAreReportedAsUnavailable() throws Exception {
    EvidenceObjectStore store = mock(EvidenceObjectStore.class);
    when(store.open(LICENSE_KEY)).thenThrow(new IOException("minio down"));
    var service = new InspectorPartnerLicenseDocumentService(Optional.of(store));

    assertThatThrownBy(() -> service.open(LICENSE_KEY))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("STORAGE_UNAVAILABLE"));
  }

  @Test
  void rendersDraftPartnerContractPdfWithPlaceholdersForMissingProfileData() {
    User user = user();
    InspectorProfile profile = new InspectorProfile(user.getId());
    var service =
        new InspectorPartnerLicenseDocumentService(Optional.of(mock(EvidenceObjectStore.class)));

    byte[] pdf = service.renderDraftPartnerContractPdf(user, profile);

    assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    assertThat(pdf.length).isGreaterThan(1000);
  }

  @Test
  void rendersDraftPartnerContractPdfWithProfileAndEscapedXml() {
    User user = user();
    InspectorProfile profile = new InspectorProfile(user.getId());
    profile.updatePersonalDetails("123456789", LocalDate.of(1990, 1, 1), "Hanoi <HQ>", null);
    profile.updatePilotLicense(
        "LIC-1",
        "Class A",
        LocalDate.of(2020, 1, 1),
        Instant.parse("2030-01-01T00:00:00Z"),
        "CAA",
        null);
    var service =
        new InspectorPartnerLicenseDocumentService(Optional.of(mock(EvidenceObjectStore.class)));

    byte[] pdf = service.renderDraftPartnerContractPdf(user, profile);

    assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    assertThat(pdf.length).isGreaterThan(1000);
  }

  private static User user() {
    User user = new User("pilot@example.test", "Pilot <One>", "hash", UserStatus.ACTIVE);
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    return user;
  }
}
