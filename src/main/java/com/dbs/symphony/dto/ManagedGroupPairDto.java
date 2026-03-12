package com.dbs.symphony.dto;

public record ManagedGroupPairDto(
        GroupDto managerGroup,
        GroupDto userGroup,
        String derivedBy
) {}
