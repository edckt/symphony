package com.dbs.symphony.dto;

public record UserSummaryDto(
        String userId,
        String displayName,
        String email,
        String groupId
) {}
