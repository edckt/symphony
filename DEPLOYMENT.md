# Symphony Deployment Runbook

## Projects & Key IDs

| Item | Value |
|---|---|
| Calling project (hosts Cloud Run, Cloud SQL) | `oc-project-485203` (number: `803605259759`) |
| Target project (Workbench instances live here) | `ac-project-485203` (number: `266704757709`) |
| Region | `asia-southeast1` |
| Cloud Run service URL | `https://symphony-803605259759.asia-southeast1.run.app` |
| Container image | `asia-southeast1-docker.pkg.dev/oc-project-485203/symphony/symphony:latest` |
| Cloud Run SA | `803605259759-compute@developer.gserviceaccount.com` |
| Target default compute SA | `266704757709-compute@developer.gserviceaccount.com` |
| Cloud SQL instance | `symphony-postgres` (Postgres 16, private IP `10.118.0.3`) |
| VPC connector | `symphony-connector` (range `10.8.0.0/28`, default network) |

---

## One-Time Infrastructure Setup

Only needed when setting up from scratch. Skip if infrastructure already exists.

### Cloud SQL (Postgres 16, private IP only)

```bash
# Org policy enforces ENTERPRISE edition (not ENTERPRISE_PLUS)
gcloud sql instances create symphony-postgres \
  --database-version=POSTGRES_16 --tier=db-f1-micro --edition=ENTERPRISE \
  --region=asia-southeast1 --no-assign-ip \
  --network=default --project=oc-project-485203

gcloud sql databases create workbench \
  --instance=symphony-postgres --project=oc-project-485203

gcloud sql users create workbench \
  --instance=symphony-postgres --password=changeme \
  --project=oc-project-485203
```

### VPC Connector (Cloud Run → Cloud SQL private IP)

```bash
gcloud compute networks vpc-access connectors create symphony-connector \
  --region=asia-southeast1 --range=10.8.0.0/28 \
  --project=oc-project-485203
```

If connectivity fails after creation, re-run VPC peering:
```bash
gcloud services vpc-peerings connect \
  --service=servicenetworking.googleapis.com \
  --ranges=google-managed-services-default \
  --network=default --project=oc-project-485203
```

### Artifact Registry

```bash
gcloud artifacts repositories create symphony \
  --repository-format=docker --location=asia-southeast1 \
  --project=oc-project-485203
```

---

## One-Time IAM & API Setup

Only needed once per environment. All of these are already done for the current setup.

```bash
# 1. Enable APIs on the calling project (oc-project-485203)
gcloud services enable cloudasset.googleapis.com --project=oc-project-485203
gcloud services enable notebooks.googleapis.com  --project=oc-project-485203

# 2. Enable APIs on the target project (ac-project-485203)
gcloud services enable notebooks.googleapis.com --project=ac-project-485203

# 3. Cloud Run SA → read CAI (to list Workbench instances) on target project
gcloud projects add-iam-policy-binding ac-project-485203 \
  --member="serviceAccount:803605259759-compute@developer.gserviceaccount.com" \
  --role="roles/cloudasset.viewer"

# 4. Cloud Run SA → create/manage Workbench instances on target project
gcloud projects add-iam-policy-binding ac-project-485203 \
  --member="serviceAccount:803605259759-compute@developer.gserviceaccount.com" \
  --role="roles/notebooks.runner"

# 5. Cloud Run SA → impersonate target project's default compute SA
#    Required by GCP when creating VMs (org policy enforces no external IPs,
#    so the default compute SA is always attached to the VM)
gcloud iam service-accounts add-iam-policy-binding \
  266704757709-compute@developer.gserviceaccount.com \
  --member="serviceAccount:803605259759-compute@developer.gserviceaccount.com" \
  --role="roles/iam.serviceAccountUser" \
  --project=ac-project-485203
```

---

## Routine Deploy (every release)

```bash
export PROJECT_ID=oc-project-485203
export REGION=asia-southeast1
export IMAGE=$REGION-docker.pkg.dev/$PROJECT_ID/symphony/symphony:latest

# Authenticate Docker (only needed once per machine)
gcloud auth configure-docker $REGION-docker.pkg.dev

# Build, push, deploy
docker build -t $IMAGE . && docker push $IMAGE

gcloud run deploy symphony \
  --image=$IMAGE --region=$REGION --platform=managed \
  --no-allow-unauthenticated --port=8080 --memory=512Mi --cpu=1 \
  --vpc-connector=symphony-connector \
  --env-vars-file=env.yaml \
  --project=$PROJECT_ID
```

### env.yaml (runtime config, not committed with secrets in prod)

