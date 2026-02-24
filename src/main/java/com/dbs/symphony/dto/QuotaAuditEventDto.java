package com.dbs.symphony.dto;

import java.time.OffsetDateTime;

public record QuotaAuditEventDto(
        String eventId,
        String action,      // UPSERT or DELETE
        OffsetDateTime at,
        AuditActorDto actor,
        AuditContextDto context,
        UserQuotaSpecDto oldSpec, // nullable
        UserQuotaSpecDto newSpec  // nullable
) {}