package com.dbs.symphony.controller;

import com.dbs.symphony.dto.AsyncOperationDto;
import com.dbs.symphony.exception.NotFoundException;
import com.dbs.symphony.security.CurrentPrincipal;
import com.dbs.symphony.service.OperationService;
import com.dbs.symphony.service.WorkbenchService;
import com.dbs.symphony.util.OperationIdCodec;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class OperationsController {

    private final WorkbenchService workbenchService;
    private final OperationService operationService;

    public OperationsController(WorkbenchService workbenchService, OperationService operationService) {
        this.workbenchService = workbenchService;
        this.operationService = operationService;
    }

    @GetMapping("/v1/operations/{operationId}")
    @PreAuthorize("isAuthenticated()")
    public AsyncOperationDto getOperation(@PathVariable String operationId) {
        String lroName;
        try {
            lroName = OperationIdCodec.decode(operationId);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Operation not found: " + operationId);
        }
        operationService.assertOwnership(operationId, CurrentPrincipal.principal());
        return workbenchService.getOperation(lroName);
    }
}
