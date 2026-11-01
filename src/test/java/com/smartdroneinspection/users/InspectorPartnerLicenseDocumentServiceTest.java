package com.smartdroneinspection.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.service.InspectorPartnerLicenseDocumentService;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InspectorPartnerLicenseDocumentServiceTest {

  private static final byte[] PDF =
      "%PDF-1.7\nDocument".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

  @Test
  void adminDocumentReadUsesStoredApplicationObjectKey() throws Exception {
    EvidenceObjectStore store = mock(EvidenceObjectStore.class);
    String key = "inspector-partner-applications/licenses/legacy-license.pdf";
    when(store.open(anyString())).thenReturn(new ByteArrayInputStream(PDF));
    var service = new InspectorPartnerLicenseDocumentService(Optional.of(store));

    var content = service.open(key);

    assertThat(content.contentType()).isEqualTo("application/pdf");
    assertThat(content.stream().readAllBytes()).isEqualTo(PDF);
    verify(store).open(key);
  }

  @Test
  void rejectsUnsupportedLegacyLicenseDocumentExtension() {
    var service =
        new InspectorPartnerLicenseDocumentService(Optional.of(mock(EvidenceObjectStore.class)));

    assertThatThrownBy(
            () -> service.open("inspector-partner-applications/licenses/legacy-license.exe"))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).code())
        .isEqualTo("NOT_FOUND");
  }
}
