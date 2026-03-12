package com.dbs.symphony.controller;

import com.dbs.symphony.config.CaiProperties;
import com.dbs.symphony.dto.AsyncOperationDto;
import com.dbs.symphony.dto.CreateInstanceRequestDto;
import com.dbs.symphony.dto.EffectiveLimitsDto;
import com.dbs.symphony.dto.SystemCapsDto;
import com.dbs.symphony.dto.UsageTotalsDto;
import com.dbs.symphony.dto.UserQuotaUsageDto;
import com.dbs.symphony.dto.WorkbenchInstanceDto;
import com.dbs.symphony.exception.QuotaViolationException;
import com.dbs.symphony.exception.ForbiddenException;
import com.dbs.symphony.exception.NotFoundException;
import com.dbs.symphony.security.SecurityConfig;
import com.dbs.symphony.service.CaiService;
import com.dbs.symphony.service.InstanceSizeCatalogService;
import com.dbs.symphony.service.OperationService;
import com.dbs.symphony.service.QuotaEnforcementService;
import com.dbs.symphony.service.WorkbenchService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {WorkbenchController.class, OperationsController.class}, properties = "app.security.enabled=true")
@Import(SecurityConfig.class)
class WorkbenchControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockBean CaiService caiService;
    @MockBean QuotaEnforcementService enforcement;
    @MockBean CaiProperties caiProperties;
    @MockBean WorkbenchService workbenchService;
    @MockBean InstanceSizeCatalogService sizeCatalog;
    @MockBean OperationService operationService;
    @MockBean JwtDecoder jwtDecoder;

    // ──────────────────────────────────────────────────────
    // GET /v1/projects/{projectId}/workbench/instances
    // ──────────────────────────────────────────────────────

    @Test
    void listInstances_withoutAuth_returns401() throws Exception {
        mvc.perform(get("/v1/projects/p1/workbench/instances"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listInstances_withManagerScopeOnly_returns403() throws Exception {
        mvc.perform(get("/v1/projects/p1/workbench/instances")
                        .with(managerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listInstances_withUserScope_returns200() throws Exception {
        when(caiService.listUserInstances(anyString(), anyString())).thenReturn(List.of());

        mvc.perform(get("/v1/projects/p1/workbench/instances")
                        .with(userJwt()))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────────────
    // GET /v1/projects/{projectId}/workbench/instance-sizes
    // ──────────────────────────────────────────────────────

    @Test
    void listInstanceSizes_withoutAuth_returns401() throws Exception {
        mvc.perform(get("/v1/projects/p1/workbench/instance-sizes?groupId=g1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listInstanceSizes_withManagerScopeOnly_returns403() throws Exception {
        mvc.perform(get("/v1/projects/p1/workbench/instance-sizes?groupId=g1")
                        .with(managerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listInstanceSizes_withUserScope_returns200() throws Exception {
        var limits = new EffectiveLimitsDto(3, Integer.MAX_VALUE, List.of(), null, List.of());
        var snapshot = new UserQuotaUsageDto("p1", "g1", "user1", null,
                SystemCapsDto.INSTANCE, limits, new UsageTotalsDto(0, 0), new UsageTotalsDto(3, 0), "GLOBAL_DEFAULT");
        when(enforcement.computeUsage(anyString(), anyString(), anyString(), anyString())).thenReturn(snapshot);
        when(sizeCatalog.listSizes(any())).thenReturn(List.of());

        mvc.perform(get("/v1/projects/p1/workbench/instance-sizes?groupId=g1")
                        .with(userJwt()))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────────────
    // POST /v1/projects/{projectId}/workbench/instances
    // ──────────────────────────────────────────────────────

    @Test
    void createInstance_withoutAuth_returns401() throws Exception {
        mvc.perform(post("/v1/projects/p1/workbench/instances?groupId=g1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(instanceBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createInstance_withManagerScopeOnly_returns403() throws Exception {
        mvc.perform(post("/v1/projects/p1/workbench/instances?groupId=g1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(instanceBody())
                        .with(managerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void createInstance_withUserScope_returns202() throws Exception {
        var op = new AsyncOperationDto("op-123", "PENDING", OffsetDateTime.now(), null, null, null);
        when(sizeCatalog.resolve(anyString())).thenReturn(smallSize());
        when(workbenchService.createInstance(anyString(), anyString(), any(), anyString(), anyInt())).thenReturn(op);

        mvc.perform(post("/v1/projects/p1/workbench/instances?groupId=g1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(instanceBody())
                        .with(userJwt()))
                .andExpect(status().isAccepted());
    }

    @Test
    void createInstance_quotaViolation_returns422() throws Exception {
        when(sizeCatalog.resolve(anyString())).thenReturn(smallSize());
        when(enforcement.computeUsage(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new QuotaViolationException(List.of(), null));

        mvc.perform(post("/v1/projects/p1/workbench/instances?groupId=g1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(instanceBody())
                        .with(userJwt()))
                .andExpect(status().isUnprocessableEntity());
    }

    // ──────────────────────────────────────────────────────
    // GET /v1/operations/{operationId}
    // ──────────────────────────────────────────────────────

    @Test
    void getOperation_withoutAuth_returns401() throws Exception {
        String encodedId = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("projects/p1/locations/l1/operations/op-123".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(get("/v1/operations/" + encodedId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOperation_withManagerScope_returns200() throws Exception {
        String encodedId = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("projects/p1/locations/l1/operations/op-123".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var op = new AsyncOperationDto(encodedId, "RUNNING", null, null, null, null);
        when(workbenchService.getOperation(anyString())).thenReturn(op);

        mvc.perform(get("/v1/operations/" + encodedId)
                        .with(managerJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void getOperation_withUserScope_returns200() throws Exception {
        String encodedId = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("projects/p1/locations/l1/operations/op-123".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var op = new AsyncOperationDto(encodedId, "RUNNING", null, null, null, null);
        when(workbenchService.getOperation(anyString())).thenReturn(op);

        mvc.perform(get("/v1/operations/" + encodedId)
                        .with(userJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void getOperation_unknownId_returns404() throws Exception {
        String encodedId = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("projects/p1/locations/l1/operations/unknown".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        doThrow(new NotFoundException("Operation not found: " + encodedId))
                .when(operationService).assertOwnership(anyString(), anyString());

        mvc.perform(get("/v1/operations/" + encodedId)
                        .with(userJwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOperation_differentOwner_returns403() throws Exception {
        String encodedId = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("projects/p1/locations/l1/operations/op-456".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        doThrow(new ForbiddenException("Not authorized to access this operation"))
                .when(operationService).assertOwnership(anyString(), anyString());

        mvc.perform(get("/v1/operations/" + encodedId)
                        .with(userJwt()))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────────────
    // GET /v1/projects/{projectId}/workbench/instances/{instanceId}
    // ──────────────────────────────────────────────────────

    @Test
    void getInstance_withoutAuth_returns401() throws Exception {
        mvc.perform(get("/v1/projects/p1/workbench/instances/inst-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getInstance_withManagerScopeOnly_returns403() throws Exception {
        mvc.perform(get("/v1/projects/p1/workbench/instances/inst-1")
                        .with(managerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getInstance_withUserScope_notOwned_returns404() throws Exception {
        when(caiService.listUserInstances(anyString(), anyString())).thenReturn(List.of());

        mvc.perform(get("/v1/projects/p1/workbench/instances/inst-1")
                        .with(userJwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getInstance_withUserScope_found_returns200() throws Exception {
        var instance = new WorkbenchInstanceDto("inst-1", "My NB", "ACTIVE", "e2-standard-2",
                "asia-southeast1-b", "user1", null);
        when(caiService.listUserInstances(anyString(), anyString())).thenReturn(List.of(instance));

        mvc.perform(get("/v1/projects/p1/workbench/instances/inst-1")
                        .with(userJwt()))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────────────
    // POST /v1/projects/{projectId}/workbench/instances/{instanceId}:start
    // ──────────────────────────────────────────────────────

    @Test
    void startInstance_withoutAuth_returns401() throws Exception {
        mvc.perform(post("/v1/projects/p1/workbench/instances/inst-1:start"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startInstance_withManagerScopeOnly_returns403() throws Exception {
        mvc.perform(post("/v1/projects/p1/workbench/instances/inst-1:start")
                        .with(managerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void startInstance_withUserScope_notOwned_returns404() throws Exception {
        when(caiService.listUserInstances(anyString(), anyString())).thenReturn(List.of());

        mvc.perform(post("/v1/projects/p1/workbench/instances/inst-1:start")
                        .with(userJwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void startInstance_withUserScope_instanceFound_returns202() throws Exception {
        var instance = new WorkbenchInstanceDto("inst-1", "My NB", "STOPPED", "e2-standard-2",
                "asia-southeast1-b", "user1", null);
        when(caiService.listUserInstances(anyString(), anyString())).thenReturn(List.of(instance));
        var op = new AsyncOperationDto("projects/p1/locations/asia-southeast1-b/operations/op-start-1",
                "PENDING", OffsetDateTime.now(), null, null, null);
        when(workbenchService.startInstance(anyString())).thenReturn(op);

        mvc.perform(post("/v1/projects/p1/workbench/instances/inst-1:start")
                        .with(userJwt()))
                .andExpect(status().isAccepted());
    }

    @Test
    void startInstance_withUserScope_wrongState_returns409() throws Exception {
        var instance = new WorkbenchInstanceDto("inst-1", "My NB", "ACTIVE", "e2-standard-2",
                "asia-southeast1-b", "user1", null);
        when(caiService.listUserInstances(anyString(), anyString())).thenReturn(List.of(instance));

        mvc.perform(post("/v1/projects/p1/workbench/instances/inst-1:start")
                        .with(userJwt()))
                .andExpect(status().isConflict());
    }

    // ──────────────────────────────────────────────────────
    // POST /v1/projects/{projectId}/workbench/instances/{instanceId}:stop
    // ──────────────────────────────────────────────────────

    @Test
    void stopInstance_withoutAuth_returns401() throws Exception {
        mvc.perform(post("/v1/projects/p1/workbench/instances/inst-1:stop"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void stopInstance_withManagerScopeOnly_returns403() throws Exception {
        mvc.perform(post("/v1/projects/p1/workbench/instances/inst-1:stop")
                        .with(managerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void stopInstance_withUserScope_notOwned_returns404() throws Exception {
        when(caiService.listUserInstances(anyString(), anyString())).thenReturn(List.of());

        mvc.perform(post("/v1/projects/p1/workbench/instances/inst-1:stop")
                        .with(userJwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void stopInstance_withUserScope_instanceFound_returns202() throws Exception {
        var instance = new WorkbenchInstanceDto("inst-1", "My NB", "ACTIVE", "e2-standard-2",
                "asia-southeast1-b", "user1", null);
        when(caiService.listUserInstances(anyString(), anyString())).thenReturn(List.of(instance));
        var op = new AsyncOperationDto("projects/p1/locations/asia-southeast1-b/operations/op-stop-1",
                "PENDING", OffsetDateTime.now(), null, null, null);
        when(workbenchService.stopInstance(anyString())).thenReturn(op);

        mvc.perform(post("/v1/projects/p1/workbench/instances/inst-1:stop")
                        .with(userJwt()))
                .andExpect(status().isAccepted());
    }

    @Test
    void stopInstance_withUserScope_wrongState_returns409() throws Exception {
        var instance = new WorkbenchInstanceDto("inst-1", "My NB", "STOPPED", "e2-standard-2",
                "asia-southeast1-b", "user1", null);
        when(caiService.listUserInstances(anyString(), anyString())).thenReturn(List.of(instance));

        mvc.perform(post("/v1/projects/p1/workbench/instances/inst-1:stop")
                        .with(userJwt()))
                .andExpect(status().isConflict());
    }

    // ──────────────────────────────────────────────────────
    // DELETE /v1/projects/{projectId}/workbench/instances/{instanceId}
    // ──────────────────────────────────────────────────────

    @Test
    void deleteInstance_withoutAuth_returns401() throws Exception {
        mvc.perform(delete("/v1/projects/p1/workbench/instances/inst-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteInstance_withManagerScopeOnly_returns403() throws Exception {
        mvc.perform(delete("/v1/projects/p1/workbench/instances/inst-1")
                        .with(managerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteInstance_withUserScope_instanceNotOwned_returns404() throws Exception {
        when(caiService.listUserInstances(anyString(), anyString())).thenReturn(List.of());

        mvc.perform(delete("/v1/projects/p1/workbench/instances/inst-1")
                        .with(userJwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteInstance_withUserScope_instanceFound_returns202() throws Exception {
        var instance = new WorkbenchInstanceDto("inst-1", "My NB", "ACTIVE", "e2-standard-2",
                "asia-southeast1-b", "user1", null);
        when(caiService.listUserInstances(anyString(), anyString())).thenReturn(List.of(instance));
        var op = new AsyncOperationDto("projects/p1/locations/asia-southeast1-b/operations/op-del-1",
                "PENDING", OffsetDateTime.now(), null, null, null);
        when(workbenchService.deleteInstance(anyString())).thenReturn(op);

        mvc.perform(delete("/v1/projects/p1/workbench/instances/inst-1")
                        .with(userJwt()))
                .andExpect(status().isAccepted());
    }

    // ──────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────

    private String instanceBody() throws Exception {
        return mapper.writeValueAsString(new CreateInstanceRequestDto(
                "my-notebook", "asia-southeast1-b", "SMALL", null));
    }

    private com.dbs.symphony.config.InstanceSizeProperties.SizeDefinition smallSize() {
        return new com.dbs.symphony.config.InstanceSizeProperties.SizeDefinition(
                "SMALL", "Small", "4 vCPU · 16 GB RAM", "e2-standard-4", 4, 16, 150, false);
    }
}
