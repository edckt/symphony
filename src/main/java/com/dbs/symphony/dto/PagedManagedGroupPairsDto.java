package com.dbs.symphony.dto;

import java.util.List;

public record PagedManagedGroupPairsDto(
        List<ManagedGroupPairDto> items,
        String nextPageToken
) {}
