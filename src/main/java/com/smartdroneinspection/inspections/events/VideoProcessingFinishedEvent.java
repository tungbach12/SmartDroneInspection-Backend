package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/** Published when asynchronous drone video transcoding finishes (READY or FAILED). */
public record VideoProcessingFinishedEvent(
    UUID evidenceId,
    UUID inspectionId,
    UUID inspectorId,
    String fileName,
    boolean success,
    String errorMessage,
    Instant finishedAt) {}
