package com.dbs.symphony.service;

import com.dbs.symphony.dto.WorkbenchInstanceDto;
import com.google.cloud.asset.v1.AssetServiceClient;
import com.google.cloud.asset.v1.ResourceSearchResult;
import com.google.cloud.asset.v1.SearchAllResourcesRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "app.cai.enabled", havingValue = "true")
public class GcpCaiService implements CaiService {

    private static final String ASSET_TYPE = "compute.googleapis.com/Instance";

    private final AssetServiceClient assetServiceClient;

    public GcpCaiService(AssetServiceClient assetServiceClient) {
        this.assetServiceClient = assetServiceClient;
    }

    @Override
    public List<WorkbenchInstanceDto> listUserInstances(String projectId, String bankUserId) {
        String labelUserId = GcpLabels.normalizeUserId(bankUserId);
        String query = "labels.notebooks-product:* AND labels.user:" + labelUserId;

        SearchAllResourcesRequest request = SearchAllResourcesRequest.newBuilder()
                .setScope("projects/" + projectId)
                .setQuery(query)
                .addAssetTypes(ASSET_TYPE)
                .build();

        List<WorkbenchInstanceDto> results = new ArrayList<>();
        for (ResourceSearchResult result : assetServiceClient.searchAllResources(request).iterateAll()) {
            results.add(toDto(result));
        }
        return results;
    }

    private WorkbenchInstanceDto toDto(ResourceSearchResult result) {
        // Resource name: //compute.googleapis.com/projects/{proj}/zones/{zone}/instances/{name}
        String name = result.getName();
        String id = name.substring(name.lastIndexOf('/') + 1);

        // Machine type is stored as a URL in additionalAttributes; parse the type name
        String machineType = null;
        var attrs = result.getAdditionalAttributes().getFieldsMap();
        if (attrs.containsKey("machineType")) {
            String machineTypeUrl = attrs.get("machineType").getStringValue();
            machineType = machineTypeUrl.substring(machineTypeUrl.lastIndexOf('/') + 1);
        }

        // Labels contain the owner user id
        String ownerUserId = result.getLabelsMap().get("user");

        return new WorkbenchInstanceDto(
                id,
                result.getDisplayName(),
                result.getState(),
                machineType,
                result.getLocation(),
                ownerUserId,
                null  // CAI search results do not reliably surface createdAt; null until GCP API is wired
        );
    }
}
