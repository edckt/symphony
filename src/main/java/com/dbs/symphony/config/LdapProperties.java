package com.dbs.symphony.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ldap")
public record LdapProperties(
        boolean enabled,
        String url,
        String base,
        String bindDn,
        String bindPassword,
        String groupSearchBase,
        String userSearchBase,
        String groupObjectClass,
        String userIdAttribute,
        String managerGroupSuffix,
        String userGroupSuffix
) {
    public LdapProperties {
        if (groupObjectClass == null || groupObjectClass.isBlank()) groupObjectClass = "group";
        if (userIdAttribute == null || userIdAttribute.isBlank()) userIdAttribute = "sAMAccountName";
        if (managerGroupSuffix == null || managerGroupSuffix.isBlank()) managerGroupSuffix = "_Managers";
        if (userGroupSuffix == null || userGroupSuffix.isBlank()) userGroupSuffix = "_Users";
    }
}
