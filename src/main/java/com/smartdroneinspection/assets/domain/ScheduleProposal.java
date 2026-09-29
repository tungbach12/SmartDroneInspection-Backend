package com.smartdroneinspection.assets.domain;

import com.smartdroneinspection.assets.domain.enums.ScheduleProposalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "schedule_proposals",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_schedule_proposal_active",
            columnNames = {"asset_id", "frequency_unit", "frequency_interval"}))
public class ScheduleProposal {

  @Id @GeneratedValue private UUID id;

  @Column(name = "asset_id", nullable = false)
  private UUID assetId;

  @Column(name = "checklist_template_id", nullable = false)
  private UUID checklistTemplateId;

  @Column(name = "frequency_unit", nullable = false, length = 16)
  private String frequencyUnit;

  @Column(name = "frequency_interval", nullable = false)
  private int frequencyInterval;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private ScheduleProposalStatus status;

  @Column(name = "manager_note", length = 500)
  private String managerNote;

  @Column(name = "reviewed_by_user_id")
  private UUID reviewedByUserId;

  @Column(name = "selected_by_user_id")
  private UUID selectedByUserId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected ScheduleProposal() {}

  private ScheduleProposal(
      UUID assetId, UUID checklistTemplateId, String frequencyUnit, int frequencyInterval) {
    if (frequencyInterval <= 0) {
      throw new IllegalArgumentException("frequencyInterval must be positive");
    }
    this.assetId = assetId;
    this.checklistTemplateId = checklistTemplateId;
    this.frequencyUnit = frequencyUnit;
    this.frequencyInterval = frequencyInterval;
    this.status = ScheduleProposalStatus.GENERATED;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public static ScheduleProposal generate(
      UUID assetId, UUID checklistTemplateId, String frequencyUnit, int frequencyInterval) {
    return new ScheduleProposal(assetId, checklistTemplateId, frequencyUnit, frequencyInterval);
  }

  public void managerAdjust(String newUnit, int newInterval) {
    requireNotClientSelected();
    if (newInterval <= 0) {
      throw new IllegalArgumentException("frequencyInterval must be positive");
    }
    this.frequencyUnit = newUnit;
    this.frequencyInterval = newInterval;
    touch();
  }

  public void managerApprove(String note, UUID reviewerId) {
    if (status != ScheduleProposalStatus.GENERATED
        && status != ScheduleProposalStatus.MANAGER_REJECTED) {
      throw new IllegalStateException("Only generated proposals can be approved");
    }
    this.managerNote = note;
    this.reviewedByUserId = reviewerId;
    this.status = ScheduleProposalStatus.MANAGER_APPROVED;
    touch();
  }

  public void managerReject(String note, UUID reviewerId) {
    requireNotClientSelected();
    this.managerNote = note;
    this.reviewedByUserId = reviewerId;
    this.status = ScheduleProposalStatus.MANAGER_REJECTED;
    touch();
  }

  public void clientSelect(UUID selectorId) {
    if (status != ScheduleProposalStatus.MANAGER_APPROVED) {
      throw new IllegalStateException("Only manager-approved proposals can be selected");
    }
    this.selectedByUserId = selectorId;
    this.status = ScheduleProposalStatus.CLIENT_SELECTED;
    touch();
  }

  public void supersede() {
    if (status == ScheduleProposalStatus.MANAGER_APPROVED) {
      this.status = ScheduleProposalStatus.SUPERSEDED;
      touch();
    }
  }

  private void requireNotClientSelected() {
    if (status == ScheduleProposalStatus.CLIENT_SELECTED) {
      throw new IllegalStateException("A client-selected proposal cannot be changed");
    }
  }

  private void touch() {
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getAssetId() {
    return assetId;
  }

  public UUID getChecklistTemplateId() {
    return checklistTemplateId;
  }

  public String getFrequencyUnit() {
    return frequencyUnit;
  }

  public int getFrequencyInterval() {
    return frequencyInterval;
  }

  public ScheduleProposalStatus getStatus() {
    return status;
  }

  public String getManagerNote() {
    return managerNote;
  }

  public UUID getReviewedByUserId() {
    return reviewedByUserId;
  }

  public UUID getSelectedByUserId() {
    return selectedByUserId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
