package com.dbs.symphony.config;

import com.google.cloud.notebooks.v2.NotebookServiceClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@EnableConfigurationProperties(WorkbenchProperties.class)
public class GcpConfig {

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
