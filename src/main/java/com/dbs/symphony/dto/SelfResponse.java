package com.dbs.symphony.dto;

import java.util.List;

public record SelfResponse(
        String userId,
        String displayName,
        String email,
        List<String> roles
) {}