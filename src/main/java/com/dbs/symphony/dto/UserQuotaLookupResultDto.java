package com.dbs.symphony.dto;

public record UserQuotaLookupResultDto(
        String projectId,
        String groupId,
        String userId,
        String status,              // EXPLICIT or NONE
        UserQuotaDto quota,         // null unless status=EXPLICIT
        String effectiveSource,     // USER_QUOTA / DEFAULT_QUOTA / GLOBAL_DEFAULT
        String note,
        UserQuotaSpecDto effectiveSpec  // resolved spec for enforcement; null for GLOBAL_DEFAULT
) {}