package com.dbs.symphony.dto;

import java.util.List;

public record BatchUserQuotasResponseDto(
        List<UserQuotaLookupResultDto> items,
        DefaultQuotaDto defaultQuota,
        List<String> warnings
) {}
