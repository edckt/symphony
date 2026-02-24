# Symphony — Architecture & Workflow Diagrams

---

## 0 · UI Integration Overview

```mermaid
%%{init: {'theme': 'base'}}%%
flowchart LR
    classDef actor  fill:#2E4057,stroke:#1C2E3D,color:#fff
    classDef userFt fill:#1B6CA8,stroke:#135587,color:#fff
    classDef mgrFt  fill:#6B3FA0,stroke:#512E82,color:#fff
    classDef userEp fill:#148F77,stroke:#0E6655,color:#fff
    classDef mgrEp  fill:#7D3C98,stroke:#6C3483,color:#fff

    DS(["Data Scientist"]):::actor
    MG(["Manager"]):::actor

    subgraph user_lane["User lane  (workbench.user)"]
        direction LR
        UF["List notebooks\nCreate notebook\nStart / Stop / Delete\nPoll operation\nCheck usage"]:::userFt
        subgraph uep["Symphony — User Endpoints"]
            U1["GET  /workbench/instances"]:::userEp
            U2["POST /workbench/instances"]:::userEp
            U3["POST /instances/{id}:start"]:::userEp
            U4["POST /instances/{id}:stop"]:::userEp
            U5["DEL  /instances/{id}"]:::userEp
            U6["GET  /workbench/operations"]:::userEp
            U7["GET  /self/usage"]:::userEp
        end
    end

    subgraph mgr_lane["Manager lane  (workbench.manager)"]
        direction LR
        MF["Set quota\nView quota\nRemove quota\nSet default\nView audit"]:::mgrFt
        subgraph mep["Symphony — Manager Endpoints"]
            M1["PUT  /quotas/users/{u}"]:::mgrEp
            M2["GET  /quotas/users/{u}"]:::mgrEp
            M3["DEL  /quotas/users/{u}"]:::mgrEp
            M4["PUT  /quotas/default"]:::mgrEp
            M5["GET  /audit/latest"]:::mgrEp
        end
    end

    DS --> UF
    MG --> MF

    UF -->|"list"| U1
    UF -->|"create"| U2
    UF -->|"start"| U3
    UF -->|"stop"| U4
    UF -->|"delete"| U5
    UF -->|"poll"| U6
    UF -->|"usage"| U7

    MF -->|"set"| M1
    MF -->|"view"| M2
    MF -->|"remove"| M3
    MF -->|"default"| M4
    MF -->|"audit"| M5
```

---

## 1 · Component Architecture

```mermaid
%%{init: {'theme': 'base'}}%%
flowchart LR
    classDef actor  fill:#2E4057,stroke:#1C2E3D,color:#fff
    classDef sec    fill:#566573,stroke:#424B54,color:#fff
    classDef mgrApi fill:#6B3FA0,stroke:#512E82,color:#fff
    classDef usrApi fill:#1B6CA8,stroke:#135587,color:#fff
    classDef svc    fill:#117A65,stroke:#0E6655,color:#fff
    classDef db     fill:#BA4A00,stroke:#A04000,color:#fff
    classDef gcp    fill:#1A5276,stroke:#154360,color:#fff

    MGR(["Manager"]):::actor
    USR(["Data Scientist"]):::actor

    subgraph sym["Symphony  ·  Cloud Run"]
        direction LR
        SEC["JWT · @PreAuthorize\nAuthorizationService"]:::sec

        subgraph apis["APIs"]
            direction TB
            subgraph mgr_api["Manager APIs"]
                QC["QuotaController\nPUT/GET/DEL /quotas/users/{u}"]:::mgrApi
                DC["DefaultQuotaController\nPUT/GET/DEL /quotas/default"]:::mgrApi
                AC["AuditController\nGET /audit/latest"]:::mgrApi
            end
            subgraph usr_api["User APIs"]
                WC["WorkbenchController\nPOST/GET/DEL :start :stop /operations"]:::usrApi
                UC["UsageController\nGET /self/usage"]:::usrApi
            end
        end

        subgraph svcs["Services"]
            direction TB
            QES["QuotaEnforcementService\ncomputeUsage · enforceCreate"]:::svc
            QS["QuotaService\nupsert · get · delete · default"]:::svc
            GCAI["GcpCaiService\nlistUserInstances"]:::svc
            GWS["GcpWorkbenchService\ncreate · delete · start · stop · poll"]:::svc
            AUS["AuditService · latestEvent"]:::svc
        end
    end

    DB[("PostgreSQL\nuser_quotas\ndefault_quotas\nquota_audit")]:::db

    subgraph gcp["GCP  (Target Project)"]
        direction TB
        CAI["Cloud Asset Inventory"]:::gcp
        NB["Notebooks API v2"]:::gcp
    end

    MGR -->|"workbench.manager"| SEC
    USR -->|"workbench.user"| SEC
    SEC --> apis

    QC & DC --> QS
    AC --> AUS
    QS & AUS --> DB

    WC & UC --> QES
    QES --> QS
    QES --> GCAI
    GCAI --> CAI

    WC -->|"quota OK"| GWS
    GWS --> NB
```

---

## 2 · Quota Resolution  (3-tier fallback)

