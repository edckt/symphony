package com.dbs.symphony.service;

import com.dbs.symphony.dto.EffectiveLimitsDto;
import com.dbs.symphony.dto.SystemCapsDto;
import com.dbs.symphony.dto.UsageTotalsDto;
import com.dbs.symphony.dto.UserQuotaLookupResultDto;
import com.dbs.symphony.dto.UserQuotaSpecDto;
import com.dbs.symphony.dto.UserQuotaUsageDto;
import com.dbs.symphony.dto.ViolationDto;
import com.dbs.symphony.dto.WorkbenchInstanceDto;
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

        // Count all instances CAI returns: both RUNNING and STOPPED (temporarily off, can restart).
        // Truly deleted instances disappear from CAI entirely and are never returned here.
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
     * machineType, zone, bootDiskGb, and requiresApproval are pre-resolved from the chosen instance size.
     * Throws QuotaViolationException (→ 422) if any limit would be exceeded.
     *
     * @param requiresApproval true for sizes (LARGE/XLARGE) that require explicit manager grant
     */
    public void enforceCreate(UserQuotaUsageDto usageSnapshot, String machineType, String zone,
                              int bootDiskGb, boolean requiresApproval) {
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
            int requestedVcpu = machineTypeService.vcpuCount(machineType);
            if (usage.totalVcpu() + requestedVcpu > limits.maxTotalVcpu()) {
                violations.add(violation("MAX_VCPU_EXCEEDED",
                        "vCPU limit of " + limits.maxTotalVcpu() + " would be exceeded",
                        Map.of("limit", limits.maxTotalVcpu(),
                               "current", usage.totalVcpu(),
                               "requested", requestedVcpu)));
            }
        }

        // Machine type allowlist
        // requiresApproval=true: must be explicitly listed (empty allowlist = not approved)
        // requiresApproval=false: allowed unless explicitly excluded by a non-empty allowlist
        boolean machineTypeNotAllowed = requiresApproval
                ? !limits.allowedMachineTypes().contains(machineType)
                : !limits.allowedMachineTypes().isEmpty() && !limits.allowedMachineTypes().contains(machineType);
        if (machineTypeNotAllowed) {
            violations.add(violation("MACHINE_TYPE_NOT_ALLOWED",
                    "Machine type " + machineType + " is not in the allowed list",
                    Map.of("value", machineType)));
        }

        // Zone allowlist
        if (!limits.allowedZones().isEmpty()
                && !limits.allowedZones().contains(zone)) {
            violations.add(violation("ZONE_NOT_ALLOWED",
                    "Zone " + zone + " is not in the allowed list",
                    Map.of("value", zone)));
        }

        // Boot disk size
        if (limits.maxBootDiskGb() != null && bootDiskGb > limits.maxBootDiskGb()) {
            violations.add(violation("DISK_TOO_LARGE",
                    "Boot disk size " + bootDiskGb + " GB exceeds limit of " + limits.maxBootDiskGb() + " GB",
                    Map.of("limit", limits.maxBootDiskGb(),
                           "requested", bootDiskGb)));
        }

        if (!violations.isEmpty()) {
            throw new QuotaViolationException(violations, usageSnapshot);
        }
    }

    // ---------------------------------------------------------------------------

    private EffectiveLimitsDto resolveEffectiveLimits(UserQuotaLookupResultDto lookup) {
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
