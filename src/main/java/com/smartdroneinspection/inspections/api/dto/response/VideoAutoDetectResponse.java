package com.smartdroneinspection.inspections.api.dto.response;

import java.util.List;
import java.util.UUID;

public record VideoAutoDetectResponse(
    UUID videoEvidenceId,
    int scannedFrames,
    int extractedEvidenceCount,
    int createdFindingsCount,
    List<ExtractedDefectFrame> keyframes) {

  public record ExtractedDefectFrame(
      double timestampSeconds, EvidenceResponse evidence, List<FindingResponse> findings) {}
}
