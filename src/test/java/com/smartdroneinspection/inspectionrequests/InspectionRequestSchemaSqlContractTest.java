package com.smartdroneinspection.inspectionrequests;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InspectionRequestSchemaSqlContractTest {

  @Test
  void requestScopeCheckRejectsOnlyBlankValuesAndPreservesWhitespace() throws IOException {
    String migration =
        Files.readString(
            Path.of("src/main/resources/db/migration/V6__inspection_requests_and_assignments.sql"));

    assertThat(migration)
        .contains("LENGTH(BTRIM(scope)) > 0")
        .doesNotContain("scope = BTRIM(scope)");
  }
}
