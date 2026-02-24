package com.dbs.symphony.dto;

import java.util.Map;

public record ViolationDto(
        String code,
        String message,
        Map<String, Object> details
) {}
