package com.dbs.symphony.service;

import com.dbs.symphony.dto.*;
import com.dbs.symphony.exception.QuotaViolationException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class QuotaEnforcementService {

    static final int SYSTEM_MAX_INSTANCES = 3;
    // Sentinel meaning "no restriction" for vCPU limit
    static final int UNLIMITED_VCPU = Integer.MAX_VALUE;

    private final QuotaService quotaService;
    private final CaiService caiService;
    private final MachineTypeService machineTypeService;

    public QuotaEnforcementService(QuotaService quotaService,
                                   CaiService caiService,
                                   MachineTypeService machineTypeService) {
        this.quotaService = quotaService;
        this.caiService = caiService;
        this.machineTypeService = machineTypeService;
    }

    /**
     * Computes a live usage snapshot for the given user:
     *  1. Resolves effective limits from the three-tier quota fallback
     *  2. Queries CAI for current instances
     *  3. Returns the snapshot with usage and remaining headroom
     */
    public UserQuotaUsageDto computeUsage(String projectId, String groupId,
                                          String userId, String bankUserId) {
        var lookup = quotaService.getUserQuota(projectId, groupId, userId);
        EffectiveLimitsDto limits = resolveEffectiveLimits(lookup);

        List<WorkbenchInstanceDto> instances = caiService.listUserInstances(projectId, bankUserId);
        int instanceCount = instances.size();
        int totalVcpu = instances.stream()
                .mapToInt(i -> machineTypeService.vcpuCount(i.machineType()))
                .sum();

        UsageTotalsDto usage = new UsageTotalsDto(instanceCount, totalVcpu);
        UsageTotalsDto remaining = new UsageTotalsDto(
                Math.max(0, limits.maxInstances() - instanceCount),
                limits.maxTotalVcpu() == UNLIMITED_VCPU
                        ? UNLIMITED_VCPU
                        : Math.max(0, limits.maxTotalVcpu() - totalVcpu)
        );

        return new UserQuotaUsageDto(
                projectId, groupId, userId,
                OffsetDateTime.now(),
                SystemCapsDto.INSTANCE,
                limits,
                usage,
                remaining,
                lookup.effectiveSource()
        );
    }

    /**
     * Validates a create request against the pre-computed usage snapshot.
     * Throws QuotaViolationException (→ 422) if any limit would be exceeded.
     */
    public void enforceCreate(UserQuotaUsageDto usageSnapshot, CreateInstanceRequestDto request) {
        EffectiveLimitsDto limits = usageSnapshot.effectiveLimits();
        UsageTotalsDto usage = usageSnapshot.usage();
        List<ViolationDto> violations = new ArrayList<>();

        // System hard cap — checked first so clients see the most precise code
        if (usage.instances() >= SYSTEM_MAX_INSTANCES) {
            violations.add(violation("SYSTEM_MAX_INSTANCES_EXCEEDED",
                    "System limit of " + SYSTEM_MAX_INSTANCES + " instances per user exceeded",
                    Map.of("limit", SYSTEM_MAX_INSTANCES, "current", usage.instances())));
        } else if (usage.instances() >= limits.maxInstances()) {
            violations.add(violation("MAX_INSTANCES_EXCEEDED",
                    "Manager quota of " + limits.maxInstances() + " instances exceeded",
                    Map.of("limit", limits.maxInstances(), "current", usage.instances())));
        }

        // vCPU check
        if (limits.maxTotalVcpu() != UNLIMITED_VCPU) {
            int requestedVcpu = machineTypeService.vcpuCount(request.machineType());
            if (usage.totalVcpu() + requestedVcpu > limits.maxTotalVcpu()) {
                violations.add(violation("MAX_VCPU_EXCEEDED",
                        "vCPU limit of " + limits.maxTotalVcpu() + " would be exceeded",
                        Map.of("limit", limits.maxTotalVcpu(),
                               "current", usage.totalVcpu(),
                               "requested", requestedVcpu)));
            }
        }

        // Machine type allowlist
        if (!limits.allowedMachineTypes().isEmpty()
                && !limits.allowedMachineTypes().contains(request.machineType())) {
            violations.add(violation("MACHINE_TYPE_NOT_ALLOWED",
                    "Machine type " + request.machineType() + " is not in the allowed list",
                    Map.of("value", request.machineType())));
        }

        // Zone allowlist
        if (!limits.allowedZones().isEmpty()
                && !limits.allowedZones().contains(request.zone())) {
            violations.add(violation("ZONE_NOT_ALLOWED",
                    "Zone " + request.zone() + " is not in the allowed list",
                    Map.of("value", request.zone())));
        }

        // Boot disk size
        if (limits.maxBootDiskGb() != null
                && request.bootDiskGb() != null
                && request.bootDiskGb() > limits.maxBootDiskGb()) {
            violations.add(violation("DISK_TOO_LARGE",
                    "Boot disk size " + request.bootDiskGb() + " GB exceeds limit of " + limits.maxBootDiskGb() + " GB",
                    Map.of("limit", limits.maxBootDiskGb(),
                           "requested", request.bootDiskGb())));
        }

        if (!violations.isEmpty()) {
            throw new QuotaViolationException(violations, usageSnapshot);
        }
    }

    // ---------------------------------------------------------------------------

    private EffectiveLimitsDto resolveEffectiveLimits(
            com.dbs.symphony.dto.UserQuotaLookupResultDto lookup) {
        UserQuotaSpecDto spec = lookup.effectiveSpec();
        if (spec == null) {
            return new EffectiveLimitsDto(
                    SYSTEM_MAX_INSTANCES, UNLIMITED_VCPU, List.of(), null, List.of());
        }
        int maxInstances = Math.min(SYSTEM_MAX_INSTANCES, spec.maxInstances());
        int maxTotalVcpu = spec.maxTotalVcpu() == 0 ? UNLIMITED_VCPU : spec.maxTotalVcpu();
        List<String> allowedMachineTypes = spec.allowedMachineTypes() != null
                ? spec.allowedMachineTypes() : List.of();
        List<String> allowedZones = spec.allowedZones() != null
                ? spec.allowedZones() : List.of();

        return new EffectiveLimitsDto(
                maxInstances, maxTotalVcpu, allowedMachineTypes, spec.maxBootDiskGb(), allowedZones);
    }

    private ViolationDto violation(String code, String message, Map<String, Object> details) {
        return new ViolationDto(code, message, details);
    }
}
