package com.dbs.symphony.controller;

import com.dbs.symphony.dto.UserQuotaDto;
import com.dbs.symphony.dto.UserQuotaLookupResultDto;
import com.dbs.symphony.dto.UserQuotaSpecDto;
import com.dbs.symphony.security.AuthorizationService;
import com.dbs.symphony.security.CurrentPrincipal;
import com.dbs.symphony.service.QuotaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@PreAuthorize("hasAnyAuthority('SCOPE_workbench.manager', 'workbench.manager')")
public class QuotaController {

    private final QuotaService quotaService;
    private final AuthorizationService authz;

    public QuotaController(QuotaService quotaService, AuthorizationService authz) {
        this.quotaService = quotaService;
        this.authz = authz;
    }

    private static final String ID_PATTERN = Patterns.ID;

    @PutMapping("/v1/projects/{projectId}/managed-groups/{groupId}/quotas/users/{userId}")
    public UserQuotaDto upsertUserQuotaInGroup(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId,
            @PathVariable @Pattern(regexp = ID_PATTERN) String groupId,
            @PathVariable @Pattern(regexp = ID_PATTERN) String userId,
            @Valid @RequestBody UserQuotaSpecDto spec
    ) {
        authz.assertManagesGroup(CurrentPrincipal.principal(), groupId);
        authz.assertUserInGroup(userId, groupId);
        return quotaService.upsertUserQuota(projectId, groupId, userId, spec);
    }

    @GetMapping("/v1/projects/{projectId}/managed-groups/{groupId}/quotas/users/{userId}")
    public UserQuotaLookupResultDto getUserQuotaInGroup(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId,
            @PathVariable @Pattern(regexp = ID_PATTERN) String groupId,
            @PathVariable @Pattern(regexp = ID_PATTERN) String userId
    ) {
        authz.assertManagesGroup(CurrentPrincipal.principal(), groupId);
        authz.assertUserInGroup(userId, groupId);
        return quotaService.getUserQuota(projectId, groupId, userId);
    }

    @DeleteMapping("/v1/projects/{projectId}/managed-groups/{groupId}/quotas/users/{userId}")
    public ResponseEntity<Void> deleteUserQuotaInGroup(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId,
            @PathVariable @Pattern(regexp = ID_PATTERN) String groupId,
            @PathVariable @Pattern(regexp = ID_PATTERN) String userId
    ) {
        authz.assertManagesGroup(CurrentPrincipal.principal(), groupId);
        authz.assertUserInGroup(userId, groupId);
        quotaService.deleteUserQuota(projectId, groupId, userId);
        return ResponseEntity.noContent().build();
    }
}
