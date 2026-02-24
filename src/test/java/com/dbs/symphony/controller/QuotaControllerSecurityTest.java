package com.dbs.symphony.controller;

import com.dbs.symphony.dto.UserQuotaDto;
import com.dbs.symphony.dto.UserQuotaLookupResultDto;
import com.dbs.symphony.dto.UserQuotaSpecDto;
import com.dbs.symphony.exception.ForbiddenException;
import com.dbs.symphony.security.AuthorizationService;
import com.dbs.symphony.security.SecurityConfig;
import com.dbs.symphony.service.QuotaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static com.dbs.symphony.controller.JwtFixtures.managerJwt;
import static com.dbs.symphony.controller.JwtFixtures.userJwt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = QuotaController.class, properties = "app.security.enabled=true")
@Import(SecurityConfig.class)
class QuotaControllerSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    QuotaService quotaService;

    @MockBean
    AuthorizationService authz;

    @MockBean
    JwtDecoder jwtDecoder;

    @Test
    void managerEndpoint_withoutAuth_returns401() throws Exception {
        mvc.perform(get("/v1/projects/p1/managed-groups/g1/quotas/users/u1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void managerEndpoint_withUserScopeOnly_returns403() throws Exception {
        mvc.perform(get("/v1/projects/p1/managed-groups/g1/quotas/users/u1")
                        .with(userJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerEndpoint_withManagerScopeAndDeniedDomainAuth_returns403() throws Exception {
        doThrow(new ForbiddenException("Principal is not allowed to manage this group"))
                .when(authz).assertManagesGroup(anyString(), anyString());

        mvc.perform(get("/v1/projects/p1/managed-groups/g1/quotas/users/u1")
                        .with(managerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerEndpoint_withManagerScopeAndAllowedDomainAuth_returns200() throws Exception {
        var spec = new UserQuotaSpecDto(3, 16, List.of("n2-standard-4"), 200, List.of("asia-southeast1-a"));
        var quota = new UserQuotaDto("p1", "g1", "u1", spec, OffsetDateTime.now(), "manager1");
        var result = new UserQuotaLookupResultDto("p1", "g1", "u1", "EXPLICIT", quota, "USER_QUOTA", null, spec);
        when(quotaService.getUserQuota("p1", "g1", "u1")).thenReturn(result);

        mvc.perform(get("/v1/projects/p1/managed-groups/g1/quotas/users/u1")
                        .with(managerJwt()))
                .andExpect(status().isOk());

        verify(authz).assertManagesGroup("manager1", "g1");
        verify(authz).assertUserInGroup("u1", "g1");
    }

    // ──────────────────────────────────────────────────────
    // DELETE /v1/projects/{projectId}/managed-groups/{groupId}/quotas/users/{userId}
    // ──────────────────────────────────────────────────────

    @Test
    void deleteQuota_withoutAuth_returns401() throws Exception {
        mvc.perform(delete("/v1/projects/p1/managed-groups/g1/quotas/users/u1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteQuota_withUserScopeOnly_returns403() throws Exception {
        mvc.perform(delete("/v1/projects/p1/managed-groups/g1/quotas/users/u1")
                        .with(userJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteQuota_withManagerScope_returns204() throws Exception {
        mvc.perform(delete("/v1/projects/p1/managed-groups/g1/quotas/users/u1")
                        .with(managerJwt()))
                .andExpect(status().isNoContent());

        verify(authz).assertManagesGroup("manager1", "g1");
        verify(authz).assertUserInGroup("u1", "g1");
    }
}
