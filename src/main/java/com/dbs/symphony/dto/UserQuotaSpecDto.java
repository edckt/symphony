package com.dbs.symphony.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UserQuotaSpecDto(
        @Min(0) int maxInstances,
        @Min(0) int maxTotalVcpu,
        @NotNull List<String> allowedMachineTypes,  // empty list = no restriction
        @Min(50) Integer maxBootDiskGb,
        List<String> allowedZones
) {}