package com.dbs.symphony.dto;

import java.util.List;

public record PagedEffectiveQuotaResultsDto(
        List<EffectiveQuotaResultDto> items,
        String nextPageToken
) {}
