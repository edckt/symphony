package com.dbs.symphony.security;

import com.dbs.symphony.exception.ForbiddenException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {
    private final AuthorizationProperties properties;

    public AuthorizationService(AuthorizationProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validate() {
        if (properties.getMode() == AuthorizationProperties.Mode.CONFIG) {
            if (properties.getManagedGroupsByPrincipal().isEmpty() && properties.getUsersByGroup().isEmpty()) {
                throw new IllegalStateException(
                    "app.authz.mode is CONFIG but both managedGroupsByPrincipal and usersByGroup are empty. " +
                    "Configure the authorization maps or switch mode to ALLOW_ALL."
                );
            }
        }
    }

    public void assertManagesGroup(String principal, String groupId) {
        if (properties.getMode() == AuthorizationProperties.Mode.ALLOW_ALL) {
            return;
        }
        var allowedGroups = properties.groupsForPrincipal(principal);
        if (!allowedGroups.contains("*") && !allowedGroups.contains(groupId)) {
            throw new ForbiddenException("Principal is not allowed to manage this group");
        }
    }

    public void assertUserInGroup(String userId, String groupId) {
        if (properties.getMode() == AuthorizationProperties.Mode.ALLOW_ALL) {
            return;
        }
        var allowedUsers = properties.usersForGroup(groupId);
        if (!allowedUsers.contains("*") && !allowedUsers.contains(userId)) {
            throw new ForbiddenException("User is not a member of this managed group");
        }
    }
}
