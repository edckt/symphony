package com.dbs.symphony.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.workbench")
public record WorkbenchProperties(List<String> zones) {
    public WorkbenchProperties {
        if (zones == null) zones = List.of();
    }
}
