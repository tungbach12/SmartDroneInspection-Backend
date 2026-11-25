package com.smartdroneinspection.assets.events;

import java.time.Instant;
import java.util.UUID;

/** Published when a Manager or Admin approves or rejects a drone registration application. */
public record DroneApplicationReviewedEvent(
    UUID applicationId,
    UUID droneId,
    UUID inspectorUserId,
    String model,
    String serialNumber,
    boolean approved,
    String reviewNote,
    Instant occurredAt) {}
