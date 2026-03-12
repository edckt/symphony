package com.dbs.symphony.dto;

public record GroupDto(
        String groupId,
        String displayName,
        String description,
        String source,
        Integer memberCount
) {}
