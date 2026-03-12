package com.dbs.symphony.service;

import com.dbs.symphony.config.LdapProperties;
import com.dbs.symphony.dto.GroupDto;
import com.dbs.symphony.dto.ManagedGroupPairDto;
import com.dbs.symphony.dto.UserSummaryDto;
import com.dbs.symphony.exception.NotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.filter.PresentFilter;
import org.springframework.stereotype.Service;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import java.util.ArrayList;
import java.util.List;

/**
 * LDAP-backed DirectoryService that resolves manager↔user group pairs
 * using the *_Managers ↔ *_Users naming convention.
 *
 * IMPORTANT: All user-supplied values are passed through Spring LDAP filter builders
 * (AndFilter, EqualsFilter) — never concatenated directly — to prevent LDAP injection.
 */
@Service
@ConditionalOnProperty(name = "app.ldap.enabled", havingValue = "true")
public class LdapDirectoryService implements DirectoryService {

    private static final String SOURCE = "ACTIVE_DIRECTORY";

    private final LdapTemplate ldapTemplate;
    private final LdapProperties props;

    public LdapDirectoryService(LdapTemplate ldapTemplate, LdapProperties props) {
        this.ldapTemplate = ldapTemplate;
        this.props = props;
    }

    @Override
    public List<ManagedGroupPairDto> listManagedGroupPairs(String principal) {
        // 1. Find all groups where the principal is a member
        String principalDn = resolvePrincipalDn(principal);
        if (principalDn == null) return List.of();

        AndFilter groupFilter = new AndFilter();
        groupFilter.and(new EqualsFilter("objectClass", props.groupObjectClass()));
        groupFilter.and(new EqualsFilter("member", principalDn));

        List<String> managerGroupCns = ldapTemplate.search(
                props.groupSearchBase(),
                groupFilter.encode(),
                (Attributes attrs) -> getAttr(attrs, "cn")
        );

        // 2. Keep only groups ending with managerGroupSuffix
        List<ManagedGroupPairDto> pairs = new ArrayList<>();
        for (String cn : managerGroupCns) {
            if (cn == null || !cn.endsWith(props.managerGroupSuffix())) continue;

            String userGroupCn = cn.substring(0, cn.length() - props.managerGroupSuffix().length())
                    + props.userGroupSuffix();

            // 3. Look up the paired user group
            GroupDto managerGroup = findGroupByCn(cn);
            GroupDto userGroup = findGroupByCnOrNull(userGroupCn);
            if (userGroup == null) continue; // paired group doesn't exist — skip

            pairs.add(new ManagedGroupPairDto(managerGroup, userGroup, "SUFFIX_PAIRING"));
        }
        return pairs;
    }

    @Override
    public GroupDto getGroup(String groupId) {
        GroupDto group = findGroupByCnOrNull(groupId);
        if (group == null) throw new NotFoundException("Group not found: " + groupId);
        return group;
    }

    @Override
    public List<UserSummaryDto> listGroupMembers(String groupId) {
        // Fetch member DNs from the group entry
        AndFilter filter = new AndFilter();
        filter.and(new EqualsFilter("objectClass", props.groupObjectClass()));
        filter.and(new EqualsFilter("cn", groupId));

        List<List<String>> results = ldapTemplate.search(
                props.groupSearchBase(),
                filter.encode(),
                (Attributes attrs) -> getMultiAttr(attrs, "member")
        );

        if (results.isEmpty()) return List.of();
        List<String> memberDns = results.get(0);

        // Resolve each member DN to a UserSummaryDto
        List<UserSummaryDto> members = new ArrayList<>();
        for (String dn : memberDns) {
            String userId = extractAttributeFromDn(dn, props.userIdAttribute());
            if (userId == null) continue;
            members.add(new UserSummaryDto(userId, null, null, groupId));
        }
        return members;
    }

    // --- Helpers ---

    private GroupDto findGroupByCn(String cn) {
        GroupDto g = findGroupByCnOrNull(cn);
        if (g == null) throw new NotFoundException("Group not found: " + cn);
        return g;
    }

    private GroupDto findGroupByCnOrNull(String cn) {
        AndFilter filter = new AndFilter();
        filter.and(new EqualsFilter("objectClass", props.groupObjectClass()));
        filter.and(new EqualsFilter("cn", cn));

        List<GroupDto> results = ldapTemplate.search(
                props.groupSearchBase(),
                filter.encode(),
                (Attributes attrs) -> {
                    String groupId = getAttr(attrs, "cn");
                    String displayName = getAttr(attrs, "displayName");
                    if (displayName == null) displayName = groupId;
                    String description = getAttr(attrs, "description");
                    int memberCount = countAttr(attrs, "member");
                    return new GroupDto(groupId, displayName, description, SOURCE, memberCount);
                }
        );
        return results.isEmpty() ? null : results.get(0);
    }

    /** Returns the full distinguished name for the given sAMAccountName, or null if not found. */
    private String resolvePrincipalDn(String sAMAccountName) {
        AndFilter filter = new AndFilter();
        filter.and(new PresentFilter("objectClass"));
        filter.and(new EqualsFilter(props.userIdAttribute(), sAMAccountName));

        List<String> dns = ldapTemplate.search(
                props.userSearchBase(),
                filter.encode(),
                (Attributes attrs) -> getAttr(attrs, "distinguishedName")
        );
        return dns.isEmpty() ? null : dns.get(0);
    }

    private static String getAttr(Attributes attrs, String name) {
        try {
            var attr = attrs.get(name);
            return attr == null ? null : (String) attr.get();
        } catch (NamingException e) {
            return null;
        }
    }

    private static List<String> getMultiAttr(Attributes attrs, String name) {
        try {
            var attr = attrs.get(name);
            if (attr == null) return List.of();
            List<String> values = new ArrayList<>();
            var all = attr.getAll();
            while (all.hasMore()) values.add((String) all.next());
            return values;
        } catch (NamingException e) {
            return List.of();
        }
    }

    private static int countAttr(Attributes attrs, String name) {
        var attr = attrs.get(name);
        return attr == null ? 0 : attr.size();
    }

    /** Extracts a named attribute value from a DN string, e.g. "CN=alice,OU=..." → "alice" for CN. */
    private static String extractAttributeFromDn(String dn, String attrName) {
        if (dn == null) return null;
        String prefix = attrName + "=";
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return trimmed.substring(prefix.length());
            }
        }
        return null;
    }
}
