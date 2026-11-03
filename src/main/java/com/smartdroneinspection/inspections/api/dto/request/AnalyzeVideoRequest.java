package com.smartdroneinspection.inspections.api.dto.request;

import java.util.List;

public record AnalyzeVideoRequest(List<CandidateFrame> frames) {

  public record CandidateFrame(
      double timestampSeconds, String fileName, String imageBase64, String locationDescription) {}
}
