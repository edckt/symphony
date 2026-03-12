package com.dbs.symphony.dto;

/**
 * Represents a named instance size option returned by the sizes discovery endpoint.
 *
 * @param size        Canonical size identifier (e.g. "SMALL", "MEDIUM", "LARGE", "XLARGE")
 * @param displayName Human-readable label shown in UI (e.g. "Small")
 * @param description Short spec summary shown in UI (e.g. "4 vCPU · 16 GB RAM")
 * @param machineType Underlying GCP machine type (e.g. "e2-standard-4")
 * @param vcpu        vCPU count
 * @param memoryGb    RAM in GB
 * @param bootDiskGb  Default boot disk size in GB
 * @param available        false when the user's effective quota excludes this size
 * @param requiresApproval true when a manager must explicitly grant this size via allowedMachineTypes
 */
public record InstanceSizeDto(
        String size,
        String displayName,
        String description,
        String machineType,
        int vcpu,
        int memoryGb,
        int bootDiskGb,
        boolean available,
        boolean requiresApproval
) {}
