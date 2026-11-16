package com.smartdroneinspection.inspections.api.dto.request;

import java.math.BigDecimal;
import java.util.List;

/** Inspector-selected video keyframes promoted into linked Image Evidence + AI Findings. */
public record PromoteVideoKeyframesRequest(List<KeyframeSelection> keyframes) {

  public record KeyframeSelection(
      double timestampSeconds,
      String fileName,
      String locationDescription,
      String imageBase64,
      List<DetectionPayload> detections) {}

  public record DetectionPayload(
      String modelName, String predictedLabel, BigDecimal confidence, String boundingBox) {}
}