```mermaid
%%{init: {'theme': 'base'}}%%
flowchart TD
    classDef start fill:#2E4057,stroke:#1C2E3D,color:#fff
    classDef check fill:#1B6CA8,stroke:#135587,color:#fff
    classDef t1    fill:#1E8449,stroke:#196F3D,color:#fff
    classDef t2    fill:#D4AC0D,stroke:#B7950B,color:#000
    classDef t3    fill:#BA4A00,stroke:#A04000,color:#fff
    classDef out   fill:#566573,stroke:#424B54,color:#fff

    S(["Resolve quota\nproject P · group G · user U"]):::start

    S --> Q1{{"Explicit quota\nfor P + G + U?"}}:::check
    Q1 -->|Yes| T1["EXPLICIT\nUSER_QUOTA"]:::t1
    Q1 -->|No| Q2{{"Default quota\nfor project P?"}}:::check
    Q2 -->|Yes| T2["NONE\nDEFAULT_QUOTA"]:::t2
    Q2 -->|No| T3["NONE\nGLOBAL_DEFAULT\nmaxInstances=3, vCPU=∞"]:::t3

    T1 & T2 & T3 --> OUT(["effectiveSpec → enforcement"]):::out
```

---

## 3 · Create Instance  (end-to-end sequence)

```mermaid
%%{init: {'theme': 'base'}}%%
sequenceDiagram
    autonumber
    actor User
    participant WC  as Workbench API
    participant QES as Enforcement
    participant QS  as QuotaService
    participant DB  as PostgreSQL
    participant CAI as Cloud Asset Inventory
    participant GWS as Workbench Service
    participant NB  as Notebooks API v2

    User->>WC: POST /workbench/instances?groupId={g}
    Note over WC: Validate JWT · resolve bankUserId from email claim

    rect rgb(210, 235, 255)
        Note over WC,CAI: Compute usage snapshot
        WC->>QES: computeUsage(project, group, user, bankUserId)
        QES->>QS: getUserQuota(p, g, user)
        QS->>DB: SELECT quota (3-tier fallback)
        DB-->>QES: effectiveLimits
        QES->>CAI: search by labels.user=normalised-id
        CAI-->>QES: current instances + machine types
        QES-->>WC: usage {instances, vCPU, limits, remaining}
    end

    rect rgb(255, 245, 210)
        Note over WC,QES: Enforce quota
        WC->>QES: enforceCreate(snapshot, request)
        Note over QES: system cap ≤3 · maxInstances<br/>maxTotalVcpu · machine type<br/>zone · boot disk
    end

    alt quota violated
        QES-->>WC: QuotaViolationException
        WC-->>User: 422 QUOTA_VIOLATION {violations, snapshot}
    else quota OK
        rect rgb(210, 255, 230)
            Note over WC,NB: Create instance
            WC->>GWS: createInstance(project, bankUserId, request)
            Note over GWS: instanceId = name + 8-char UUID<br/>labels: notebooks-product=true<br/>boot disk min 150 GB · no public IP
            GWS->>NB: createInstanceAsync(request)
            NB-->>GWS: OperationFuture (LRO name)
            GWS-->>WC: {status: PENDING, id: op-123}
            WC-->>User: 202 Accepted {status: PENDING, id: op-123}
        end

        loop poll until DONE or FAILED
            User->>WC: GET /workbench/operations?name=op-123
            WC->>GWS: getOperation(op-123)
            GWS->>NB: operationsClient.getOperation
            NB-->>GWS: Operation {done: true}
            GWS-->>WC: {status: DONE, resourceId: instances/my-nb}
            WC-->>User: 200 {status: DONE, resourceId: instances/my-nb}
        end
    end
```

---

## 4 · Quota Management  (manager sequence)

```mermaid
%%{init: {'theme': 'base'}}%%
sequenceDiagram
    autonumber
    actor Manager
    participant QC as Quota API
    participant AS as AuthorizationService
    participant QS as QuotaService
    participant DB as PostgreSQL

    rect rgb(210, 235, 255)
        Note over Manager,DB: SET quota
        Manager->>QC: PUT /managed-groups/{g}/quotas/users/{u}
        Note over QC: require workbench.manager
        QC->>AS: assertManagesGroup(manager1, g)
        QC->>AS: assertUserInGroup(u, g)
        AS-->>QC: OK
        QC->>QS: upsertUserQuota(p, g, u, spec)
        QS->>DB: SELECT existing (capture old spec)
        QS->>DB: UPSERT user_quotas
        QS->>DB: INSERT quota_audit (UPSERT)
        QS-->>QC: UserQuotaDto
        QC-->>Manager: 200 {spec, updatedAt, updatedBy}
    end

    rect rgb(255, 245, 210)
        Note over Manager,DB: GET quota
        Manager->>QC: GET /managed-groups/{g}/quotas/users/{u}
        QC->>QS: getUserQuota(p, g, u)
        QS->>DB: SELECT with 3-tier fallback
        DB-->>QS: result
        QC-->>Manager: 200 {status: EXPLICIT, quota, effectiveSpec}
    end

    rect rgb(255, 225, 210)
        Note over Manager,DB: DELETE quota
        Manager->>QC: DELETE /managed-groups/{g}/quotas/users/{u}
        QC->>AS: assertManagesGroup + assertUserInGroup
        QC->>QS: deleteUserQuota(p, g, u)
        QS->>DB: DELETE user_quotas
        QS->>DB: INSERT quota_audit (DELETE)
        QC-->>Manager: 204 No Content
    end
```
