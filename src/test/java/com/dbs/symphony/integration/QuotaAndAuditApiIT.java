package com.dbs.symphony.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dbs.symphony.dto.UserQuotaSpecDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuotaAndAuditApiIT extends PostgresIntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void quotaUpsert_withEmptyAllowedMachineTypes_isAccepted() throws Exception {
        String quotaPath = "/v1/projects/p9/managed-groups/g9/quotas/users/u999";

        var spec = new UserQuotaSpecDto(2, 8, List.of(), 100, List.of());

        mvc.perform(put(quotaPath)
                .with(managerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(spec)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.spec.allowedMachineTypes", hasSize(0)));
    }

    @Test
    void quotaUpsert_get_delete_and_auditLatest() throws Exception {
        String projectId = "p1";
        String groupId = "g1";
        String userId = "u123";

        String quotaPath = "/v1/projects/%s/managed-groups/%s/quotas/users/%s".formatted(projectId, groupId, userId);
        String auditPath = quotaPath + "/audit/latest";

        var spec = new UserQuotaSpecDto(
                3,
                16,
                List.of("e2-standard-4", "n2-standard-8"),
                200,
                List.of("asia-southeast1-a")
        );

        // 1) PUT quota
        mvc.perform(put(quotaPath)
                .with(managerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(spec)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId", is(projectId)))
            .andExpect(jsonPath("$.groupId", is(groupId)))
            .andExpect(jsonPath("$.userId", is(userId)))
            .andExpect(jsonPath("$.spec.maxInstances", is(3)))
            .andExpect(jsonPath("$.updatedAt", notNullValue()))
            .andExpect(jsonPath("$.updatedBy", notNullValue()));

        // 2) GET quota => EXPLICIT
        mvc.perform(get(quotaPath).with(managerJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("EXPLICIT")))
            .andExpect(jsonPath("$.quota.userId", is(userId)))
            .andExpect(jsonPath("$.quota.spec.maxTotalVcpu", is(16)));

        // 3) Audit latest => PRESENT + UPSERT
        mvc.perform(get(auditPath).with(managerJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("PRESENT")))
            .andExpect(jsonPath("$.latest.action", is("UPSERT")))
            .andExpect(jsonPath("$.latest.actor.principal", notNullValue()))
            .andExpect(jsonPath("$.latest.newSpec.maxInstances", is(3)));

        // 4) DELETE quota
        mvc.perform(delete(quotaPath).with(managerJwt()))
            .andExpect(status().isNoContent());

        // 5) GET quota => NONE
        mvc.perform(get(quotaPath).with(managerJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("NONE")))
            .andExpect(jsonPath("$.quota").doesNotExist()); // because it's null in the DTO

        // 6) Audit latest => PRESENT + DELETE
        mvc.perform(get(auditPath).with(managerJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("PRESENT")))
            .andExpect(jsonPath("$.latest.action", is("DELETE")))
            .andExpect(jsonPath("$.latest.oldSpec").isNotEmpty())
            .andExpect(jsonPath("$.latest.newSpec").doesNotExist());
    }
}
