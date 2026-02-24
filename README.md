# Symphony Orchestrator API

Spring Boot service deployed on GKE that manages Vertex AI Workbench quotas and provisioning. It acts as an enforcement layer between users/managers and GCP — checking live instance counts via Cloud Asset Inventory before allowing new Workbench instances to be created.

---

## Table of Contents

- [Architecture](#architecture)
- [API Overview](#api-overview)
- [Quota System](#quota-system)
- [Security & Authentication](#security--authentication)
- [Authorization (Domain Checks)](#authorization-domain-checks)
- [CAI Integration](#cai-integration)
- [Database](#database)
- [Local Development](#local-development)
- [Configuration Reference](#configuration-reference)
- [Kubernetes Deployment](#kubernetes-deployment)
- [GCP Prerequisites](#gcp-prerequisites)
- [Pre-Deployment Checklist](#pre-deployment-checklist)

---

## Architecture

```
                        ┌─────────────────────────────────────────┐
                        │             Symphony API                │
                        │                                         │
  User (workbench.user) │  WorkbenchController  UsageController   │
  Manager (w.manager)   │  QuotaController      AuditController   │
                        │                                         │
                        │         QuotaEnforcementService         │
                        │        /          |           \         │
                        │  QuotaService  CaiService  MachineType  │
                        │   (Postgres)    (GCP CAI)   Service     │
                        └─────────────────────────────────────────┘
                              |                    |
                         Cloud SQL             Cloud Asset
                         (Postgres)            Inventory
```

**Request flow for `POST /workbench/instances`:**
1. JWT validated by Spring Security (OAuth2 resource server)
2. Authorization check — caller must have `workbench.user` scope
3. `QuotaEnforcementService` queries CAI for the caller's live instances
4. Effective limits resolved from the three-tier quota fallback
5. All quota checks evaluated; `422 QuotaViolationError` returned if any fail
6. `202 AsyncOperation{status: PENDING}` returned (GCP Workbench creation wired in a follow-up)

---

## API Overview

Interactive docs available at `/swagger-ui.html` when running.

### User endpoints (`workbench.user` scope)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/v1/self` | Current caller identity and roles |
| `GET` | `/v1/projects/{projectId}/workbench/instances` | List caller's Workbench instances (via CAI) |
| `POST` | `/v1/projects/{projectId}/workbench/instances?groupId=` | Create instance — quota enforced, returns `AsyncOperation` |
| `GET` | `/v1/projects/{projectId}/workbench/instances/{id}` | Get instance detail |
| `DELETE` | `/v1/projects/{projectId}/workbench/instances/{id}` | Delete instance |
| `POST` | `/v1/projects/{projectId}/workbench/instances/{id}:start` | Start stopped instance |
| `POST` | `/v1/projects/{projectId}/workbench/instances/{id}:stop` | Stop running instance |
| `GET` | `/v1/projects/{projectId}/self/usage?groupId=` | Live usage snapshot with remaining headroom |
| `GET` | `/v1/operations/{operationId}` | Poll async operation status |

### Manager endpoints (`workbench.manager` scope)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/v1/self/managed-groups` | Groups the caller manages |
| `GET` | `/v1/groups/{groupId}` | Group metadata |
| `GET` | `/v1/groups/{groupId}/members` | Users in a managed group |
| `PUT` | `/v1/projects/{projectId}/managed-groups/{groupId}/quotas/users/{userId}` | Set user quota |
| `GET` | `/v1/projects/{projectId}/managed-groups/{groupId}/quotas/users/{userId}` | Get user quota |
| `DELETE` | `/v1/projects/{projectId}/managed-groups/{groupId}/quotas/users/{userId}` | Remove user quota |
| `POST` | `/v1/projects/{projectId}/managed-groups/{groupId}/quotas/users:batchGet` | Batch get quotas |
| `POST` | `/v1/projects/{projectId}/managed-groups/{groupId}/usage/users:batchGet` | Batch get usage |
| `POST` | `/v1/projects/{projectId}/managed-groups/{groupId}/effective-quota:batchForGroup` | Dashboard rows (limits + usage) |
| `GET` | `/v1/projects/{projectId}/managed-groups/{groupId}/quotas/users/{userId}/audit/latest` | Latest quota change |
| `GET` | `/v1/projects/{projectId}/quotas/default` | Get project default quota |
| `PUT` | `/v1/projects/{projectId}/quotas/default` | Set project default quota |
| `DELETE` | `/v1/projects/{projectId}/quotas/default` | Delete project default quota |

---

## Quota System

### Three-tier fallback

When a quota check is needed for a user, the effective limits are resolved in order:

```
1. USER_QUOTA       — explicit per-user quota set by a manager for this (project, group, user)
2. DEFAULT_QUOTA    — project-level default quota set by a manager via PUT /quotas/default
3. GLOBAL_DEFAULT   — hardcoded fallback: 3 instances, unlimited vCPU, no machine type/zone/disk restrictions
```

The `effectiveSource` field on all quota lookup and usage responses indicates which tier applied.

### System cap

The platform enforces a hard cap of **3 instances per user**, regardless of what a manager configures. A manager quota of 5 is silently clamped to 3. This ensures the system cap is always respected without requiring managers to know about it.

### Quota spec fields

| Field | Type | Description |
|-------|------|-------------|
| `maxInstances` | integer | Max concurrent instances; system cap of 3 still applies |
| `maxTotalVcpu` | integer | Max total vCPUs across all instances; `0` = unlimited |
| `allowedMachineTypes` | string[] | Allowlist; empty array = no restriction |
| `allowedZones` | string[] | Zone allowlist; empty array = no restriction |
| `maxBootDiskGb` | integer | Max boot disk size in GB; null = no restriction |

### Violation codes

When `POST /workbench/instances` is rejected with `422`, the response body contains a `QuotaViolationError` with one or more of:

| Code | Meaning |
|------|---------|
| `SYSTEM_MAX_INSTANCES_EXCEEDED` | Hard platform cap of 3 reached |
| `MAX_INSTANCES_EXCEEDED` | Manager-configured instance limit reached |
| `MAX_VCPU_EXCEEDED` | Adding the requested machine type would exceed the vCPU limit |
| `MACHINE_TYPE_NOT_ALLOWED` | Requested machine type not in the allowlist |
| `ZONE_NOT_ALLOWED` | Requested zone not in the allowlist |
| `DISK_TOO_LARGE` | Requested boot disk exceeds the configured maximum |

### vCPU counting

vCPU count is derived from the machine type name by parsing the last hyphen-delimited token (e.g. `n2-standard-8` → 8, `e2-standard-4` → 4). For custom machine types (`custom-N-MEMORY`) the second token is used. Unrecognisable types default to 8.

---

## Security & Authentication

All endpoints require a valid JWT bearer token. The service operates as an OAuth2 resource server.

Configure the JWT issuer in `application-prod.yaml` or via environment variable:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI}
```

**Scopes checked by Spring Security (`@PreAuthorize`):**
- `workbench.user` — end-user access (instance lifecycle, usage)
- `workbench.manager` — manager access (quota management, group directory)

Security can be disabled for local development:

```yaml
app:
  security:
    enabled: false
```

---

## Authorization (Domain Checks)

In addition to scope checks, manager endpoints enforce domain-level authorization: the caller must manage the `{groupId}` and the `{userId}` must belong to that group.

| Config key | Values | Description |
|------------|--------|-------------|
| `app.authz.mode` | `allow-all` \| `config` | `allow-all` skips domain checks (local dev); `config` enforces the maps below |
| `app.authz.managed-groups-by-principal` | map | Which groups each manager principal can manage; `"*"` key applies to all principals |
| `app.authz.users-by-group` | map | Which users belong to each group; `"*"` key applies to all groups |

**Important:** If `mode: config` and both maps are empty, the application refuses to start with an `IllegalStateException`.

### Example config (YAML)

```yaml
app:
  authz:
    mode: config
    managed-groups-by-principal:
      manager1: [gA, gB]
      "*": [gPublic]
    users-by-group:
      gA: [u123, u456]
      gB: [u789]
      "*": [uShared]
```

### Kubernetes-friendly override (via `SPRING_APPLICATION_JSON`)

```bash
export SPRING_APPLICATION_JSON='{
  "app": {
    "authz": {
      "mode": "config",
      "managed-groups-by-principal": { "manager1": ["gA", "gB"] },
      "users-by-group": { "gA": ["u123", "u456"] }
    }
  }
}'
```

---

## CAI Integration

Instance counts and machine types are sourced live from [Cloud Asset Inventory](https://cloud.google.com/asset-inventory/docs/overview) rather than maintained in the local database.

**Query issued:** `labels.notebooks-product:* AND labels.user:{bankUserId}` on asset type `compute.googleapis.com/Instance` scoped to the GCP project.

### User identity on GCP labels

Workbench instances are labelled with the user's bank staff ID (`labels.user`), which may differ from the JWT `sub`. Configure which JWT claim holds the bank ID:

```yaml
app:
  cai:
    user-id-claim: preferred_username   # set to null to fall back to JWT subject
```

### Enabling/disabling CAI

| Profile | `app.cai.enabled` | Behaviour |
|---------|-------------------|-----------|
| `dev` / local | `false` | `DisabledCaiService` returns empty list; logs a warning |
| `uat` / `prod` | `true` | `GcpCaiService` queries CAI via Workload Identity |

When CAI is disabled, all quota checks against instance counts will pass (usage = 0).

---

## Database

PostgreSQL managed via Flyway. Two migrations, applied on startup.

### `user_quotas` (V1)

Stores explicit per-user quotas set by managers.

| Column | Type | Description |
|--------|------|-------------|
| `id` | text PK | UUID |
| `project_id` | text | GCP project |
| `group_id` | text | Manager's group context |
| `user_id` | text | Bank staff ID |
| `spec_json` | text | Serialised `UserQuotaSpec` |
| `updated_at` | timestamptz | Last modified |
| `updated_by` | text | Manager principal |

Unique constraint and index on `(project_id, group_id, user_id)`.

Also creates `default_quotas` — project-wide fallback quotas with unique constraint on `(project_id)`.

### `quota_audit` (V2)

Append-only audit log of all quota changes.

| Column | Type | Description |
|--------|------|-------------|
| `event_id` | text PK | UUID |
| `project_id` | text | GCP project |
| `group_id` | text | Group context |
| `user_id` | text | Subject of the change |
| `action` | text | `UPSERT` or `DELETE` |
| `at` | timestamptz | Event timestamp |
| `actor_principal` | text | Who made the change |
| `old_spec_json` | text null | Spec before change |
| `new_spec_json` | text null | Spec after change |

Index on `(project_id, group_id, user_id, at desc)`.

---

## Local Development

**Prerequisites:** Docker, Java 21, Maven (or use the included `./mvnw` wrapper)

**Start Postgres:**

```bash
docker compose up -d
```

**Run the application:**

```bash
./mvnw spring-boot:run
```

The `dev` profile (`application-dev.yaml`) has security disabled and `authz.mode: allow-all`. Flyway runs automatically on startup and creates the schema from a clean database.

**Run tests:**

```bash
./mvnw test
```

Integration tests use Testcontainers to spin up a real Postgres instance — Docker must be running.

**Browse the API:**

Open `http://localhost:8080/swagger-ui.html` for the Swagger UI.

---

## Configuration Reference

All properties can be overridden via environment variables using Spring's relaxed binding (e.g. `APP_CAI_ENABLED=true`).

| Property | Default | Description |
|----------|---------|-------------|
| `app.security.enabled` | `false` | Enable JWT enforcement; set `true` in prod/uat |
| `app.authz.mode` | `allow-all` | `allow-all` or `config` |
| `app.authz.managed-groups-by-principal` | `{}` | Manager → groups map (required when mode=config) |
| `app.authz.users-by-group` | `{}` | Group → users map (required when mode=config) |
| `app.cai.enabled` | `false` | Enable Cloud Asset Inventory queries |
| `app.cai.user-id-claim` | _(null)_ | JWT claim for bank staff ID; null falls back to `sub` |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | _(required in prod)_ | JWT issuer URL |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/workbench` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `workbench` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | `workbench` | DB password |
| `JWT_ISSUER_URI` | _(none)_ | Shorthand env var mapping to the JWT issuer URI |
| `CAI_USER_ID_CLAIM` | `preferred_username` | Shorthand for `app.cai.user-id-claim` in prod/uat |

---

## Kubernetes Deployment

Manifests use [Kustomize](https://kustomize.io/). Structure:

```
manifests/
  base/               — shared Deployment, Service, ServiceAccount, ConfigMap, Secret templates
  overlays/
    local/            — local Docker / kind cluster
    dev/              — development GKE namespace
    uat/              — UAT GKE namespace
    prod/             — production GKE namespace
```

**Preview rendered manifests (no apply):**

```bash
kubectl kustomize manifests/overlays/prod
```

**Apply:**

```bash
kubectl apply -k manifests/overlays/dev
kubectl apply -k manifests/overlays/uat
kubectl apply -k manifests/overlays/prod
```

### Placeholders to fill before deploying

| File | Placeholder | Replace with |
|------|-------------|--------------|
| `manifests/base/deployment.yaml` | `PROJECT_ID`, `REPOSITORY` | Artifact Registry project and repo name |
| `manifests/base/serviceaccount.yaml` | `PROJECT_ID` | GCP project ID |
| `manifests/overlays/*/patch-cloudsql-proxy.yaml` | `PROJECT_ID:REGION:INSTANCE_*` | Cloud SQL instance connection name per env |
| `manifests/overlays/*/patch-secret.yaml` | `*-change-me` | Real database password |
| `manifests/overlays/prod/patch-configmap.yaml` | _(add `JWT_ISSUER_URI`)_ | Your IdP issuer URL |

### Required secrets per overlay

The `symphony-db` Secret must contain:

```yaml
SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/workbench   # localhost = Cloud SQL Proxy sidecar
SPRING_DATASOURCE_USERNAME: workbench
SPRING_DATASOURCE_PASSWORD: <real password>
```

The `symphony-config` ConfigMap for prod/uat must include:

```yaml
SPRING_PROFILES_ACTIVE: prod
APP_SECURITY_ENABLED: "true"
APP_AUTHZ_MODE: config
JWT_ISSUER_URI: https://your-idp.example.com/
```

The authz maps must also be populated, either in the ConfigMap or via `SPRING_APPLICATION_JSON`.

---

## GCP Prerequisites

### Workload Identity

The GKE service account (`symphony` KSA) must be bound to a GCP service account (GSA):

```bash
# Create the GSA
gcloud iam service-accounts create symphony-gsa \
  --project=PROJECT_ID

# Allow the KSA to impersonate the GSA
gcloud iam service-accounts add-iam-policy-binding \
  symphony-gsa@PROJECT_ID.iam.gserviceaccount.com \
  --role=roles/iam.workloadIdentityUser \
  --member="serviceAccount:PROJECT_ID.svc.id.goog[NAMESPACE/symphony]"
```

### Cloud SQL

```bash
gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:symphony-gsa@PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/cloudsql.client"
```

The Cloud SQL Auth Proxy sidecar is included in all overlays and listens on `localhost:5432`.

### Cloud Asset Inventory

```bash
# Enable the API
gcloud services enable cloudasset.googleapis.com --project=PROJECT_ID

# Grant viewer role to the GSA
gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:symphony-gsa@PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/cloudasset.viewer"
```

---

## Pre-Deployment Checklist

Before running `kubectl apply` for the first time:

- [ ] Replace `PROJECT_ID` / `REPOSITORY` in `manifests/base/deployment.yaml`
- [ ] Replace `PROJECT_ID` in `manifests/base/serviceaccount.yaml`
- [ ] Replace Cloud SQL instance connection names in each `patch-cloudsql-proxy.yaml`
- [ ] Set real DB password in each `patch-secret.yaml`
- [ ] Add `JWT_ISSUER_URI` to prod/uat ConfigMap
- [ ] Populate authz maps (`APP_AUTHZ_MANAGED_GROUPS_BY_PRINCIPAL` / `APP_AUTHZ_USERS_BY_GROUP`) or temporarily set `APP_AUTHZ_MODE: allow-all`
- [ ] Create GSA `symphony-gsa@PROJECT_ID.iam.gserviceaccount.com`
- [ ] Bind GSA to KSA via Workload Identity
- [ ] Grant `roles/cloudsql.client` to the GSA
- [ ] Grant `roles/cloudasset.viewer` to the GSA
- [ ] Enable Cloud Asset Inventory API on the project
