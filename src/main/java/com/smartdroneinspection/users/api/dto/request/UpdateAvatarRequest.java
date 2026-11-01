package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateAvatarRequest(@Size(max = 2000000) String avatarUrl) {}
