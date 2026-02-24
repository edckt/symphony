package com.dbs.symphony.util;

import java.util.Optional;

public final class PairingRules {

    private static final String MANAGERS_SUFFIX = "_Managers";
    private static final String USERS_SUFFIX = "_Users";

    private PairingRules() {}

    /**
     * Returns true if this group is a manager group.
     * Example: Finance_Managers
     */
    public static boolean isManagerGroup(String groupName) {
        return groupName != null && groupName.endsWith(MANAGERS_SUFFIX);
    }

    /**
     * Returns true if this group is a user group.
     * Example: Finance_Users
     */
    public static boolean isUserGroup(String groupName) {
        return groupName != null && groupName.endsWith(USERS_SUFFIX);
    }

    /**
     * Given a manager group name, derive the paired user group name.
     *
     * Finance_Managers -> Finance_Users
     */
    public static Optional<String> toUserGroup(String managerGroupName) {
        if (!isManagerGroup(managerGroupName)) {
            return Optional.empty();
        }

        String base = managerGroupName.substring(
            0,
            managerGroupName.length() - MANAGERS_SUFFIX.length()
        );

        return Optional.of(base + USERS_SUFFIX);
    }

    /**
     * Given a user group name, derive the paired manager group name.
     *
     * Finance_Users -> Finance_Managers
     */
    public static Optional<String> toManagerGroup(String userGroupName) {
        if (!isUserGroup(userGroupName)) {
            return Optional.empty();
        }

        String base = userGroupName.substring(
            0,
            userGroupName.length() - USERS_SUFFIX.length()
        );

        return Optional.of(base + MANAGERS_SUFFIX);
    }

    /**
     * Extracts the team base name.
     *
     * Finance_Managers -> Finance
     * Finance_Users    -> Finance
     */
    public static Optional<String> extractTeamName(String groupName) {
        if (groupName == null) return Optional.empty();

        if (isManagerGroup(groupName)) {
            return Optional.of(
                groupName.substring(
                    0,
                    groupName.length() - MANAGERS_SUFFIX.length()
                )
            );
        }

        if (isUserGroup(groupName)) {
            return Optional.of(
                groupName.substring(
                    0,
                    groupName.length() - USERS_SUFFIX.length()
                )
            );
        }

        return Optional.empty();
    }
}