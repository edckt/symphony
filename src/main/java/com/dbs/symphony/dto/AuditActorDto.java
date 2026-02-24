package com.dbs.symphony.dto;

public record AuditActorDto(
        String principal,
        String displayName,
        String email
) {}