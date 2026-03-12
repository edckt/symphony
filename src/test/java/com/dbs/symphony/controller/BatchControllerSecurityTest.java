package com.dbs.symphony.controller;

import com.dbs.symphony.dto.EffectiveLimitsDto;
import com.dbs.symphony.dto.SystemCapsDto;
import com.dbs.symphony.dto.UserQuotaLookupResultDto;
import com.dbs.symphony.dto.UserQuotaUsageDto;
import com.dbs.symphony.dto.UsageTotalsDto;
import com.dbs.symphony.exception.ForbiddenException;
import com.dbs.symphony.security.AuthorizationService;
import com.dbs.symphony.security.SecurityConfig;
import com.dbs.symphony.service.DirectoryService;
import com.dbs.symphony.service.QuotaEnforcementService;
import com.dbs.symphony.service.QuotaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BatchController.class, properties = "app.security.enabled=true")
@Import(SecurityConfig.class)
class BatchControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockBean QuotaService quotaService;
    @MockBean QuotaEnforcementService enforcement;
    @MockBean DirectoryService directoryService;
    @MockBean AuthorizationService authz;
    @MockBean JwtDecoder jwtDecoder;

    private static final String PROJECT = "p1";
    private static final String GROUP = "g1";

    // ──────────────────────────────────────────────────────
    // POST .../quotas/users:batchGet
    // ──────────────────────────────────────────────────────

    @Test
    void batchGetQuotas_withoutAuth_returns401() throws Exception {
        mvc.perform(post("/v1/projects/{p}/managed-groups/{g}/quotas/users:batchGet", PROJECT, GROUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds":["u1"],"includeDefault":false}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void batchGetQuotas_withUserScopeOnly_returns403() throws Exception {
        mvc.perform(post("/v1/projects/{p}/managed-groups/{g}/quotas/users:batchGet", PROJECT, GROUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds":["u1"],"includeDefault":false}
                                """)
                        .with(userJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void batchGetQuotas_withManagerScope_whenDomainDenied_returns403() throws Exception {
        doThrow(new ForbiddenException("not a manager")).when(authz).assertManagesGroup(anyString(), anyString());

        mvc.perform(post("/v1/projects/{p}/managed-groups/{g}/quotas/users:batchGet", PROJECT, GROUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds":["u1"],"includeDefault":false}
                                """)
                        .with(managerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void batchGetQuotas_withManagerScope_returns200() throws Exception {
        var result = new UserQuotaLookupResultDto(PROJECT, GROUP, "u1", "NONE", null, "GLOBAL_DEFAULT", null, null);
        when(quotaService.getUserQuota(PROJECT, GROUP, "u1")).thenReturn(result);

        mvc.perform(post("/v1/projects/{p}/managed-groups/{g}/quotas/users:batchGet", PROJECT, GROUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds":["u1"],"includeDefault":false}
                                """)
                        .with(managerJwt()))
                .andExpect(status().isOk());

        verify(authz).assertManagesGroup("manager1", GROUP);
    }

    // ──────────────────────────────────────────────────────
    // POST .../usage/users:batchGet
    // ──────────────────────────────────────────────────────

    @Test
    void batchGetUsage_withoutAuth_returns401() throws Exception {
        mvc.perform(post("/v1/projects/{p}/managed-groups/{g}/usage/users:batchGet", PROJECT, GROUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds":["u1"],"includeLimits":true}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void batchGetUsage_withUserScopeOnly_returns403() throws Exception {
        mvc.perform(post("/v1/projects/{p}/managed-groups/{g}/usage/users:batchGet", PROJECT, GROUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds":["u1"],"includeLimits":true}
                                """)
                        .with(userJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void batchGetUsage_withManagerScope_returns200() throws Exception {
        var usage = new UserQuotaUsageDto(PROJECT, GROUP, "u1", OffsetDateTime.now(),
                SystemCapsDto.INSTANCE,
                new EffectiveLimitsDto(3, Integer.MAX_VALUE, List.of(), null, List.of()),
                new UsageTotalsDto(0, 0), new UsageTotalsDto(3, Integer.MAX_VALUE),
                "GLOBAL_DEFAULT");
        when(enforcement.computeUsage(PROJECT, GROUP, "u1", "u1")).thenReturn(usage);

        mvc.perform(post("/v1/projects/{p}/managed-groups/{g}/usage/users:batchGet", PROJECT, GROUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds":["u1"],"includeLimits":true}
                                """)
                        .with(managerJwt()))
                .andExpect(status().isOk());

        verify(authz).assertManagesGroup("manager1", GROUP);
    }

    // ──────────────────────────────────────────────────────
    // POST .../effective-quota:batchForGroup
    // ──────────────────────────────────────────────────────

    @Test
    void batchForGroup_withoutAuth_returns401() throws Exception {
        mvc.perform(post("/v1/projects/{p}/managed-groups/{g}/effective-quota:batchForGroup", PROJECT, GROUP))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void batchForGroup_withUserScopeOnly_returns403() throws Exception {
        mvc.perform(post("/v1/projects/{p}/managed-groups/{g}/effective-quota:batchForGroup", PROJECT, GROUP)
                        .with(userJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void batchForGroup_withManagerScope_whenDomainDenied_returns403() throws Exception {
        doThrow(new ForbiddenException("not a manager")).when(authz).assertManagesGroup(anyString(), anyString());

        mvc.perform(post("/v1/projects/{p}/managed-groups/{g}/effective-quota:batchForGroup", PROJECT, GROUP)
                        .with(managerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void batchForGroup_withManagerScope_returns200() throws Exception {
        when(directoryService.listGroupMembers(GROUP)).thenReturn(List.of());

        mvc.perform(post("/v1/projects/{p}/managed-groups/{g}/effective-quota:batchForGroup", PROJECT, GROUP)
                        .with(managerJwt()))
                .andExpect(status().isOk());

        verify(authz).assertManagesGroup("manager1", GROUP);
    }
}
