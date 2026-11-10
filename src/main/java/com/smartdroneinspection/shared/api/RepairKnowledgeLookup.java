package com.smartdroneinspection.shared.api;

import java.util.List;

/**
 * Cross-module read-only lookup for RAG engineering standards and post-repair verification criteria
 * from the repair knowledge base.
 */
public interface RepairKnowledgeLookup {

  record VerificationGuidance(
      List<String> referenceStandards, String verificationChecklist, String sourceTitle) {}

  VerificationGuidance lookupVerificationGuidance(String defectLabel, String technicalNotes);
}
