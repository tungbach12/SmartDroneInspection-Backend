package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.Size;

/**
 * The MF2-06 submission.
 *
 * <p>{@code acknowledgment} is the safety acknowledgment the inspector makes when submitting. It is
 * recorded as part of the submission event, not stored as a preparation field, because it is an act
 * rather than preparation content: SRS 3.4 is explicit that acknowledging the restrictions is not a
 * statutory licence and not a guaranteed digital signature.
 */
public record SubmitPreparationRequest(@Size(max = 2000) String acknowledgment) {}
