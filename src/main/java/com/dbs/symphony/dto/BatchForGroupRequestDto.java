package com.dbs.symphony.dto;

public record BatchForGroupRequestDto(
        boolean includeUsage,  // spec default: true; absent/false → controller treats as include-all
        int pageSize,          // spec default: 200; 0 = unset, controller applies default
        String pageToken       // nullable
) {}
