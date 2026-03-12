package com.dbs.symphony.service;

import com.dbs.symphony.config.WorkbenchProperties;
import com.dbs.symphony.dto.WorkbenchInstanceDto;
import com.google.cloud.notebooks.v2.Instance;
import com.google.cloud.notebooks.v2.NotebookServiceClient;
import com.google.cloud.notebooks.v2.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * CaiService implementation that lists Workbench instances via the Notebooks API v2.
 *
 * Queries each configured zone in parallel using virtual threads.
 * DELETED instances are excluded so they do not count toward quota during the
 * brief window between deletion request and GCE-level cleanup.
 */
@Service
@ConditionalOnProperty(name = "app.workbench.enabled", havingValue = "true")
public class GcpWorkbenchCatalogService implements CaiService {

    private static final Logger log = LoggerFactory.getLogger(GcpWorkbenchCatalogService.class);

    private final NotebookServiceClient notebookServiceClient;
    private final List<String> zones;

    public GcpWorkbenchCatalogService(NotebookServiceClient notebookServiceClient,
                                      WorkbenchProperties props) {
        this.notebookServiceClient = notebookServiceClient;
        this.zones = props.zones();
    }

    @Override
    public List<WorkbenchInstanceDto> listUserInstances(String projectId, String bankUserId) {
        if (zones.isEmpty()) return List.of();

        String labelUserId = GcpLabels.normalizeUserId(bankUserId);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = zones.stream()
                    .map(zone -> CompletableFuture.supplyAsync(
                            () -> listForZone(projectId, zone, labelUserId), executor))
                    .toList();

            List<WorkbenchInstanceDto> results = new ArrayList<>();
            for (var f : futures) {
                results.addAll(f.join());
            }
            return results;
        }
    }

    private List<WorkbenchInstanceDto> listForZone(String projectId, String zone, String labelUserId) {
        String parent = "projects/" + projectId + "/locations/" + zone;
        List<WorkbenchInstanceDto> instances = new ArrayList<>();
        try {
            for (Instance instance : notebookServiceClient.listInstances(parent).iterateAll()) {
                if (!labelUserId.equals(instance.getLabelsOrDefault("user", null))) continue;
                if (instance.getState() == State.DELETED) continue;
                instances.add(toDto(instance));
            }
        } catch (Exception e) {
            log.warn("Failed to list Workbench instances in zone {}: {}", zone, e.getMessage());
        }
        return instances;
    }

    private static WorkbenchInstanceDto toDto(Instance instance) {
        // Resource name: projects/{p}/locations/{zone}/instances/{id}
        String[] parts = instance.getName().split("/");
        String zone = parts[parts.length - 3];

        // Instance.id is the unique resource ID (last segment of the resource name)
        String id = instance.getId();

        return new WorkbenchInstanceDto(
                id,
                id,                               // no display_name field in v2 proto; use id
                instance.getState().name(),        // ACTIVE, STOPPED, INITIALIZING, etc.
                instance.getGceSetup().getMachineType(),
                zone,
                instance.getLabelsOrDefault("user", null),
                null
        );
    }
}
