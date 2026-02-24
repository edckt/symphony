package com.dbs.symphony.dto;

import java.util.List;

public record EffectiveLimitsDto(
        int maxInstances,
        int maxTotalVcpu,       // Integer.MAX_VALUE means no restriction
        List<String> allowedMachineTypes,   // empty = no restriction
        Integer maxBootDiskGb,              // null = no restriction
        List<String> allowedZones           // empty = no restriction
) {}
