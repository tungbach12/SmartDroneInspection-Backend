package com.smartdroneinspection.assets.events;

import java.time.Instant;
import java.util.UUID;

/** Published when an Inspector submits or resubmits a drone registration application. */
public record DroneApplicationSubmittedEvent(
    UUID applicationId,
    UUID droneId,
    UUID inspectorUserId,
    String model,
    String serialNumber,
    boolean resubmitted,
    Instant occurredAt) {}
