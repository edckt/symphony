package com.dbs.symphony.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchUserUsageRequestDto(
        @NotNull @Size(min = 1, max = 500) List<String> userIds,
        boolean includeLimits
) {}
