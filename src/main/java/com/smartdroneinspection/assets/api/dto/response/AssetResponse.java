package com.smartdroneinspection.assets.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Asset projection.
 *
 * @param checkpointsLocked true once an inspection exists for the asset; checkpoint edits are then
 *     rejected and clients render the checkpoint list read-only
 */
public record AssetResponse(
    UUID id,
    String code,
    String name,
    String description,
    String locationText,
    BigDecimal latitude,
    BigDecimal longitude,
    String status,
    UUID categoryId,
    Instant createdAt,
    List<String> inspectionItems,
    BigDecimal structureHeightMeters,
    BigDecimal footprintAreaSquareMeters,
    boolean checkpointsLocked) {

  public AssetResponse {
    inspectionItems = inspectionItems == null ? List.of() : List.copyOf(inspectionItems);
  }

  /** Backward-compatible constructor for callers that do not read the checkpoint lock. */
  public AssetResponse(
      UUID id,
      String code,
      String name,
      String description,
      String locationText,
      BigDecimal latitude,
      BigDecimal longitude,
      String status,
      UUID categoryId,
      Instant createdAt,
      List<String> inspectionItems,
      BigDecimal structureHeightMeters,
      BigDecimal footprintAreaSquareMeters) {
    this(
        id,
        code,
        name,
        description,
        locationText,
        latitude,
        longitude,
        status,
        categoryId,
        createdAt,
        inspectionItems,
        structureHeightMeters,
        footprintAreaSquareMeters,
        false);
  }

  public AssetResponse(
      UUID id,
      String code,
      String name,
      String description,
      String locationText,
      BigDecimal latitude,
      BigDecimal longitude,
      String status,
      UUID categoryId,
      Instant createdAt) {
    this(
        id,
        code,
        name,
        description,
        locationText,
        latitude,
        longitude,
        status,
        categoryId,
        createdAt,
        List.of(),
        null,
        null,
        false);
  }
}
