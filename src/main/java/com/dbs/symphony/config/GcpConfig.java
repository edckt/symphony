package com.dbs.symphony.config;

import com.google.cloud.asset.v1.AssetServiceClient;
import com.google.cloud.notebooks.v2.NotebookServiceClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class GcpConfig {

    /**
     * Creates the CAI client only when app.cai.enabled=true.
     * Uses Application Default Credentials automatically:
     *   - GKE: Workload Identity
     *   - Local: gcloud auth application-default login
     */
    @Bean
    @ConditionalOnProperty(name = "app.cai.enabled", havingValue = "true")
    public AssetServiceClient assetServiceClient() throws IOException {
        return AssetServiceClient.create();
    }

    /**
     * Creates the Workbench client only when app.workbench.enabled=true.
     * Uses Application Default Credentials automatically.
     */
    @Bean
    @ConditionalOnProperty(name = "app.workbench.enabled", havingValue = "true")
    public NotebookServiceClient notebookServiceClient() throws IOException {
        return NotebookServiceClient.create();
    }
}
