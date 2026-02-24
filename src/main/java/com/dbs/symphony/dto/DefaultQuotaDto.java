package com.dbs.symphony.dto;

import java.time.OffsetDateTime;

public record DefaultQuotaDto(
        String projectId,
        UserQuotaSpecDto spec,
        OffsetDateTime updatedAt,
        String updatedBy
) {}
