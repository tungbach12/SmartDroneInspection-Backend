package com.smartdroneinspection.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetTest {

  @Test
  void normalizesAssetCodeAndSupportsLifecycle() {
    Asset asset =
        new Asset(
            UUID.randomUUID(),
            UUID.randomUUID(),
            " bridge-01 ",
            "River Bridge",
            null,
            "District 1",
            null,
            null,
            null,
            UUID.randomUUID());

    assertThat(asset.getCode()).isEqualTo("BRIDGE-01");
    assertThat(asset.getStatus()).isEqualTo(AssetStatus.ACTIVE);

    asset.deactivate();
    assertThat(asset.getStatus()).isEqualTo(AssetStatus.INACTIVE);
    asset.activate();
    asset.retire();
    assertThat(asset.getStatus()).isEqualTo(AssetStatus.RETIRED);
    assertThatThrownBy(asset::activate).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsInvalidCoordinates() {
    assertThatThrownBy(
            () ->
                new Asset(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "A-1",
                    "Asset",
                    null,
                    "Location",
                    new BigDecimal("91"),
                    null,
                    null,
                    UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Latitude must be between -90 and 90");
  }

  @Test
  void rejectsInvalidDocumentMetadata() {
    Asset asset = newAsset();

    assertThatThrownBy(
            () ->
                asset.addDocument(
                    UUID.randomUUID(),
                    "INSPECTION_REPORT",
                    "report.pdf",
                    "application/pdf",
                    0,
                    "a".repeat(64),
                    "assets/report.pdf",
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Document size must be positive");

    assertThatThrownBy(
            () ->
                asset.addDocument(
                    UUID.randomUUID(),
                    "INSPECTION_REPORT",
                    "report.pdf",
                    "application/pdf",
                    1,
                    "A".repeat(64),
                    "assets/report.pdf",
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Checksum must be a 64-character lowercase hexadecimal SHA-256 value");
  }

  @Test
  void rejectsMissingDocumentChecksum() {
    Asset asset = newAsset();

    assertThatThrownBy(
            () ->
                asset.addDocument(
                    UUID.randomUUID(),
                    "INSPECTION_REPORT",
                    "report.pdf",
                    "application/pdf",
                    1,
                    null,
                    "assets/report.pdf",
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Checksum must be a 64-character lowercase hexadecimal SHA-256 value");
  }

  private Asset newAsset() {
    return new Asset(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "BRIDGE-01",
        "River Bridge",
        null,
        "District 1",
        null,
        null,
        null,
        UUID.randomUUID());
  }
}
