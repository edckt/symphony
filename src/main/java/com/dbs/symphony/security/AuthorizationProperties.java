package com.dbs.symphony.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "app.authz")
public class AuthorizationProperties {
    public enum Mode {
        ALLOW_ALL,
        CONFIG
    }

    private Mode mode = Mode.ALLOW_ALL;
    private Map<String, Set<String>> managedGroupsByPrincipal = new HashMap<>();
    private Map<String, Set<String>> usersByGroup = new HashMap<>();

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Map<String, Set<String>> getManagedGroupsByPrincipal() {
        return managedGroupsByPrincipal;
    }

    public void setManagedGroupsByPrincipal(Map<String, Set<String>> managedGroupsByPrincipal) {
        this.managedGroupsByPrincipal = managedGroupsByPrincipal == null ? new HashMap<>() : managedGroupsByPrincipal;
    }

    public Map<String, Set<String>> getUsersByGroup() {
        return usersByGroup;
    }

    public void setUsersByGroup(Map<String, Set<String>> usersByGroup) {
        this.usersByGroup = usersByGroup == null ? new HashMap<>() : usersByGroup;
    }

    Set<String> groupsForPrincipal(String principal) {
        Set<String> result = new HashSet<>();
        if (managedGroupsByPrincipal.containsKey("*")) {
            result.addAll(managedGroupsByPrincipal.get("*"));
        }
        if (principal != null && managedGroupsByPrincipal.containsKey(principal)) {
            result.addAll(managedGroupsByPrincipal.get(principal));
        }
        return result;
    }

    Set<String> usersForGroup(String groupId) {
        Set<String> result = new HashSet<>();
        if (usersByGroup.containsKey("*")) {
            result.addAll(usersByGroup.get("*"));
        }
        if (groupId != null && usersByGroup.containsKey(groupId)) {
            result.addAll(usersByGroup.get(groupId));
        }
        return result;
    }
}
