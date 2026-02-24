package com.dbs.symphony.controller;

import com.dbs.symphony.dto.AuditActorDto;
import com.dbs.symphony.dto.AuditContextDto;
import com.dbs.symphony.dto.QuotaAuditEventDto;
import com.dbs.symphony.dto.UserQuotaSpecDto;
import com.dbs.symphony.exception.ForbiddenException;
import com.dbs.symphony.security.AuthorizationService;
import com.dbs.symphony.security.SecurityConfig;
import com.dbs.symphony.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.dbs.symphony.controller.JwtFixtures.managerJwt;
import static com.dbs.symphony.controller.JwtFixtures.userJwt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuditController.class, properties = "app.security.enabled=true")
@Import(SecurityConfig.class)
class AuditControllerSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    AuditService auditService;

    @MockBean
    AuthorizationService authz;

    @MockBean
    JwtDecoder jwtDecoder;

    @Test
    void auditEndpoint_withoutAuth_returns401() throws Exception {
        mvc.perform(get("/v1/projects/p1/managed-groups/g1/quotas/users/u1/audit/latest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void auditEndpoint_withUserScopeOnly_returns403() throws Exception {
        mvc.perform(get("/v1/projects/p1/managed-groups/g1/quotas/users/u1/audit/latest")
                        .with(userJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditEndpoint_withManagerScopeAndDeniedDomainAuth_returns403() throws Exception {
        doThrow(new ForbiddenException("Principal is not allowed to manage this group"))
                .when(authz).assertManagesGroup(anyString(), anyString());

        mvc.perform(get("/v1/projects/p1/managed-groups/g1/quotas/users/u1/audit/latest")
                        .with(managerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditEndpoint_withManagerScopeAndAllowedDomainAuth_returns200() throws Exception {
        var event = new QuotaAuditEventDto(
                "evt-1",
                "UPSERT",
                OffsetDateTime.now(),
                new AuditActorDto("manager1", "Manager One", "manager1@example.com"),
                new AuditContextDto("g1", null),
                null,
                new UserQuotaSpecDto(3, 16, List.of("n2-standard-4"), 200, List.of("asia-southeast1-a"))
        );
        when(auditService.latestEvent("p1", "g1", "u1")).thenReturn(Optional.of(event));

        mvc.perform(get("/v1/projects/p1/managed-groups/g1/quotas/users/u1/audit/latest")
                        .with(managerJwt()))
                .andExpect(status().isOk());

        verify(authz).assertManagesGroup("manager1", "g1");
        verify(authz).assertUserInGroup("u1", "g1");
    }
}
