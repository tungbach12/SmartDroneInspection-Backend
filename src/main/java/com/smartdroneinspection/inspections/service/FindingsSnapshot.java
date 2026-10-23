package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Finding;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Stable digest of the evidence and findings the Inspector resolved at the MF3 gate. */
final class FindingsSnapshot {

  private FindingsSnapshot() {}

  static String hash(List<Evidence> evidence, List<Finding> findings) {
    StringBuilder source = new StringBuilder();
    evidence.stream()
        .sorted(Comparator.comparing(item -> item.getId().toString()))
        .forEach(
            item ->
                source
                    .append("E|")
                    .append(item.getId())
                    .append('|')
                    .append(item.getFileName())
                    .append('|')
                    .append(item.getSizeBytes())
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
                    .append(item.getStatus())
                    .append('|')
                    .append(item.getDefectLabel())
                    .append('|')
                    .append(item.getSeverity())
                    .append('|')
                    .append(item.getTechnicalNotes())
                    .append('|')
                    .append(item.getRecommendedAction())
                    .append('|')
                    .append(
                        item.getInspectorConfirmedAt() == null
                            ? null
                            : item.getInspectorConfirmedAt()
                                .truncatedTo(java.time.temporal.ChronoUnit.MICROS))
                    .append('|')
                    .append(
                        item.getDeletedAt() == null
                            ? null
                            : item.getDeletedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS))
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
