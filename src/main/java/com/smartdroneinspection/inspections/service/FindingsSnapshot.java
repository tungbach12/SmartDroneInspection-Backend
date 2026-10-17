package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.domain.AiFindingCandidate;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.VerifiedFinding;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Stable digest of the evidence and Inspector-resolved findings reviewed at the MF3 gate. */
final class FindingsSnapshot {

  private FindingsSnapshot() {}

  static String hash(
      List<Evidence> evidence,
      List<AiFindingCandidate> candidates,
      List<VerifiedFinding> findings) {
    StringBuilder source = new StringBuilder();
    evidence.stream()
        .sorted(Comparator.comparing(item -> item.getId().toString()))
        .forEach(
            item ->
                source
                    .append("E|")
                    .append(item.getId())
                    .append('|')
                    .append(item.getChecksumSha256())
                    .append('\n'));
    candidates.stream()
        .sorted(Comparator.comparing(item -> item.getId().toString()))
        .forEach(
            item ->
                source
                    .append("C|")
                    .append(item.getId())
                    .append('|')
                    .append(item.getStatus())
                    .append('|')
                    .append(item.getPredictedLabel())
                    .append('|')
                    .append(item.getBoundingBox())
                    .append('|')
                    .append(item.getRejectionReason())
                    .append('\n'));
    findings.stream()
        .sorted(Comparator.comparing(item -> item.getId().toString()))
        .forEach(
            item ->
                source
                    .append("F|")
                    .append(item.getId())
                    .append('|')
                    .append(item.getEvidenceId())
                    .append('|')
                    .append(item.getSource())
                    .append('|')
                    .append(item.getDefectLabel())
                    .append('|')
                    .append(item.getSeverity())
                    .append('|')
                    .append(item.getLocationDescription())
                    .append('|')
                    .append(item.getComponent())
                    .append('|')
                    .append(item.getTechnicalNotes())
                    .append('|')
                    .append(item.getRecommendedAction())
                    .append('|')
                    .append(item.getDescription())
                    .append('|')
                    .append(item.getObservedCondition())
                    .append('|')
                    .append(item.getPriority())
                    .append('|')
                    .append(item.getMeasurement())
                    .append('|')
                    .append(item.getStatus())
                    .append('\n'));
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(source.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required by the runtime", ex);
    }
  }
}
