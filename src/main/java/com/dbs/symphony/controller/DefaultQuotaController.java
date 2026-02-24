package com.dbs.symphony.controller;

import com.dbs.symphony.dto.DefaultQuotaDto;
import com.dbs.symphony.dto.UserQuotaSpecDto;
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
public class DefaultQuotaController {

    private static final String ID_PATTERN = Patterns.ID;

    private final QuotaService quotaService;

    public DefaultQuotaController(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @GetMapping("/v1/projects/{projectId}/quotas/default")
    public DefaultQuotaDto getDefaultQuota(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId
    ) {
        return quotaService.getDefaultQuota(projectId);
    }

    @PutMapping("/v1/projects/{projectId}/quotas/default")
    public DefaultQuotaDto upsertDefaultQuota(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId,
            @Valid @RequestBody UserQuotaSpecDto spec
    ) {
        return quotaService.upsertDefaultQuota(projectId, spec);
    }

    @DeleteMapping("/v1/projects/{projectId}/quotas/default")
    public ResponseEntity<Void> deleteDefaultQuota(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId
    ) {
        quotaService.deleteDefaultQuota(projectId);
        return ResponseEntity.noContent().build();
    }
}
