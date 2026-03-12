package com.dbs.symphony.service;

import com.dbs.symphony.dto.AsyncOperationDto;
import com.dbs.symphony.dto.CreateInstanceRequestDto;

public interface WorkbenchService {
    /**
     * Creates a Workbench instance for the given user in the project.
     * machineType and bootDiskGb are pre-resolved from the chosen instance size.
     * Returns immediately with a PENDING operation; caller polls getOperation() to check completion.
     */
    AsyncOperationDto createInstance(String projectId, String bankUserId, CreateInstanceRequestDto request,
                                     String machineType, int bootDiskGb);

    /**
     * Deletes the Workbench instance identified by its full GCP resource name.
     * Returns immediately with a PENDING operation; caller polls getOperation() to check completion.
     */
    AsyncOperationDto deleteInstance(String instanceName);

    /**
     * Starts a stopped Workbench instance identified by its full GCP resource name.
     * Returns immediately with a PENDING operation; caller polls getOperation() to check completion.
     */
    AsyncOperationDto startInstance(String instanceName);

    /**
     * Stops a running Workbench instance identified by its full GCP resource name.
     * Returns immediately with a PENDING operation; caller polls getOperation() to check completion.
     */
    AsyncOperationDto stopInstance(String instanceName);

    /**
     * Returns the current status of a GCP LRO by its full operation name
     * (e.g. "projects/my-project/locations/asia-southeast1-b/operations/operation-1234").
     */
    AsyncOperationDto getOperation(String operationName);
}
