package com.dbs.symphony.service;

import com.dbs.symphony.config.LdapProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LdapDirectoryServiceTest {

    @Mock LdapTemplate ldapTemplate;

    private static final LdapProperties PROPS = new LdapProperties(
            true,
            "ldap://localhost:389",
            "DC=corp,DC=example,DC=com",
            "cn=svc,DC=corp,DC=example,DC=com",
            "secret",
            "OU=Groups",
            "OU=Users",
            "group",
            "sAMAccountName",
            "_Managers",
            "_Users"
    );

    @Test
    @SuppressWarnings("unchecked")
    void listManagedGroupPairs_principalNotFoundInLdap_returnsEmptyListWithoutGroupSearch() {
        LdapDirectoryService svc = new LdapDirectoryService(ldapTemplate, PROPS);

        // resolvePrincipalDn search returns no DN
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenReturn(List.of());

        var result = svc.listManagedGroupPairs("unknown-user");

        assertThat(result).isEmpty();
        // Group membership search must NOT be called since DN resolved to null
        verify(ldapTemplate, never()).search(eq(PROPS.groupSearchBase()), anyString(), any(AttributesMapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listManagedGroupPairs_principalFoundButNoManagerGroups_returnsEmptyList() {
        LdapDirectoryService svc = new LdapDirectoryService(ldapTemplate, PROPS);

        String principalDn = "CN=alice,OU=Users,DC=corp,DC=example,DC=com";

        // First call: resolvePrincipalDn — returns the DN
        // Second call: group membership search — returns no matching groups
        when(ldapTemplate.search(anyString(), anyString(), any(AttributesMapper.class)))
                .thenReturn(List.of(principalDn))
                .thenReturn(List.of());

        var result = svc.listManagedGroupPairs("alice");

        assertThat(result).isEmpty();
    }
}
