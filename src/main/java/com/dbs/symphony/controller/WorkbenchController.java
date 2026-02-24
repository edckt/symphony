package com.dbs.symphony.controller;

import com.dbs.symphony.config.CaiProperties;
import com.dbs.symphony.dto.AsyncOperationDto;
import com.dbs.symphony.dto.CreateInstanceRequestDto;
import com.dbs.symphony.dto.WorkbenchInstanceDto;
import com.dbs.symphony.exception.NotFoundException;
import com.dbs.symphony.security.CurrentPrincipal;
import com.dbs.symphony.service.CaiService;
import com.dbs.symphony.service.QuotaEnforcementService;
import com.dbs.symphony.service.WorkbenchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@PreAuthorize("hasAnyAuthority('SCOPE_workbench.user', 'workbench.user')")
public class WorkbenchController {

    private static final String ID_PATTERN = Patterns.ID;

    private final CaiService caiService;
    private final QuotaEnforcementService enforcement;
    private final CaiProperties caiProperties;
    private final WorkbenchService workbenchService;

    public WorkbenchController(CaiService caiService,
                               QuotaEnforcementService enforcement,
                               CaiProperties caiProperties,
                               WorkbenchService workbenchService) {
        this.caiService = caiService;
        this.enforcement = enforcement;
        this.caiProperties = caiProperties;
        this.workbenchService = workbenchService;
    }

    @GetMapping("/v1/projects/{projectId}/workbench/instances")
    public List<WorkbenchInstanceDto> listInstances(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId
    ) {
        String bankUserId = resolveBankUserId();
        return caiService.listUserInstances(projectId, bankUserId);
    }

    @PostMapping("/v1/projects/{projectId}/workbench/instances")
    public ResponseEntity<AsyncOperationDto> createInstance(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId,
            @RequestParam @Pattern(regexp = ID_PATTERN) String groupId,
            @Valid @RequestBody CreateInstanceRequestDto request
    ) {
        String userId = CurrentPrincipal.principal();
        String bankUserId = resolveBankUserId();

        var usageSnapshot = enforcement.computeUsage(projectId, groupId, userId, bankUserId);
        enforcement.enforceCreate(usageSnapshot, request);

        return ResponseEntity.accepted()
                .body(workbenchService.createInstance(projectId, bankUserId, request));
    }

    @GetMapping("/v1/projects/{projectId}/workbench/instances/{instanceId}")
    public WorkbenchInstanceDto getInstance(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId,
            @PathVariable @Pattern(regexp = ID_PATTERN) String instanceId
    ) {
        String bankUserId = resolveBankUserId();
        return caiService.listUserInstances(projectId, bankUserId).stream()
                .filter(i -> instanceId.equals(i.id()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Instance not found or not owned by current user: " + instanceId));
    }

    @DeleteMapping("/v1/projects/{projectId}/workbench/instances/{instanceId}")
    public ResponseEntity<AsyncOperationDto> deleteInstance(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId,
            @PathVariable @Pattern(regexp = ID_PATTERN) String instanceId
    ) {
        return ResponseEntity.accepted()
                .body(workbenchService.deleteInstance(resolveInstanceName(projectId, instanceId)));
    }

    @PostMapping("/v1/projects/{projectId}/workbench/instances/{instanceId}:start")
    public ResponseEntity<AsyncOperationDto> startInstance(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId,
            @PathVariable @Pattern(regexp = ID_PATTERN) String instanceId
    ) {
        String instanceName = resolveInstanceName(projectId, instanceId);
        return ResponseEntity.accepted().body(workbenchService.startInstance(instanceName));
    }

    @PostMapping("/v1/projects/{projectId}/workbench/instances/{instanceId}:stop")
    public ResponseEntity<AsyncOperationDto> stopInstance(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId,
            @PathVariable @Pattern(regexp = ID_PATTERN) String instanceId
    ) {
        String instanceName = resolveInstanceName(projectId, instanceId);
        return ResponseEntity.accepted().body(workbenchService.stopInstance(instanceName));
    }

    @GetMapping("/v1/workbench/operations")
    public AsyncOperationDto getOperation(@RequestParam String name) {
        return workbenchService.getOperation(name);
    }

    /** CAI lookup: verifies ownership and returns the full GCP resource name including zone. */
    private String resolveInstanceName(String projectId, String instanceId) {
        String bankUserId = resolveBankUserId();
        WorkbenchInstanceDto instance = caiService.listUserInstances(projectId, bankUserId).stream()
                .filter(i -> instanceId.equals(i.id()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Instance not found or not owned by current user: " + instanceId));
        return "projects/" + projectId + "/locations/" + instance.zone() + "/instances/" + instanceId;
    }

    private String resolveBankUserId() {
        return CurrentPrincipal.bankUserId(caiProperties.getUserIdClaim());
    }
}
