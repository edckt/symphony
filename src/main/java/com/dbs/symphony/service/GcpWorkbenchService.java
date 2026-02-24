package com.dbs.symphony.service;

import com.dbs.symphony.dto.AsyncOperationDto;
import com.dbs.symphony.dto.CreateInstanceRequestDto;
import com.dbs.symphony.dto.ErrorDto;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.notebooks.v2.BootDisk;
import com.google.cloud.notebooks.v2.CreateInstanceRequest;
import com.google.cloud.notebooks.v2.DeleteInstanceRequest;
import com.google.cloud.notebooks.v2.GceSetup;
import com.google.cloud.notebooks.v2.StartInstanceRequest;
import com.google.cloud.notebooks.v2.StopInstanceRequest;
import com.google.cloud.notebooks.v2.Instance;
import com.google.cloud.notebooks.v2.NetworkInterface;
import com.google.cloud.notebooks.v2.NotebookServiceClient;
import com.google.cloud.notebooks.v2.OperationMetadata;
import com.google.longrunning.Operation;
import com.google.protobuf.Empty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Real Workbench service that calls the GCP Notebooks API v2 (notebooks.googleapis.com/v2).
 * Active when app.workbench.enabled=true.
 *
 * Fire-and-return pattern: createInstance starts the GCP LRO and returns immediately with PENDING.
 * The caller polls getOperation() using the returned operation name.
 */
@Service
@ConditionalOnProperty(name = "app.workbench.enabled", havingValue = "true")
public class GcpWorkbenchService implements WorkbenchService {

    private static final Logger log = LoggerFactory.getLogger(GcpWorkbenchService.class);

    private final NotebookServiceClient notebookServiceClient;

    public GcpWorkbenchService(NotebookServiceClient notebookServiceClient) {
        this.notebookServiceClient = notebookServiceClient;
    }

    @Override
    public AsyncOperationDto createInstance(String projectId, String bankUserId, CreateInstanceRequestDto request) {
        String instanceId = deriveInstanceId(request.displayName());
        String normalizedUserId = GcpLabels.normalizeUserId(bankUserId);
        String parent = "projects/" + projectId + "/locations/" + request.zone();

        Map<String, String> labels = new HashMap<>();
        if (request.labels() != null) {
            labels.putAll(request.labels());
        }
        labels.put("notebooks-product", "true");
        labels.put("user", normalizedUserId);

        long bootDiskGb = Math.max(150L, request.bootDiskGb() != null ? (long) request.bootDiskGb() : 150L);
        BootDisk bootDisk = BootDisk.newBuilder()
                .setDiskSizeGb(bootDiskGb)
                .build();

        NetworkInterface networkInterface = NetworkInterface.newBuilder()
                .setNetwork("projects/" + projectId + "/global/networks/default")
                .build();

        GceSetup gceSetup = GceSetup.newBuilder()
                .setMachineType(request.machineType())
                .setBootDisk(bootDisk)
                .addNetworkInterfaces(networkInterface)
                .setDisablePublicIp(true)
                .build();

        Instance instance = Instance.newBuilder()
                .putAllLabels(labels)
                .setGceSetup(gceSetup)
                .build();

        CreateInstanceRequest createRequest = CreateInstanceRequest.newBuilder()
                .setParent(parent)
                .setInstanceId(instanceId)
                .setInstance(instance)
                .build();

        log.info("Creating Workbench instance: parent={} instanceId={} userId={}", parent, instanceId, normalizedUserId);

        OperationFuture<Instance, OperationMetadata> future =
                notebookServiceClient.createInstanceAsync(createRequest);

        return awaitOperationName(future, "creation");
    }

    @Override
    public AsyncOperationDto deleteInstance(String instanceName) {
        log.info("Deleting Workbench instance: name={}", instanceName);

        DeleteInstanceRequest deleteRequest = DeleteInstanceRequest.newBuilder()
                .setName(instanceName)
                .build();

        OperationFuture<Empty, OperationMetadata> future =
                notebookServiceClient.deleteInstanceAsync(deleteRequest);

        return awaitOperationName(future, "deletion");
    }

    @Override
    public AsyncOperationDto startInstance(String instanceName) {
        log.info("Starting Workbench instance: name={}", instanceName);

        StartInstanceRequest startRequest = StartInstanceRequest.newBuilder()
                .setName(instanceName)
                .build();

        OperationFuture<Instance, OperationMetadata> future =
                notebookServiceClient.startInstanceAsync(startRequest);

        return awaitOperationName(future, "start");
    }

    @Override
    public AsyncOperationDto stopInstance(String instanceName) {
        log.info("Stopping Workbench instance: name={}", instanceName);

        StopInstanceRequest stopRequest = StopInstanceRequest.newBuilder()
                .setName(instanceName)
                .build();

        OperationFuture<Instance, OperationMetadata> future =
                notebookServiceClient.stopInstanceAsync(stopRequest);

        return awaitOperationName(future, "stop");
    }

    @Override
    public AsyncOperationDto getOperation(String operationName) {
        Operation op = notebookServiceClient.getOperationsClient().getOperation(operationName);
        String status;
        String resourceId = null;
        OffsetDateTime doneAt = null;
        ErrorDto error = null;

        if (op.getDone()) {
            doneAt = OffsetDateTime.now();
            if (op.hasError()) {
                status = "FAILED";
                error = new ErrorDto(op.getError().getMessage(), "GCP_ERROR", null);
            } else {
                status = "DONE";
                try {
                    resourceId = op.getResponse().unpack(Instance.class).getName();
                } catch (Exception e) {
                    log.warn("Could not unpack instance name from operation response: {}", e.getMessage());
                }
            }
        } else {
            status = "RUNNING";
        }

        return new AsyncOperationDto(operationName, status, null, doneAt, resourceId, error);
    }

    private AsyncOperationDto awaitOperationName(OperationFuture<?, OperationMetadata> future, String action) {
        String operationName;
        try {
            operationName = future.getName();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while retrieving operation name for " + action, e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to start Workbench instance " + action + ": " + e.getCause().getMessage(), e);
        }
        log.info("Workbench instance {} started: operation={}", action, operationName);
        return new AsyncOperationDto(operationName, "PENDING", OffsetDateTime.now(), null, null, null);
    }

    /** Derives a valid GCP resource ID from a display name. */
    private static String deriveInstanceId(String displayName) {
        String base = displayName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isEmpty()) base = "nb";
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        // GCP instance IDs max 63 chars; truncate base if needed
        if (base.length() > 54) base = base.substring(0, 54);
        return base + "-" + suffix;
    }

}
