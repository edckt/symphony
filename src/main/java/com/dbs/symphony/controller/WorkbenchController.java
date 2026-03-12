package com.dbs.symphony.controller;

import com.dbs.symphony.config.CaiProperties;
import com.dbs.symphony.config.InstanceSizeProperties.SizeDefinition;
import com.dbs.symphony.dto.AsyncOperationDto;
import com.dbs.symphony.dto.CreateInstanceRequestDto;
import com.dbs.symphony.dto.InstanceSizeDto;
import com.dbs.symphony.dto.WorkbenchInstanceDto;
import com.dbs.symphony.exception.ConflictException;
import com.dbs.symphony.exception.NotFoundException;
import com.dbs.symphony.security.CurrentPrincipal;
import com.dbs.symphony.service.CaiService;
import com.dbs.symphony.service.InstanceSizeCatalogService;
import com.dbs.symphony.service.OperationService;
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
    private final InstanceSizeCatalogService sizeCatalog;
    private final OperationService operationService;

    public WorkbenchController(CaiService caiService,
                               QuotaEnforcementService enforcement,
                               CaiProperties caiProperties,
                               WorkbenchService workbenchService,
                               InstanceSizeCatalogService sizeCatalog,
                               OperationService operationService) {
        this.caiService = caiService;
        this.enforcement = enforcement;
        this.caiProperties = caiProperties;
        this.workbenchService = workbenchService;
        this.sizeCatalog = sizeCatalog;
        this.operationService = operationService;
    }

    @GetMapping("/v1/projects/{projectId}/workbench/instance-sizes")
    public List<InstanceSizeDto> listInstanceSizes(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId,
            @RequestParam @Pattern(regexp = ID_PATTERN) String groupId
    ) {
        String userId = CurrentPrincipal.principal();
        String bankUserId = resolveBankUserId();
        var usageSnapshot = enforcement.computeUsage(projectId, groupId, userId, bankUserId);
        return sizeCatalog.listSizes(usageSnapshot.effectiveLimits().allowedMachineTypes());
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

        SizeDefinition size = sizeCatalog.resolve(request.instanceSize());

        var usageSnapshot = enforcement.computeUsage(projectId, groupId, userId, bankUserId);
        enforcement.enforceCreate(usageSnapshot, size.getMachineType(), request.zone(),
                size.getBootDiskGb(), size.isRequiresApproval());

        AsyncOperationDto op = workbenchService.createInstance(projectId, bankUserId, request,
                size.getMachineType(), size.getBootDiskGb());
        operationService.record(op, userId, projectId);
        return ResponseEntity.accepted().body(op);
    }

    @GetMapping("/v1/projects/{projectId}/workbench/instances/{instanceId}")
    public WorkbenchInstanceDto getInstance(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId,
            @PathVariable @Pattern(regexp = ID_PATTERN) String instanceId
    ) {
        return resolveOwnedInstance(projectId, instanceId);
    }

    @DeleteMapping("/v1/projects/{projectId}/workbench/instances/{instanceId}")
    public ResponseEntity<AsyncOperationDto> deleteInstance(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId,
            @PathVariable @Pattern(regexp = ID_PATTERN) String instanceId
    ) {
        AsyncOperationDto op = workbenchService.deleteInstance(
                instanceName(projectId, resolveOwnedInstance(projectId, instanceId)));
        operationService.record(op, CurrentPrincipal.principal(), projectId);
        return ResponseEntity.accepted().body(op);
    }

    @PostMapping("/v1/projects/{projectId}/workbench/instances/{instanceId}:start")
    public ResponseEntity<AsyncOperationDto> startInstance(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId,
            @PathVariable @Pattern(regexp = ID_PATTERN) String instanceId
    ) {
        WorkbenchInstanceDto instance = resolveOwnedInstance(projectId, instanceId);
        if (!"STOPPED".equals(instance.state())) {
            throw new ConflictException("Instance is not in a startable state: current state is " + instance.state());
        }
        AsyncOperationDto op = workbenchService.startInstance(instanceName(projectId, instance));
        operationService.record(op, CurrentPrincipal.principal(), projectId);
        return ResponseEntity.accepted().body(op);
    }

    @PostMapping("/v1/projects/{projectId}/workbench/instances/{instanceId}:stop")
    public ResponseEntity<AsyncOperationDto> stopInstance(
            @PathVariable @Pattern(regexp = ID_PATTERN) String projectId,
            @PathVariable @Pattern(regexp = ID_PATTERN) String instanceId
    ) {
        WorkbenchInstanceDto instance = resolveOwnedInstance(projectId, instanceId);
        if (!"ACTIVE".equals(instance.state())) {
            throw new ConflictException("Instance is not in a stoppable state: current state is " + instance.state());
        }
        AsyncOperationDto op = workbenchService.stopInstance(instanceName(projectId, instance));
        operationService.record(op, CurrentPrincipal.principal(), projectId);
        return ResponseEntity.accepted().body(op);
    }

    /** CAI lookup: verifies ownership and returns the instance DTO. */
    private WorkbenchInstanceDto resolveOwnedInstance(String projectId, String instanceId) {
        String bankUserId = resolveBankUserId();
        return caiService.listUserInstances(projectId, bankUserId).stream()
                .filter(i -> instanceId.equals(i.id()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Instance not found or not owned by current user: " + instanceId));
    }

    private static String instanceName(String projectId, WorkbenchInstanceDto instance) {
        return "projects/" + projectId + "/locations/" + instance.zone() + "/instances/" + instance.id();
    }

    private String resolveBankUserId() {
        return CurrentPrincipal.bankUserId(caiProperties.getUserIdClaim());
    }
}
