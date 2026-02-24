package com.dbs.symphony.service;

import org.springframework.stereotype.Service;

/**
 * Derives vCPU count from a GCP machine type name without making a Compute API call.
 *
 * Convention for predefined types (e2-standard-4, n2-standard-8, etc.):
 *   the last hyphen-delimited token is the vCPU count.
 *
 * Convention for custom types (custom-8-32768):
 *   the second token is the vCPU count.
 *
 * Falls back to 8 vCPUs if the name cannot be parsed, which is a safe
 * over-count that errs on the conservative side for quota enforcement.
 */
@Service
public class MachineTypeService {

    private static final int FALLBACK_VCPU = 8;

    public int vcpuCount(String machineType) {
        if (machineType == null || machineType.isBlank()) {
            return FALLBACK_VCPU;
        }

        String[] parts = machineType.split("-");

        // custom-N-M → second token is vCPU count
        if (parts.length >= 2 && "custom".equals(parts[0])) {
            return parseOrDefault(parts[1]);
        }

        // e2-standard-4 / n2-standard-8 / n2d-standard-16 / c2-standard-4 etc. → last token
        if (parts.length >= 1) {
            return parseOrDefault(parts[parts.length - 1]);
        }

        return FALLBACK_VCPU;
    }

    private int parseOrDefault(String token) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            return FALLBACK_VCPU;
        }
    }
}