```yaml
SPRING_PROFILES_ACTIVE: dev
APP_SECURITY_ENABLED: "false"       # set "true" in prod; enables JWT validation
APP_AUTHZ_MODE: allow-all           # set "group-membership" in prod
APP_CAI_ENABLED: "true"
CAI_USER_ID_CLAIM: email
APP_WORKBENCH_ENABLED: "true"
SPRING_DATASOURCE_URL: "jdbc:postgresql://10.118.0.3:5432/workbench?connectTimeout=10&socketTimeout=30"
SPRING_DATASOURCE_USERNAME: workbench
SPRING_DATASOURCE_PASSWORD: changeme
```

> **Note:** `--no-allow-unauthenticated` is required — org policy
> `constraints/iam.allowedPolicyMemberDomains` blocks `allUsers` IAM bindings.

---

## Smoke Test After Deploy

```bash
TOKEN=$(gcloud auth print-identity-token)
BASE=https://symphony-803605259759.asia-southeast1.run.app

# Health check
curl -s -H "Authorization: Bearer $TOKEN" $BASE/actuator/health | jq .

# Set a quota (dev mode: X-User-Id header simulates the caller identity)
curl -s -X PUT "$BASE/v1/projects/ac-project-485203/managed-groups/g1/quotas/users/user1" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: manager1" \
  -H "Content-Type: application/json" \
  -d '{"maxInstances":1,"maxTotalVcpu":0,"allowedMachineTypes":[],"maxBootDiskGb":null,"allowedZones":[]}' | jq .

# Create a Workbench instance
curl -s -X POST "$BASE/v1/projects/ac-project-485203/workbench/instances?groupId=g1" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: user1" \
  -H "Content-Type: application/json" \
  -d '{"displayName":"my-notebook","zone":"asia-southeast1-b","machineType":"e2-standard-2","bootDiskGb":150}' | jq .

# Poll operation (copy id from above)
OP_NAME="projects/ac-project-485203/locations/asia-southeast1-b/operations/..."
curl -s "$BASE/v1/workbench/operations?name=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))" "$OP_NAME")" \
  -H "Authorization: Bearer $TOKEN" -H "X-User-Id: user1" | jq .

# Verify quota blocks a second instance (expect 422 MAX_INSTANCES_EXCEEDED)
curl -s -X POST "$BASE/v1/projects/ac-project-485203/workbench/instances?groupId=g1" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: user1" \
  -H "Content-Type: application/json" \
  -d '{"displayName":"my-notebook-2","zone":"asia-southeast1-b","machineType":"e2-standard-2","bootDiskGb":150}' | jq .
```

---

## Teardown

```bash
# 1. Delete Workbench instances (most expensive — VM + disk costs)
gcloud workbench instances list --project=ac-project-485203 --location=asia-southeast1-b
gcloud workbench instances delete <instance-name> \
  --project=ac-project-485203 --location=asia-southeast1-b
# Note: requires roles/notebooks.admin on ac-project-485203 for your personal account

# 2. Delete Cloud Run service
gcloud run services delete symphony --region=asia-southeast1 --project=oc-project-485203

# 3. Delete Cloud SQL (significant ongoing cost even idle)
gcloud sql instances delete symphony-postgres --project=oc-project-485203

# 4. Delete Artifact Registry images
gcloud artifacts docker images delete \
  asia-southeast1-docker.pkg.dev/oc-project-485203/symphony/symphony \
  --project=oc-project-485203 --delete-tags

# 5. Delete VPC connector
gcloud compute networks vpc-access connectors delete symphony-connector \
  --region=asia-southeast1 --project=oc-project-485203
```

---

## Org Policy Constraints (oc-project-485203)

These are enforced at the org level and cannot be overridden:

| Constraint | Effect |
|---|---|
| `constraints/compute.vmExternalIpAccess` | No external IPs on VMs → `GceSetup.setDisablePublicIp(true)` required |
| `constraints/sql.restrictPublicIp` | Cloud SQL must use private IP only |
| `constraints/iam.allowedPolicyMemberDomains` | No `allUsers` IAM → use `--no-allow-unauthenticated` |
| Cloud SQL edition | ENTERPRISE enforced (not ENTERPRISE_PLUS) — use `--edition=ENTERPRISE` |

---

## Known GCP Quirks

- **Workbench minimum boot disk**: 150 GB (base image requirement). The service enforces this automatically with `Math.max(150, requested)`.
- **CAI propagation delay**: After a Workbench instance reaches RUNNING, CAI takes ~1 minute to index it. Quota enforcement will see the instance only after indexing.
- **Identity tokens expire**: `gcloud auth print-identity-token` tokens are valid for ~1 hour. Re-run to refresh.
- **VPC connector**: If Cloud Run cannot reach the Cloud SQL private IP after creation, re-run `gcloud services vpc-peerings connect` to activate peering routes.
