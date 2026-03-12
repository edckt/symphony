package com.dbs.symphony.dto;

import java.util.List;

public record PagedUsersDto(
        List<UserSummaryDto> items,
        String nextPageToken
) {}
