package com.dbs.symphony.controller;

import com.dbs.symphony.dto.BatchForGroupRequestDto;
import com.dbs.symphony.dto.BatchUserIdsRequestDto;
import com.dbs.symphony.dto.BatchUserQuotasResponseDto;
import com.dbs.symphony.dto.BatchUserUsageRequestDto;
import com.dbs.symphony.dto.BatchUserUsageResponseDto;
import com.dbs.symphony.dto.EffectiveQuotaResultDto;
import com.dbs.symphony.dto.PagedEffectiveQuotaResultsDto;
import com.dbs.symphony.dto.UserQuotaLookupResultDto;
import com.dbs.symphony.dto.UserQuotaUsageDto;
import com.dbs.symphony.dto.UserSummaryDto;
import com.dbs.symphony.security.AuthorizationService;
import com.dbs.symphony.security.CurrentPrincipal;
import com.dbs.symphony.service.DirectoryService;
import com.dbs.symphony.service.QuotaEnforcementService;
import com.dbs.symphony.service.QuotaService;
import com.dbs.symphony.util.PageTokens;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@RestController
@Validated
@PreAuthorize("hasAnyAuthority('SCOPE_workbench.manager', 'workbench.manager')")
public class BatchController {

    private static final String ID_PATTERN = Patterns.ID;

    private final QuotaService quotaService;
    private final QuotaEnforcementService enforcement;
    private final DirectoryService directoryService;
    private final AuthorizationService authz;

    public BatchController(QuotaService quotaService,
                           QuotaEnforcementService enforcement,
                           DirectoryService directoryService,
                           AuthorizationService authz) {
        this.quotaService = quotaService;
        this.enforcement = enforcement;
        this.directoryService = directoryService;
        this.authz = authz;
    }

    @PostMapping("/v1/projects/{projectId}/managed-groups/{groupId}/quotas/users:batchGet")
    public BatchUserQuotasResponseDto batchGetQuotas(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId,
            @PathVariable @Pattern(regexp = ID_PATTERN) String groupId,
            @Valid @RequestBody BatchUserIdsRequestDto request
    ) {
        authz.assertManagesGroup(CurrentPrincipal.principal(), groupId);

        List<UserQuotaLookupResultDto> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (String userId : request.userIds()) {
            try {
                items.add(quotaService.getUserQuota(projectId, groupId, userId));
            } catch (Exception e) {
                warnings.add("Failed to fetch quota for user " + userId + ": " + e.getMessage());
            }
        }

        var defaultQuota = request.includeDefault() ? quotaService.getDefaultQuota(projectId) : null;
        return new BatchUserQuotasResponseDto(items, defaultQuota, warnings);
    }

    @PostMapping("/v1/projects/{projectId}/managed-groups/{groupId}/usage/users:batchGet")
    public BatchUserUsageResponseDto batchGetUsage(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId,
            @PathVariable @Pattern(regexp = ID_PATTERN) String groupId,
            @Valid @RequestBody BatchUserUsageRequestDto request
    ) {
        authz.assertManagesGroup(CurrentPrincipal.principal(), groupId);

        List<UserQuotaUsageDto> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        record UsageResult(UserQuotaUsageDto usage, String memberId, String error) {}
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = request.userIds().stream()
                    .map(memberId -> CompletableFuture.supplyAsync(() -> {
                        try {
                            // memberId is the 1bankId: used as both the quota DB key (userId)
                            // and the CAI label value (bankUserId) — they coincide in the batch case.
                            return new UsageResult(
                                    enforcement.computeUsage(projectId, groupId, memberId, memberId),
                                    memberId, null);
                        } catch (Exception e) {
                            return new UsageResult(null, memberId, e.getMessage());
                        }
                    }, executor))
                    .toList();
            for (var f : futures) {
                UsageResult r = f.join();
                if (r.error() != null) warnings.add("Failed to fetch usage for user " + r.memberId() + ": " + r.error());
                else items.add(r.usage());
            }
        }

        return new BatchUserUsageResponseDto(items, warnings);
    }

    @PostMapping("/v1/projects/{projectId}/managed-groups/{groupId}/effective-quota:batchForGroup")
    public PagedEffectiveQuotaResultsDto batchForGroup(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId,
            @PathVariable @Pattern(regexp = ID_PATTERN) String groupId,
            @RequestBody(required = false) BatchForGroupRequestDto request
    ) {
        int pageSize   = (request != null && request.pageSize() > 0) ? request.pageSize() : 200;
        String pageToken = request != null ? request.pageToken() : null;

        authz.assertManagesGroup(CurrentPrincipal.principal(), groupId);

        List<UserSummaryDto> allMembers = directoryService.listGroupMembers(groupId);

        int offset = PageTokens.decode(pageToken);
        int end = Math.min(offset + pageSize, allMembers.size());
        List<UserSummaryDto> page = allMembers.subList(offset, end);
        String nextPageToken = end < allMembers.size() ? PageTokens.encode(end) : null;

        List<EffectiveQuotaResultDto> results = new ArrayList<>();
        record GroupResult(EffectiveQuotaResultDto result, String error) {}
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = page.stream()
                    .map(member -> CompletableFuture.supplyAsync(() -> {
                        // member.userId() is the 1bankId from LDAP — used as both quota key and CAI label.
                        String memberId = member.userId();
                        try {
                            UserQuotaUsageDto usage =
                                    enforcement.computeUsage(projectId, groupId, memberId, memberId);
                            // Derive EXPLICIT/NONE from effectiveSource (USER_QUOTA → EXPLICIT, else → NONE)
                            String status = "USER_QUOTA".equals(usage.effectiveSource()) ? "EXPLICIT" : "NONE";
                            return new GroupResult(new EffectiveQuotaResultDto(
                                    projectId, groupId, memberId,
                                    usage.systemCaps(), usage.effectiveLimits(),
                                    status, usage.effectiveSource(),
                                    usage.usage(), usage.remaining()), null);
                        } catch (Exception e) {
                            return new GroupResult(null, e.getMessage());
                        }
                    }, executor))
                    .toList();
            for (var f : futures) {
                GroupResult r = f.join();
                if (r.result() != null) results.add(r.result());
            }
        }

        return new PagedEffectiveQuotaResultsDto(results, nextPageToken);
    }
}
