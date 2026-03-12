package com.dbs.symphony.dto;

public record EffectiveQuotaResultDto(
        String projectId,
        String groupId,
        String userId,
        SystemCapsDto systemCaps,
        EffectiveLimitsDto effectiveLimits,
        String status,
        String effectiveSource,
        UsageTotalsDto usage,
        UsageTotalsDto remaining
) {}
