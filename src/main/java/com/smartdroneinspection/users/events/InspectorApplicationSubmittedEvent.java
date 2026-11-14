package com.smartdroneinspection.users.events;

import java.util.UUID;

/** Published after a new Inspector application is persisted and ready for manager review. */
public record InspectorApplicationSubmittedEvent(UUID applicationId) {}
