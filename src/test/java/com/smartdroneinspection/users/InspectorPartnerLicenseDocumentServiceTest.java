package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.service.InspectorPartnerLicenseDocumentService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class InspectorPartnerLicenseDocumentServiceTest {

  private static final byte[] PDF =
      "%PDF-1.7\nDocument".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

  @Test
  void uploadsValidatedPdfUsingServerGeneratedKey() throws Exception {
    EvidenceObjectStore store = org.mockito.Mockito.mock(EvidenceObjectStore.class);
    var service = new InspectorPartnerLicenseDocumentService(Optional.of(store));

    var response = service.upload(file("license.pdf", "application/pdf", PDF));

    assertThat(response.fileKey())
        .matches("inspector-partner-applications/licenses/[0-9a-f-]{36}\\.pdf");
    verify(store)
        .put(
            anyString(),
            org.mockito.ArgumentMatchers.eq("application/pdf"),
            org.mockito.ArgumentMatchers.eq((long) PDF.length),
            any(InputStream.class));
  }

  @Test
  void rejectsSpoofedPdfBeforeStorage() {
    EvidenceObjectStore store = org.mockito.Mockito.mock(EvidenceObjectStore.class);
    var service = new InspectorPartnerLicenseDocumentService(Optional.of(store));

    assertThatThrownBy(
            () -> service.upload(file("fake.pdf", "application/pdf", new byte[] {1, 2, 3})))
        .isInstanceOf(BusinessException.class);

    verifyNoInteractions(store);
  }

  @Test
  void rejectsOversizeFileBeforeStorage() {
    EvidenceObjectStore store = org.mockito.Mockito.mock(EvidenceObjectStore.class);
    var service = new InspectorPartnerLicenseDocumentService(Optional.of(store));

    assertThatThrownBy(
            () ->
                service.upload(
                    file("large.pdf", "application/pdf", new byte[10 * 1024 * 1024 + 1])))
        .isInstanceOf(BusinessException.class);

    verifyNoInteractions(store);
  }

  @Test
  void rejectsReferenceToMissingUploadedLicense() throws Exception {
    EvidenceObjectStore store = org.mockito.Mockito.mock(EvidenceObjectStore.class);
    when(store.open(anyString())).thenThrow(new IOException("missing object"));
    var service = new InspectorPartnerLicenseDocumentService(Optional.of(store));

    assertThatThrownBy(
            () ->
                service.validateReference(
                    "inspector-partner-applications/licenses/00000000-0000-0000-0000-000000000000.pdf"))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).code())
        .isEqualTo("INVALID_EVIDENCE_REFERENCE");
  }

  @Test
  void adminDocumentReadUsesTheStoredObjectKey() throws Exception {
    EvidenceObjectStore store = org.mockito.Mockito.mock(EvidenceObjectStore.class);
    when(store.open(anyString())).thenReturn(new ByteArrayInputStream(PDF));
    var service = new InspectorPartnerLicenseDocumentService(Optional.of(store));
    String key = "inspector-partner-applications/licenses/00000000-0000-0000-0000-000000000000.pdf";

    var content = service.open(key);

    assertThat(content.contentType()).isEqualTo("application/pdf");
    assertThat(content.stream().readAllBytes()).isEqualTo(PDF);
    verify(store).open(key);
  }

  private static MockMultipartFile file(String name, String type, byte[] content) {
    return new MockMultipartFile("file", name, type, content);
  }
}
