package com.dbs.symphony.dto;

import java.util.List;

public record BatchUserUsageResponseDto(
        List<UserQuotaUsageDto> items,
        List<String> warnings
) {}
