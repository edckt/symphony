package com.dbs.symphony.service;

import com.dbs.symphony.dto.AsyncOperationDto;
import com.dbs.symphony.dto.CreateInstanceRequestDto;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.notebooks.v2.CreateInstanceRequest;
import com.google.cloud.notebooks.v2.DeleteInstanceRequest;
import com.google.cloud.notebooks.v2.Instance;
import com.google.cloud.notebooks.v2.NotebookServiceClient;
import com.google.cloud.notebooks.v2.OperationMetadata;
import com.google.cloud.notebooks.v2.StartInstanceRequest;
import com.google.cloud.notebooks.v2.StopInstanceRequest;
import com.google.longrunning.Operation;
import com.google.longrunning.OperationsClient;
import com.google.protobuf.Empty;
import com.google.rpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GcpWorkbenchServiceTest {

    @Mock NotebookServiceClient notebookServiceClient;

    GcpWorkbenchService service;

    @BeforeEach
    void setUp() {
        service = new GcpWorkbenchService(notebookServiceClient);
    }

    // ──────────────────────────────────────────────────────
    // createInstance — GCP request construction
    // ──────────────────────────────────────────────────────

    @Test
    void createInstance_setsCorrectParent() throws Exception {
        ArgumentCaptor<CreateInstanceRequest> captor = ArgumentCaptor.forClass(CreateInstanceRequest.class);
        stubFuture("projects/p1/locations/us-central1-a/operations/op-1");

        service.createInstance("my-project", "user1", request("asia-southeast1-b"));

        verify(notebookServiceClient).createInstanceAsync(captor.capture());
        assertThat(captor.getValue().getParent()).isEqualTo("projects/my-project/locations/asia-southeast1-b");
    }

    @Test
    void createInstance_appliesSystemLabels() throws Exception {
        ArgumentCaptor<CreateInstanceRequest> captor = ArgumentCaptor.forClass(CreateInstanceRequest.class);
        stubFuture("op-name");

        service.createInstance("p1", "bankUser", request("zone-a"));

        verify(notebookServiceClient).createInstanceAsync(captor.capture());
        var labels = captor.getValue().getInstance().getLabelsMap();
        assertThat(labels).containsEntry("notebooks-product", "true");
        assertThat(labels).containsEntry("user", "bankuser");  // lowercased
    }

    @Test
    void createInstance_normalizesEmailBankUserId() throws Exception {
        ArgumentCaptor<CreateInstanceRequest> captor = ArgumentCaptor.forClass(CreateInstanceRequest.class);
        stubFuture("op-name");

        service.createInstance("p1", "user@example.com", request("zone-a"));

        verify(notebookServiceClient).createInstanceAsync(captor.capture());
        assertThat(captor.getValue().getInstance().getLabelsMap())
                .containsEntry("user", "user-example-com");
    }

    @Test
    void createInstance_mergesCallerLabels() throws Exception {
        ArgumentCaptor<CreateInstanceRequest> captor = ArgumentCaptor.forClass(CreateInstanceRequest.class);
        stubFuture("op-name");

        var requestWithLabels = new CreateInstanceRequestDto(
                "my-nb", "zone-a", "e2-standard-2", 100, Map.of("team", "data-eng"));
        service.createInstance("p1", "user1", requestWithLabels);

        verify(notebookServiceClient).createInstanceAsync(captor.capture());
        var labels = captor.getValue().getInstance().getLabelsMap();
        assertThat(labels).containsEntry("team", "data-eng");
        assertThat(labels).containsEntry("notebooks-product", "true");
        assertThat(labels).containsEntry("user", "user1");
    }

    @Test
    void createInstance_systemLabelsOverrideCallerLabels() throws Exception {
        // caller cannot overwrite the mandatory "user" label
        ArgumentCaptor<CreateInstanceRequest> captor = ArgumentCaptor.forClass(CreateInstanceRequest.class);
        stubFuture("op-name");

        var requestWithConflict = new CreateInstanceRequestDto(
                "my-nb", "zone-a", "e2-standard-2", 100, Map.of("user", "attacker"));
        service.createInstance("p1", "legit-user", requestWithConflict);

        verify(notebookServiceClient).createInstanceAsync(captor.capture());
        assertThat(captor.getValue().getInstance().getLabelsMap())
                .containsEntry("user", "legit-user");  // not "attacker"
    }

    @Test
    void createInstance_setsBootDiskSizeGb() throws Exception {
        ArgumentCaptor<CreateInstanceRequest> captor = ArgumentCaptor.forClass(CreateInstanceRequest.class);
        stubFuture("op-name");

        var req = new CreateInstanceRequestDto("my-nb", "zone-a", "e2-standard-2", 200, null);
        service.createInstance("p1", "user1", req);

        verify(notebookServiceClient).createInstanceAsync(captor.capture());
        assertThat(captor.getValue().getInstance().getGceSetup().getBootDisk().getDiskSizeGb())
                .isEqualTo(200L);
    }

    @Test
    void createInstance_defaultBootDiskWhenNull() throws Exception {
        ArgumentCaptor<CreateInstanceRequest> captor = ArgumentCaptor.forClass(CreateInstanceRequest.class);
        stubFuture("op-name");

        var req = new CreateInstanceRequestDto("my-nb", "zone-a", "e2-standard-2", null, null);
        service.createInstance("p1", "user1", req);

        verify(notebookServiceClient).createInstanceAsync(captor.capture());
        assertThat(captor.getValue().getInstance().getGceSetup().getBootDisk().getDiskSizeGb())
                .isEqualTo(150L);  // minimum enforced by GCP org policy
    }

    @Test
    void createInstance_setsMachineTypeAndDefaultNetwork() throws Exception {
        ArgumentCaptor<CreateInstanceRequest> captor = ArgumentCaptor.forClass(CreateInstanceRequest.class);
        stubFuture("op-name");

        service.createInstance("my-project", "user1", request("zone-a"));

        verify(notebookServiceClient).createInstanceAsync(captor.capture());
        var gceSetup = captor.getValue().getInstance().getGceSetup();
        assertThat(gceSetup.getMachineType()).isEqualTo("e2-standard-2");
        assertThat(gceSetup.getNetworkInterfaces(0).getNetwork())
                .isEqualTo("projects/my-project/global/networks/default");
    }

    @Test
    void createInstance_returnsLroNameAsId() throws Exception {
        stubFuture("projects/p1/locations/zone-a/operations/op-99");

        AsyncOperationDto dto = service.createInstance("p1", "user1", request("zone-a"));

        assertThat(dto.id()).isEqualTo("projects/p1/locations/zone-a/operations/op-99");
        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.createdAt()).isNotNull();
    }

    @Test
    void createInstance_derivesInstanceIdFromDisplayName() throws Exception {
        ArgumentCaptor<CreateInstanceRequest> captor = ArgumentCaptor.forClass(CreateInstanceRequest.class);
        stubFuture("op-name");

        var req = new CreateInstanceRequestDto("My Notebook 2024", "zone-a", "e2-standard-2", 100, null);
        service.createInstance("p1", "user1", req);

        verify(notebookServiceClient).createInstanceAsync(captor.capture());
        String instanceId = captor.getValue().getInstanceId();
        // should start with normalized display name
        assertThat(instanceId).startsWith("my-notebook-2024-");
        // and have a UUID-derived suffix
        assertThat(instanceId).matches("my-notebook-2024-[a-f0-9]{8}");
    }

    // ──────────────────────────────────────────────────────
    // getOperation — status mapping
    // ──────────────────────────────────────────────────────

    @Test
    void getOperation_whenRunning_returnsRunning() {
        stubOperationsClient(Operation.newBuilder().setDone(false).build());

        AsyncOperationDto dto = service.getOperation("projects/p1/locations/l1/operations/op-123");

        assertThat(dto.status()).isEqualTo("RUNNING");
        assertThat(dto.doneAt()).isNull();
        assertThat(dto.error()).isNull();
    }

    @Test
    void getOperation_whenDone_returnsDone() {
        stubOperationsClient(Operation.newBuilder().setDone(true).build());

        AsyncOperationDto dto = service.getOperation("projects/p1/locations/l1/operations/op-123");

        assertThat(dto.status()).isEqualTo("DONE");
        assertThat(dto.doneAt()).isNotNull();
        assertThat(dto.error()).isNull();
    }

    @Test
    void getOperation_whenFailed_returnsFailedWithError() {
        var errorStatus = Status.newBuilder().setMessage("quota exceeded").build();
        var op = Operation.newBuilder().setDone(true).setError(errorStatus).build();
        stubOperationsClient(op);

        AsyncOperationDto dto = service.getOperation("projects/p1/locations/l1/operations/op-123");

        assertThat(dto.status()).isEqualTo("FAILED");
        assertThat(dto.doneAt()).isNotNull();
        assertThat(dto.error()).isNotNull();
        assertThat(dto.error().message()).isEqualTo("quota exceeded");
    }

    // ──────────────────────────────────────────────────────
    // deleteInstance — GCP request construction
    // ──────────────────────────────────────────────────────

    @Test
    void deleteInstance_returnsLroNameAsId() throws Exception {
        stubDeleteFuture("projects/p1/locations/asia-southeast1-b/operations/op-del-99");

        AsyncOperationDto dto = service.deleteInstance(
                "projects/p1/locations/asia-southeast1-b/instances/my-nb-abc123");

        assertThat(dto.id()).isEqualTo("projects/p1/locations/asia-southeast1-b/operations/op-del-99");
        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.createdAt()).isNotNull();
    }

    @Test
    void deleteInstance_passesInstanceNameToGcp() throws Exception {
        ArgumentCaptor<DeleteInstanceRequest> captor = ArgumentCaptor.forClass(DeleteInstanceRequest.class);
        stubDeleteFuture("op-del");

        service.deleteInstance("projects/p1/locations/asia-southeast1-b/instances/my-nb-abc123");

        verify(notebookServiceClient).deleteInstanceAsync(captor.capture());
        assertThat(captor.getValue().getName())
                .isEqualTo("projects/p1/locations/asia-southeast1-b/instances/my-nb-abc123");
    }

    // ──────────────────────────────────────────────────────
    // startInstance — GCP request construction
    // ──────────────────────────────────────────────────────

    @Test
    void startInstance_returnsLroNameAsId() throws Exception {
        stubStartFuture("projects/p1/locations/asia-southeast1-b/operations/op-start-99");

        AsyncOperationDto dto = service.startInstance(
                "projects/p1/locations/asia-southeast1-b/instances/my-nb-abc123");

        assertThat(dto.id()).isEqualTo("projects/p1/locations/asia-southeast1-b/operations/op-start-99");
        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.createdAt()).isNotNull();
    }

    @Test
    void startInstance_passesInstanceNameToGcp() throws Exception {
        ArgumentCaptor<StartInstanceRequest> captor = ArgumentCaptor.forClass(StartInstanceRequest.class);
        stubStartFuture("op-start");

        service.startInstance("projects/p1/locations/asia-southeast1-b/instances/my-nb-abc123");

        verify(notebookServiceClient).startInstanceAsync(captor.capture());
        assertThat(captor.getValue().getName())
                .isEqualTo("projects/p1/locations/asia-southeast1-b/instances/my-nb-abc123");
    }

    // ──────────────────────────────────────────────────────
    // stopInstance — GCP request construction
    // ──────────────────────────────────────────────────────

    @Test
    void stopInstance_returnsLroNameAsId() throws Exception {
        stubStopFuture("projects/p1/locations/asia-southeast1-b/operations/op-stop-99");

        AsyncOperationDto dto = service.stopInstance(
                "projects/p1/locations/asia-southeast1-b/instances/my-nb-abc123");

        assertThat(dto.id()).isEqualTo("projects/p1/locations/asia-southeast1-b/operations/op-stop-99");
        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.createdAt()).isNotNull();
    }

    @Test
    void stopInstance_passesInstanceNameToGcp() throws Exception {
        ArgumentCaptor<StopInstanceRequest> captor = ArgumentCaptor.forClass(StopInstanceRequest.class);
        stubStopFuture("op-stop");

        service.stopInstance("projects/p1/locations/asia-southeast1-b/instances/my-nb-abc123");

        verify(notebookServiceClient).stopInstanceAsync(captor.capture());
        assertThat(captor.getValue().getName())
                .isEqualTo("projects/p1/locations/asia-southeast1-b/instances/my-nb-abc123");
    }

    // ──────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void stubDeleteFuture(String operationName) throws InterruptedException, ExecutionException {
        OperationFuture<Empty, OperationMetadata> future = mock(OperationFuture.class);
        when(future.getName()).thenReturn(operationName);
        when(notebookServiceClient.deleteInstanceAsync(any(DeleteInstanceRequest.class)))
                .thenReturn(future);
    }

    @SuppressWarnings("unchecked")
    private void stubStartFuture(String operationName) throws InterruptedException, ExecutionException {
        OperationFuture<Instance, OperationMetadata> future = mock(OperationFuture.class);
        when(future.getName()).thenReturn(operationName);
        when(notebookServiceClient.startInstanceAsync(any(StartInstanceRequest.class)))
                .thenReturn(future);
    }

    @SuppressWarnings("unchecked")
    private void stubStopFuture(String operationName) throws InterruptedException, ExecutionException {
        OperationFuture<Instance, OperationMetadata> future = mock(OperationFuture.class);
        when(future.getName()).thenReturn(operationName);
        when(notebookServiceClient.stopInstanceAsync(any(StopInstanceRequest.class)))
                .thenReturn(future);
    }

    @SuppressWarnings("unchecked")
    private void stubFuture(String operationName) throws InterruptedException, ExecutionException {
        OperationFuture<Instance, OperationMetadata> future = mock(OperationFuture.class);
        when(future.getName()).thenReturn(operationName);
        when(notebookServiceClient.createInstanceAsync(any(CreateInstanceRequest.class)))
                .thenReturn(future);
    }

    private void stubOperationsClient(Operation op) {
        OperationsClient opsClient = mock(OperationsClient.class);
        when(notebookServiceClient.getOperationsClient()).thenReturn(opsClient);
        when(opsClient.getOperation(anyString())).thenReturn(op);
    }

    private CreateInstanceRequestDto request(String zone) {
        return new CreateInstanceRequestDto("my-notebook", zone, "e2-standard-2", 100, null);
    }
}
