package com.dbs.symphony.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.cai")
public class CaiProperties {

    /**
     * JWT claim whose value is used as the GCP label value for labels.user=<id>.
     * Null means fall back to the JWT subject (CurrentPrincipal.principal()).
     */
    private String userIdClaim;

    public String getUserIdClaim() {
        return userIdClaim;
    }

    public void setUserIdClaim(String userIdClaim) {
        this.userIdClaim = userIdClaim;
    }
}
