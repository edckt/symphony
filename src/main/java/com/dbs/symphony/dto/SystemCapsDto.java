package com.dbs.symphony.dto;

public record SystemCapsDto(int maxInstancesPerUser) {
    public static final SystemCapsDto INSTANCE = new SystemCapsDto(3);
}
