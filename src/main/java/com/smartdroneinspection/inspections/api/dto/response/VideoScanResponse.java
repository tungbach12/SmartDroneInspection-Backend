package com.smartdroneinspection.inspections.api.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Non-mutating AI YOLO11 video scan result containing candidate timeline defect markers. */
public record VideoScanResponse(
    UUID videoEvidenceId,
    double videoDurationSeconds,
    int scannedFrames,
    List<DetectedMarker> markers) {

  public record DetectedMarker(
      double timestampSeconds,
      int imageWidth,
      int imageHeight,
      String previewBase64,
      String primaryLabel,
      BigDecimal maxConfidence,
      List<MarkerDetection> detections) {}

  public record MarkerDetection(
      String modelName,
      String modelVersion,
      String predictedLabel,
      BigDecimal confidence,
      String boundingBox) {}
}
