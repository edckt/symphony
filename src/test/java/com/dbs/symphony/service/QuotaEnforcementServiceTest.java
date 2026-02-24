package com.dbs.symphony.service;

import com.dbs.symphony.dto.*;
import com.dbs.symphony.exception.QuotaViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaEnforcementServiceTest {

    @Mock
    QuotaService quotaService;

    @Mock
    CaiService caiService;

    QuotaEnforcementService enforcement;

    @BeforeEach
    void setUp() {
        enforcement = new QuotaEnforcementService(quotaService, caiService, new MachineTypeService());
    }

    // -------------------------------------------------------------------------
    // computeUsage
    // -------------------------------------------------------------------------

    @Test
    void computeUsage_globalDefault_returnsSystemCapLimits() {
        when(quotaService.getUserQuota("p1", "g1", "u1"))
                .thenReturn(globalDefaultLookup("p1", "g1", "u1"));
        when(caiService.listUserInstances("p1", "bank1"))
                .thenReturn(List.of());

        UserQuotaUsageDto result = enforcement.computeUsage("p1", "g1", "u1", "bank1");

        assertThat(result.effectiveSource()).isEqualTo("GLOBAL_DEFAULT");
        assertThat(result.effectiveLimits().maxInstances()).isEqualTo(3);
        assertThat(result.effectiveLimits().maxTotalVcpu()).isEqualTo(Integer.MAX_VALUE);
        assertThat(result.effectiveLimits().allowedMachineTypes()).isEmpty();
        assertThat(result.effectiveLimits().allowedZones()).isEmpty();
        assertThat(result.usage().instances()).isEqualTo(0);
        assertThat(result.remaining().instances()).isEqualTo(3);
    }

    @Test
    void computeUsage_userQuota_capsMaxInstancesAtSystemLimit() {
        // manager set 5 — should be silently clamped to 3
        when(quotaService.getUserQuota("p1", "g1", "u1"))
                .thenReturn(userQuotaLookup("p1", "g1", "u1", spec(5, 0, List.of(), null, List.of())));
        when(caiService.listUserInstances("p1", "bank1"))
                .thenReturn(List.of());

        UserQuotaUsageDto result = enforcement.computeUsage("p1", "g1", "u1", "bank1");

        assertThat(result.effectiveLimits().maxInstances()).isEqualTo(3);
    }

    @Test
    void computeUsage_userQuota_respectsManagerLimitBelowSystemCap() {
        when(quotaService.getUserQuota("p1", "g1", "u1"))
                .thenReturn(userQuotaLookup("p1", "g1", "u1", spec(2, 0, List.of(), null, List.of())));
        when(caiService.listUserInstances("p1", "bank1"))
                .thenReturn(List.of());

        UserQuotaUsageDto result = enforcement.computeUsage("p1", "g1", "u1", "bank1");

        assertThat(result.effectiveLimits().maxInstances()).isEqualTo(2);
    }

    @Test
    void computeUsage_withExistingInstances_aggregatesVcpuAndCountsCorrectly() {
        when(quotaService.getUserQuota("p1", "g1", "u1"))
                .thenReturn(globalDefaultLookup("p1", "g1", "u1"));
        when(caiService.listUserInstances("p1", "bank1"))
                .thenReturn(List.of(
                        instance("i1", "n2-standard-8"),   // 8 vCPUs
                        instance("i2", "e2-standard-4")    // 4 vCPUs
                ));

        UserQuotaUsageDto result = enforcement.computeUsage("p1", "g1", "u1", "bank1");

        assertThat(result.usage().instances()).isEqualTo(2);
        assertThat(result.usage().totalVcpu()).isEqualTo(12);
        assertThat(result.remaining().instances()).isEqualTo(1); // 3 - 2
    }

    @Test
    void computeUsage_maxTotalVcpuZero_treatedAsUnlimited() {
        when(quotaService.getUserQuota("p1", "g1", "u1"))
                .thenReturn(userQuotaLookup("p1", "g1", "u1", spec(3, 0, List.of(), null, List.of())));
        when(caiService.listUserInstances("p1", "bank1"))
                .thenReturn(List.of());

        UserQuotaUsageDto result = enforcement.computeUsage("p1", "g1", "u1", "bank1");

        assertThat(result.effectiveLimits().maxTotalVcpu()).isEqualTo(Integer.MAX_VALUE);
        assertThat(result.remaining().totalVcpu()).isEqualTo(Integer.MAX_VALUE);
    }

    // -------------------------------------------------------------------------
    // enforceCreate — passes
    // -------------------------------------------------------------------------

    @Test
    void enforceCreate_underAllLimits_doesNotThrow() {
        UserQuotaUsageDto usage = usageSnapshot(
                limits(2, 16, List.of("n2-standard-8"), 200, List.of("us-central1-a")),
                totals(1, 8)
        );
        CreateInstanceRequestDto request = new CreateInstanceRequestDto(
                "my-nb", "us-central1-a", "n2-standard-8", 100, null);

        assertThatCode(() -> enforcement.enforceCreate(usage, request)).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // enforceCreate — system cap
    // -------------------------------------------------------------------------

    @Test
    void enforceCreate_atSystemCap_throwsSystemMaxInstancesExceeded() {
        UserQuotaUsageDto usage = usageSnapshot(limits(3, Integer.MAX_VALUE, List.of(), null, List.of()), totals(3, 0));
        CreateInstanceRequestDto request = new CreateInstanceRequestDto("nb", "us-central1-a", "e2-standard-4", null, null);

        assertViolationCodes(usage, request, "SYSTEM_MAX_INSTANCES_EXCEEDED");
    }

    // -------------------------------------------------------------------------
    // enforceCreate — manager instance limit
    // -------------------------------------------------------------------------

    @Test
    void enforceCreate_atManagerInstanceLimit_throwsMaxInstancesExceeded() {
        UserQuotaUsageDto usage = usageSnapshot(limits(2, Integer.MAX_VALUE, List.of(), null, List.of()), totals(2, 0));
        CreateInstanceRequestDto request = new CreateInstanceRequestDto("nb", "us-central1-a", "e2-standard-4", null, null);

        assertViolationCodes(usage, request, "MAX_INSTANCES_EXCEEDED");
    }

    // -------------------------------------------------------------------------
    // enforceCreate — vCPU
    // -------------------------------------------------------------------------

    @Test
    void enforceCreate_vcpuWouldExceedLimit_throwsMaxVcpuExceeded() {
        // current = 12, requesting n2-standard-8 (8 vCPUs), limit = 16 → 12+8=20 > 16
        UserQuotaUsageDto usage = usageSnapshot(limits(3, 16, List.of(), null, List.of()), totals(1, 12));
        CreateInstanceRequestDto request = new CreateInstanceRequestDto("nb", "us-central1-a", "n2-standard-8", null, null);

        assertViolationCodes(usage, request, "MAX_VCPU_EXCEEDED");
    }

    @Test
    void enforceCreate_unlimitedVcpu_doesNotCheckVcpu() {
        UserQuotaUsageDto usage = usageSnapshot(limits(3, Integer.MAX_VALUE, List.of(), null, List.of()), totals(0, 0));
        CreateInstanceRequestDto request = new CreateInstanceRequestDto("nb", "us-central1-a", "n2-standard-96", null, null);

        assertThatCode(() -> enforcement.enforceCreate(usage, request)).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // enforceCreate — machine type allowlist
    // -------------------------------------------------------------------------

    @Test
    void enforceCreate_machineTypeNotAllowed_throwsMachineTypeNotAllowed() {
        UserQuotaUsageDto usage = usageSnapshot(
                limits(3, Integer.MAX_VALUE, List.of("e2-standard-4"), null, List.of()), totals(0, 0));
        CreateInstanceRequestDto request = new CreateInstanceRequestDto("nb", "us-central1-a", "n2-standard-8", null, null);

        assertViolationCodes(usage, request, "MACHINE_TYPE_NOT_ALLOWED");
    }

    @Test
    void enforceCreate_emptyMachineTypeAllowlist_allowsAnyMachineType() {
        UserQuotaUsageDto usage = usageSnapshot(limits(3, Integer.MAX_VALUE, List.of(), null, List.of()), totals(0, 0));
        CreateInstanceRequestDto request = new CreateInstanceRequestDto("nb", "us-central1-a", "n2-standard-96", null, null);

        assertThatCode(() -> enforcement.enforceCreate(usage, request)).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // enforceCreate — zone allowlist
    // -------------------------------------------------------------------------

    @Test
    void enforceCreate_zoneNotAllowed_throwsZoneNotAllowed() {
        UserQuotaUsageDto usage = usageSnapshot(
                limits(3, Integer.MAX_VALUE, List.of(), null, List.of("us-central1-a")), totals(0, 0));
        CreateInstanceRequestDto request = new CreateInstanceRequestDto("nb", "europe-west1-b", "e2-standard-4", null, null);

        assertViolationCodes(usage, request, "ZONE_NOT_ALLOWED");
    }

    @Test
    void enforceCreate_emptyZoneAllowlist_allowsAnyZone() {
        UserQuotaUsageDto usage = usageSnapshot(limits(3, Integer.MAX_VALUE, List.of(), null, List.of()), totals(0, 0));
        CreateInstanceRequestDto request = new CreateInstanceRequestDto("nb", "europe-west1-b", "e2-standard-4", null, null);

        assertThatCode(() -> enforcement.enforceCreate(usage, request)).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // enforceCreate — boot disk
    // -------------------------------------------------------------------------

    @Test
    void enforceCreate_diskExceedsLimit_throwsDiskTooLarge() {
        UserQuotaUsageDto usage = usageSnapshot(limits(3, Integer.MAX_VALUE, List.of(), 200, List.of()), totals(0, 0));
        CreateInstanceRequestDto request = new CreateInstanceRequestDto("nb", "us-central1-a", "e2-standard-4", 300, null);

        assertViolationCodes(usage, request, "DISK_TOO_LARGE");
    }

    @Test
    void enforceCreate_diskAtLimit_doesNotThrow() {
        UserQuotaUsageDto usage = usageSnapshot(limits(3, Integer.MAX_VALUE, List.of(), 200, List.of()), totals(0, 0));
        CreateInstanceRequestDto request = new CreateInstanceRequestDto("nb", "us-central1-a", "e2-standard-4", 200, null);

        assertThatCode(() -> enforcement.enforceCreate(usage, request)).doesNotThrowAnyException();
    }

    @Test
    void enforceCreate_nullBootDiskGbInRequest_skipsBootDiskCheck() {
        UserQuotaUsageDto usage = usageSnapshot(limits(3, Integer.MAX_VALUE, List.of(), 200, List.of()), totals(0, 0));
        CreateInstanceRequestDto request = new CreateInstanceRequestDto("nb", "us-central1-a", "e2-standard-4", null, null);

        assertThatCode(() -> enforcement.enforceCreate(usage, request)).doesNotThrowAnyException();
    }

    @Test
    void enforceCreate_nullBootDiskGbLimit_skipsBootDiskCheck() {
        UserQuotaUsageDto usage = usageSnapshot(limits(3, Integer.MAX_VALUE, List.of(), null, List.of()), totals(0, 0));
        CreateInstanceRequestDto request = new CreateInstanceRequestDto("nb", "us-central1-a", "e2-standard-4", 500, null);

        assertThatCode(() -> enforcement.enforceCreate(usage, request)).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // enforceCreate — multiple violations
    // -------------------------------------------------------------------------

    @Test
    void enforceCreate_multipleViolations_allReported() {
        // Forbidden machine type AND forbidden zone
        UserQuotaUsageDto usage = usageSnapshot(
                limits(3, Integer.MAX_VALUE, List.of("e2-standard-4"), null, List.of("us-central1-a")),
                totals(0, 0)
        );
        CreateInstanceRequestDto request = new CreateInstanceRequestDto(
                "nb", "europe-west1-b", "n2-standard-8", null, null);

        assertViolationCodes(usage, request, "MACHINE_TYPE_NOT_ALLOWED", "ZONE_NOT_ALLOWED");
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private void assertViolationCodes(UserQuotaUsageDto usage, CreateInstanceRequestDto request, String... codes) {
        assertThatThrownBy(() -> enforcement.enforceCreate(usage, request))
                .isInstanceOf(QuotaViolationException.class)
                .satisfies(ex -> {
                    var violations = ((QuotaViolationException) ex).getViolations();
                    assertThat(violations).extracting(ViolationDto::code).containsExactlyInAnyOrder(codes);
                });
    }

    private UserQuotaUsageDto usageSnapshot(EffectiveLimitsDto limits, UsageTotalsDto usage) {
        int remaining = Math.max(0, limits.maxInstances() - usage.instances());
        return new UserQuotaUsageDto(
                "p1", "g1", "u1", OffsetDateTime.now(),
                SystemCapsDto.INSTANCE, limits, usage,
                new UsageTotalsDto(remaining, 0),
                "USER_QUOTA"
        );
    }

    private EffectiveLimitsDto limits(int maxInstances, int maxTotalVcpu,
                                      List<String> allowedMachineTypes,
                                      Integer maxBootDiskGb,
                                      List<String> allowedZones) {
        return new EffectiveLimitsDto(maxInstances, maxTotalVcpu, allowedMachineTypes, maxBootDiskGb, allowedZones);
    }

    private UsageTotalsDto totals(int instances, int vcpu) {
        return new UsageTotalsDto(instances, vcpu);
    }

    private WorkbenchInstanceDto instance(String id, String machineType) {
        return new WorkbenchInstanceDto(id, id, "RUNNING", machineType, "us-central1-a", "bank1", OffsetDateTime.now());
    }

    private UserQuotaLookupResultDto globalDefaultLookup(String projectId, String groupId, String userId) {
        return new UserQuotaLookupResultDto(projectId, groupId, userId, "NONE", null, "GLOBAL_DEFAULT", null, null);
    }

    private UserQuotaLookupResultDto userQuotaLookup(String projectId, String groupId, String userId, UserQuotaSpecDto spec) {
        var quota = new UserQuotaDto(projectId, groupId, userId, spec, OffsetDateTime.now(), "manager1");
        return new UserQuotaLookupResultDto(projectId, groupId, userId, "EXPLICIT", quota, "USER_QUOTA", null, spec);
    }

    private UserQuotaSpecDto spec(int maxInstances, int maxTotalVcpu,
                                  List<String> allowedMachineTypes,
                                  Integer maxBootDiskGb,
                                  List<String> allowedZones) {
        return new UserQuotaSpecDto(maxInstances, maxTotalVcpu, allowedMachineTypes, maxBootDiskGb, allowedZones);
    }
}
