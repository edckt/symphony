package com.dbs.symphony.dto;

public record LatestQuotaAuditResultDto(
        String projectId,
        String groupId,
        String userId,
        String status,           // PRESENT or NONE
        QuotaAuditEventDto latest // can be null
) {}