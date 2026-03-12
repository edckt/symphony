package com.dbs.symphony.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchUserIdsRequestDto(
        @NotNull @Size(min = 1, max = 1000) List<String> userIds,
        boolean includeDefault
) {}
