package com.dbs.symphony.dto;

import java.util.List;

public record QuotaViolationErrorDto(
        String error,                       // always "QUOTA_VIOLATION"
        List<ViolationDto> violations,
        UserQuotaUsageDto snapshot
) {}
