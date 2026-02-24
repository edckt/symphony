package com.dbs.symphony.dto;

import java.time.OffsetDateTime;

public record WorkbenchInstanceDto(
        String id,
        String displayName,
        String state,
        String machineType,
        String zone,
        String ownerUserId,
        OffsetDateTime createdAt
) {}
