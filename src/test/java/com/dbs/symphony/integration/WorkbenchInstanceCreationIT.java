package com.dbs.symphony.integration;

import com.dbs.symphony.dto.AsyncOperationDto;
import com.dbs.symphony.dto.CreateInstanceRequestDto;
import com.dbs.symphony.dto.UserQuotaSpecDto;
import com.dbs.symphony.service.CaiService;
import com.dbs.symphony.service.WorkbenchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test for the Workbench instance lifecycle.
 *
 * Uses the real application context with:
 * - @MockBean CaiService — always returns an empty instance list (ownership checks → 404)
 * - @MockBean WorkbenchService — returns fake PENDING/DONE operations
 * - Real QuotaEnforcementService + QuotaService against a TestContainers PostgreSQL database
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkbenchInstanceCreationIT extends PostgresIntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockBean CaiService caiService;
    @MockBean WorkbenchService workbenchService;

    @BeforeEach
    void setUpMocks() {
        when(caiService.listUserInstances(anyString(), anyString())).thenReturn(List.of());
        var pending = new AsyncOperationDto(UUID.randomUUID().toString(), "PENDING",
                OffsetDateTime.now(), null, null, null);
        when(workbenchService.createInstance(anyString(), anyString(), any())).thenReturn(pending);
        when(workbenchService.getOperation(anyString())).thenAnswer(inv ->
                new AsyncOperationDto(inv.getArgument(0), "DONE", null, OffsetDateTime.now(), null, null));
    }

    // ──────────────────────────────────────────────────────
    // Happy path: quota allows 1 instance, user has 0
    // ──────────────────────────────────────────────────────

    @Test
    void createInstance_withinQuota_returns202WithPendingOperation() throws Exception {
        String projectId = "p-wb-create-1";
        String groupId   = "g-wb-create-1";
        String userId    = "u-wb-create-1";

        setQuota(projectId, groupId, userId, 1);

        var req = new CreateInstanceRequestDto("My Notebook", "asia-southeast1-b", "e2-standard-2", 100, null);
        mvc.perform(post("/v1/projects/{p}/workbench/instances?groupId={g}", projectId, groupId)
                        .with(userJwt(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    // ──────────────────────────────────────────────────────
    // Quota gate: quota is 0, creation must be blocked
    // ──────────────────────────────────────────────────────

    @Test
    void createInstance_quotaExceeded_returns422() throws Exception {
        String projectId = "p-wb-create-2";
        String groupId   = "g-wb-create-2";
        String userId    = "u-wb-create-2";

        setQuota(projectId, groupId, userId, 0);

        var req = new CreateInstanceRequestDto("My Notebook", "asia-southeast1-b", "e2-standard-2", 100, null);
        mvc.perform(post("/v1/projects/{p}/workbench/instances?groupId={g}", projectId, groupId)
                        .with(userJwt(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("QUOTA_VIOLATION")));
    }

    // ──────────────────────────────────────────────────────
    // Operation polling
    // ──────────────────────────────────────────────────────

    @Test
    void getOperation_withUserScope_returnsDone() throws Exception {
        mvc.perform(get("/v1/workbench/operations?name=projects/p1/locations/l1/operations/op-123")
                        .with(userJwt("any-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DONE")))
                .andExpect(jsonPath("$.id", is("projects/p1/locations/l1/operations/op-123")));
    }

    // ──────────────────────────────────────────────────────
    // GET single instance: mock returns empty list → always 404
    // ──────────────────────────────────────────────────────

    @Test
    void getInstance_instanceNotOwnedByUser_returns404() throws Exception {
        mvc.perform(get("/v1/projects/p-wb-get/workbench/instances/some-instance-id")
                        .with(userJwt("any-user")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("NOT_FOUND")));
    }

    // ──────────────────────────────────────────────────────
    // START: mock returns empty list → always 404
    // ──────────────────────────────────────────────────────

    @Test
    void startInstance_instanceNotOwnedByUser_returns404() throws Exception {
        mvc.perform(post("/v1/projects/p-wb-start/workbench/instances/some-instance-id:start")
                        .with(userJwt("any-user")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("NOT_FOUND")));
    }

    // ──────────────────────────────────────────────────────
    // STOP: mock returns empty list → always 404
    // ──────────────────────────────────────────────────────

    @Test
    void stopInstance_instanceNotOwnedByUser_returns404() throws Exception {
        mvc.perform(post("/v1/projects/p-wb-stop/workbench/instances/some-instance-id:stop")
                        .with(userJwt("any-user")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("NOT_FOUND")));
    }

    // ──────────────────────────────────────────────────────
    // DELETE: mock returns empty list → always 404
    // ──────────────────────────────────────────────────────

    @Test
    void deleteInstance_instanceNotOwnedByUser_returns404() throws Exception {
        mvc.perform(delete("/v1/projects/p-wb-del/workbench/instances/some-instance-id")
                        .with(userJwt("any-user")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("NOT_FOUND")));
    }

    // ──────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────

    private void setQuota(String projectId, String groupId, String userId, int maxInstances) throws Exception {
        var spec = new UserQuotaSpecDto(maxInstances, 0, List.of(), null, List.of());
        mvc.perform(put("/v1/projects/{p}/managed-groups/{g}/quotas/users/{u}", projectId, groupId, userId)
                        .with(managerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(spec)))
                .andExpect(status().isOk());
    }
}
