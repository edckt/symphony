package com.dbs.symphony.dto;

import java.time.OffsetDateTime;

public record UserQuotaUsageDto(
        String projectId,
        String groupId,
        String userId,
        OffsetDateTime computedAt,
        SystemCapsDto systemCaps,
        EffectiveLimitsDto effectiveLimits,
        UsageTotalsDto usage,
        UsageTotalsDto remaining,
        String effectiveSource   // USER_QUOTA / DEFAULT_QUOTA / GLOBAL_DEFAULT
) {}
