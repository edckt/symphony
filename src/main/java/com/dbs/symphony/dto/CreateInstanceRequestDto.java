package com.dbs.symphony.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record CreateInstanceRequestDto(
        @NotBlank String displayName,
        @NotBlank String zone,
        @NotBlank String instanceSize,
        Map<String, String> labels
) {}
