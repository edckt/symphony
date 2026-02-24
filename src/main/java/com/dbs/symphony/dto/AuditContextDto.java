package com.dbs.symphony.dto;

public record AuditContextDto(
        String groupId,
        String managerGroupId // nullable
) {}