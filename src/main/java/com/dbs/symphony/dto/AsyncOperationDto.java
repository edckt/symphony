package com.dbs.symphony.dto;

import java.time.OffsetDateTime;

public record AsyncOperationDto(
        String id,
        String status,          // PENDING, RUNNING, DONE, FAILED
        OffsetDateTime createdAt,
        OffsetDateTime doneAt,
        String resourceId,
        ErrorDto error
) {}
