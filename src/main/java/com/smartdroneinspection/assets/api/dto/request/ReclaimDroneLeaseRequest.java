package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.Size;

public record ReclaimDroneLeaseRequest(@Size(max = 2000) String notes) {}
