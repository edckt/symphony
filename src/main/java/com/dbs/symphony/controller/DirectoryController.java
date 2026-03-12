package com.dbs.symphony.controller;

import com.dbs.symphony.dto.GroupDto;
import com.dbs.symphony.dto.ManagedGroupPairDto;
import com.dbs.symphony.dto.PagedManagedGroupPairsDto;
import com.dbs.symphony.dto.PagedUsersDto;
import com.dbs.symphony.dto.UserSummaryDto;
import com.dbs.symphony.exception.ForbiddenException;
import com.dbs.symphony.security.AuthorizationService;
import com.dbs.symphony.security.CurrentPrincipal;
import com.dbs.symphony.service.DirectoryService;
import com.dbs.symphony.util.PageTokens;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
public class DirectoryController {

    private final DirectoryService directoryService;
    private final AuthorizationService authz;

    public DirectoryController(DirectoryService directoryService, AuthorizationService authz) {
        this.directoryService = directoryService;
        this.authz = authz;
    }

    @GetMapping("/v1/self/managed-groups")
    @PreAuthorize("hasAnyAuthority('SCOPE_workbench.manager', 'workbench.manager')")
    public PagedManagedGroupPairsDto listSelfManagedGroups(
            @RequestParam(required = false, defaultValue = "100") int pageSize,
            @RequestParam(required = false) String pageToken
    ) {
        String principal = CurrentPrincipal.principal();
        List<ManagedGroupPairDto> all = directoryService.listManagedGroupPairs(principal);
        return paginate(all, pageSize, pageToken,
                (items, next) -> new PagedManagedGroupPairsDto(items, next));
    }

    @GetMapping("/v1/groups/{groupId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_workbench.user', 'workbench.user', 'SCOPE_workbench.manager', 'workbench.manager')")
    public GroupDto getGroup(@PathVariable String groupId) {
        String principal = CurrentPrincipal.principal();
        // Caller must be a manager of the group OR a member — try manager check first
        try {
            authz.assertManagesGroup(principal, groupId);
        } catch (ForbiddenException e) {
            authz.assertUserInGroup(principal, groupId);
        }
        return directoryService.getGroup(groupId);
    }

    @GetMapping("/v1/groups/{groupId}/members")
    @PreAuthorize("hasAnyAuthority('SCOPE_workbench.manager', 'workbench.manager')")
    public PagedUsersDto listGroupMembers(
            @PathVariable String groupId,
            @RequestParam(required = false, defaultValue = "100") int pageSize,
            @RequestParam(required = false) String pageToken
    ) {
        String principal = CurrentPrincipal.principal();
        authz.assertManagesGroup(principal, groupId);
        List<UserSummaryDto> all = directoryService.listGroupMembers(groupId);
        return paginate(all, pageSize, pageToken,
                (items, next) -> new PagedUsersDto(items, next));
    }

    // --- Pagination helpers ---

    @FunctionalInterface
    private interface PageFactory<T, R> {
        R build(List<T> items, String nextPageToken);
    }

    private <T, R> R paginate(List<T> all, int pageSize, String pageToken, PageFactory<T, R> factory) {
        int offset = PageTokens.decode(pageToken);
        int end = Math.min(offset + pageSize, all.size());
        List<T> page = all.subList(offset, end);
        String nextToken = end < all.size() ? PageTokens.encode(end) : null;
        return factory.build(page, nextToken);
    }
}
