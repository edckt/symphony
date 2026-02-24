package com.dbs.symphony.service;

/** Utilities for constructing valid GCP label values. */
final class GcpLabels {
    private GcpLabels() {}

    /**
     * Normalises a user ID to a valid GCP label value.
     * Lowercases the string, replaces {@literal @} and {@literal .} with hyphens,
     * collapses consecutive hyphens, and strips any remaining disallowed characters.
     */
    static String normalizeUserId(String userId) {
        return userId.toLowerCase()
                .replaceAll("[@.]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("[^a-z0-9_-]", "");
    }
}
