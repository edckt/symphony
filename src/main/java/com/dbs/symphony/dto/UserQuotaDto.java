package com.dbs.symphony.dto;

import java.time.OffsetDateTime;

public record UserQuotaDto(
        String projectId,
        String groupId,
        String userId,
        UserQuotaSpecDto spec,
        OffsetDateTime updatedAt,
        String updatedBy
) {}