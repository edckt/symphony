package com.dbs.symphony.controller;

import com.dbs.symphony.config.CaiProperties;
import com.dbs.symphony.dto.UserQuotaUsageDto;
import com.dbs.symphony.security.CurrentPrincipal;
import com.dbs.symphony.service.QuotaEnforcementService;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@PreAuthorize("hasAnyAuthority('SCOPE_workbench.user', 'workbench.user')")
public class UsageController {

    private static final String ID_PATTERN = Patterns.ID;

    private final QuotaEnforcementService enforcement;
    private final CaiProperties caiProperties;

    public UsageController(QuotaEnforcementService enforcement, CaiProperties caiProperties) {
        this.enforcement = enforcement;
        this.caiProperties = caiProperties;
    }

    @GetMapping("/v1/projects/{projectId}/self/usage")
    public UserQuotaUsageDto getSelfUsage(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId,
            @RequestParam @Pattern(regexp = ID_PATTERN) String groupId
    ) {
        String userId = CurrentPrincipal.principal();
        String bankUserId = CurrentPrincipal.bankUserId(caiProperties.getUserIdClaim());
        return enforcement.computeUsage(projectId, groupId, userId, bankUserId);
    }
}
