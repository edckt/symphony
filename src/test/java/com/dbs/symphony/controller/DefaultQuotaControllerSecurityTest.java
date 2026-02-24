package com.dbs.symphony.controller;

import com.dbs.symphony.dto.DefaultQuotaDto;
import com.dbs.symphony.dto.UserQuotaSpecDto;
import com.dbs.symphony.security.SecurityConfig;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DefaultQuotaController.class, properties = "app.security.enabled=true")
@Import(SecurityConfig.class)
class DefaultQuotaControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockBean QuotaService quotaService;
    @MockBean JwtDecoder jwtDecoder;

    // ──────────────────────────────────────────────────────
    // GET /v1/projects/{projectId}/quotas/default
    // ──────────────────────────────────────────────────────

    @Test
    void getDefaultQuota_withoutAuth_returns401() throws Exception {
        mvc.perform(get("/v1/projects/p1/quotas/default"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getDefaultQuota_withUserScopeOnly_returns403() throws Exception {
        mvc.perform(get("/v1/projects/p1/quotas/default")
                        .with(userJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDefaultQuota_withManagerScope_returns200() throws Exception {
        var dto = new DefaultQuotaDto("p1", spec(), OffsetDateTime.now(), "manager1");
        when(quotaService.getDefaultQuota(anyString())).thenReturn(dto);

        mvc.perform(get("/v1/projects/p1/quotas/default")
                        .with(managerJwt()))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────────────
    // PUT /v1/projects/{projectId}/quotas/default
    // ──────────────────────────────────────────────────────

    @Test
    void upsertDefaultQuota_withoutAuth_returns401() throws Exception {
        mvc.perform(put("/v1/projects/p1/quotas/default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(specBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upsertDefaultQuota_withUserScopeOnly_returns403() throws Exception {
        mvc.perform(put("/v1/projects/p1/quotas/default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(specBody())
                        .with(userJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void upsertDefaultQuota_withManagerScope_returns200() throws Exception {
        var dto = new DefaultQuotaDto("p1", spec(), OffsetDateTime.now(), "manager1");
        when(quotaService.upsertDefaultQuota(anyString(), any())).thenReturn(dto);

        mvc.perform(put("/v1/projects/p1/quotas/default")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(specBody())
                        .with(managerJwt()))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────────────
    // DELETE /v1/projects/{projectId}/quotas/default
    // ──────────────────────────────────────────────────────

    @Test
    void deleteDefaultQuota_withoutAuth_returns401() throws Exception {
        mvc.perform(delete("/v1/projects/p1/quotas/default"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteDefaultQuota_withUserScopeOnly_returns403() throws Exception {
        mvc.perform(delete("/v1/projects/p1/quotas/default")
                        .with(userJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteDefaultQuota_withManagerScope_returns204() throws Exception {
        mvc.perform(delete("/v1/projects/p1/quotas/default")
                        .with(managerJwt()))
                .andExpect(status().isNoContent());
    }

    // ──────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────

    private UserQuotaSpecDto spec() {
        return new UserQuotaSpecDto(1, 0, List.of(), null, List.of());
    }

    private String specBody() throws Exception {
        return mapper.writeValueAsString(spec());
    }
}
