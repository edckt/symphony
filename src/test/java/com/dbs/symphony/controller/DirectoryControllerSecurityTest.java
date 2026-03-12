package com.dbs.symphony.controller;

import com.dbs.symphony.dto.GroupDto;
import com.dbs.symphony.exception.ForbiddenException;
import com.dbs.symphony.security.AuthorizationService;
import com.dbs.symphony.security.SecurityConfig;
import com.dbs.symphony.service.DirectoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.dbs.symphony.controller.JwtFixtures.managerJwt;
import static com.dbs.symphony.controller.JwtFixtures.userJwt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DirectoryController.class, properties = "app.security.enabled=true")
@Import(SecurityConfig.class)
class DirectoryControllerSecurityTest {

    @Autowired MockMvc mvc;

    @MockBean DirectoryService directoryService;
    @MockBean AuthorizationService authz;
    @MockBean JwtDecoder jwtDecoder;

    // ──────────────────────────────────────────────────────
    // GET /v1/self/managed-groups
    // ──────────────────────────────────────────────────────

    @Test
    void listManagedGroups_withoutAuth_returns401() throws Exception {
        mvc.perform(get("/v1/self/managed-groups"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listManagedGroups_withUserScopeOnly_returns403() throws Exception {
        mvc.perform(get("/v1/self/managed-groups").with(userJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listManagedGroups_withManagerScope_returns200() throws Exception {
        when(directoryService.listManagedGroupPairs(anyString())).thenReturn(List.of());

        mvc.perform(get("/v1/self/managed-groups").with(managerJwt()))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────────────
    // GET /v1/groups/{groupId}
    // ──────────────────────────────────────────────────────

    @Test
    void getGroup_withoutAuth_returns401() throws Exception {
        mvc.perform(get("/v1/groups/g1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getGroup_withManagerScope_returns200() throws Exception {
        var group = new GroupDto("g1", "Group One", null, "ACTIVE_DIRECTORY", 5);
        when(directoryService.getGroup("g1")).thenReturn(group);

        mvc.perform(get("/v1/groups/g1").with(managerJwt()))
                .andExpect(status().isOk());

        verify(authz).assertManagesGroup("manager1", "g1");
    }

    @Test
    void getGroup_withUserScope_whenMember_returns200() throws Exception {
        // assertManagesGroup throws → falls back to assertUserInGroup (which passes via mock no-op)
        doThrow(new ForbiddenException("not a manager")).when(authz).assertManagesGroup(anyString(), anyString());
        var group = new GroupDto("g1", "Group One", null, "ACTIVE_DIRECTORY", 5);
        when(directoryService.getGroup("g1")).thenReturn(group);

        mvc.perform(get("/v1/groups/g1").with(userJwt()))
                .andExpect(status().isOk());

        verify(authz).assertUserInGroup("user1", "g1");
    }

    @Test
    void getGroup_withUserScope_whenNeitherManagerNorMember_returns403() throws Exception {
        doThrow(new ForbiddenException("not a manager")).when(authz).assertManagesGroup(anyString(), anyString());
        doThrow(new ForbiddenException("not a member")).when(authz).assertUserInGroup(anyString(), anyString());

        mvc.perform(get("/v1/groups/g1").with(userJwt()))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────────────
    // GET /v1/groups/{groupId}/members
    // ──────────────────────────────────────────────────────

    @Test
    void listMembers_withoutAuth_returns401() throws Exception {
        mvc.perform(get("/v1/groups/g1/members"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listMembers_withUserScopeOnly_returns403() throws Exception {
        mvc.perform(get("/v1/groups/g1/members").with(userJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listMembers_withManagerScope_whenDomainDenied_returns403() throws Exception {
        doThrow(new ForbiddenException("not a manager")).when(authz).assertManagesGroup(anyString(), anyString());

        mvc.perform(get("/v1/groups/g1/members").with(managerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listMembers_withManagerScope_returns200() throws Exception {
        when(directoryService.listGroupMembers("g1")).thenReturn(List.of());

        mvc.perform(get("/v1/groups/g1/members").with(managerJwt()))
                .andExpect(status().isOk());

        verify(authz).assertManagesGroup("manager1", "g1");
    }
}
