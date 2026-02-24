package com.dbs.symphony.controller;

import com.dbs.symphony.dto.LatestQuotaAuditResultDto;
import com.dbs.symphony.security.AuthorizationService;
import com.dbs.symphony.security.CurrentPrincipal;
import com.dbs.symphony.service.AuditService;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@PreAuthorize("hasAnyAuthority('SCOPE_workbench.manager', 'workbench.manager')")
public class AuditController {

    private final AuditService auditService;
    private final AuthorizationService authz;

    public AuditController(AuditService auditService, AuthorizationService authz) {
        this.auditService = auditService;
        this.authz = authz;
    }

    private static final String ID_PATTERN = Patterns.ID;

    @GetMapping("/v1/projects/{projectId}/managed-groups/{groupId}/quotas/users/{userId}/audit/latest")
    public LatestQuotaAuditResultDto getLatestUserQuotaAudit(
        @PathVariable @Pattern(regexp = ID_PATTERN) String projectId,
        @PathVariable @Pattern(regexp = ID_PATTERN) String groupId,
        @PathVariable @Pattern(regexp = ID_PATTERN) String userId
    ) {
        authz.assertManagesGroup(CurrentPrincipal.principal(), groupId);
        authz.assertUserInGroup(userId, groupId);
        var latest = auditService.latestEvent(projectId, groupId, userId).orElse(null);
        return new LatestQuotaAuditResultDto(
            projectId,
            groupId,
            userId,
            latest == null ? "NONE" : "PRESENT",
            latest
        );
    }
}
