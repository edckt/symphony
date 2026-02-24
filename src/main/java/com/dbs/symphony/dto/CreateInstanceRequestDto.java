package com.dbs.symphony.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record CreateInstanceRequestDto(
        @NotBlank String displayName,
        @NotBlank String zone,
        @NotBlank String machineType,
        @Min(50) Integer bootDiskGb,
        Map<String, String> labels
) {}
