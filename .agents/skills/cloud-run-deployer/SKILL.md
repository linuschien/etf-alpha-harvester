---
name: cloud-run-deployer
description: Pushes a locally built Docker image to Google Artifact Registry and triggers a rolling redeployment of the target Google Cloud Run service. Reads GCP configuration from the active gcloud CLI context — no hardcoded credentials required.
---

# Skill: Cloud Run Deployer

## ℹ️ Objective

Take a pre-built local Docker image (produced by the `docker-image-builder` skill) and publish it to GCP, then trigger a zero-downtime rolling update of the Cloud Run service. Rather than using a static `latest` tag, the deployer automatically extracts the unique timestamped tag associated with the latest built image ID in the local Docker daemon.

> [!IMPORTANT]
> This skill has a **single prerequisite**: a locally available Docker image named `score-assistant:latest` and its corresponding unique tag (`score-assistant:<TIMESTAMP>-<SHA>`) created by the `docker-image-builder` skill.

---

## 🛠️ Deployment Rules

### 1. GCP Configuration Resolution

All GCP target values **MUST** be resolved from the active `gcloud` CLI context at runtime — never hardcoded in prompts or re-specified by the agent:

```bash
PROJECT_ID=$(gcloud config get-value project)
REGION=$(gcloud config get-value run/region)
```

If `run/region` is unset, fall back to `compute/region`. If both are unset, abort with a clear error message instructing the user to run:

```bash
gcloud config set run/region <REGION>
```

### 2. Artifact Registry Authentication

Before any `docker push`, verify the credential helper is already set. Only call `gcloud auth configure-docker` when **not yet configured** — it persists to `~/.docker/config.json` across sessions.

> [!WARNING]
> Do **NOT** use `docker-credential-gcloud list` to check this. That command only lists `gcr.io` endpoints (old Container Registry), not `pkg.dev` (Artifact Registry). The authoritative check is `~/.docker/config.json`:

```bash
REGISTRY_HOST="${REGION}-docker.pkg.dev"
CREDHELPER=$(grep -F "${REGISTRY_HOST}" "${HOME}/.docker/config.json" 2>/dev/null || true)
if [ -z "${CREDHELPER}" ]; then
  gcloud auth configure-docker "${REGISTRY_HOST}" --quiet
fi
```

### 3. Tag → Push → Update Pipeline

The deployer dynamically queries the local Docker daemon for all tags matching the pattern `score-assistant:YYYYMMDD-HHMMSS-sha`, sorts them in reverse chronological order, and extracts the newest tag directly. The three steps MUST execute in strict order with abort-on-error (`set -euo pipefail`):

| Step | Command | Purpose |
|------|---------|---------|
| Tag | `docker tag score-assistant:<TAG> <REGISTRY_URL>` | Map local unique-tagged image to remote coordinate |
| Push | `docker push <REGISTRY_URL>` | Upload image layers to Artifact Registry |
| Update | `gcloud run services update <SERVICE> --image=<REGISTRY_URL> --region=<REGION> --project=<PROJECT_ID> --quiet` | Surgically swap image only — preserves all other service config |

### 4. Preserve Existing Cloud Run Configuration

Use `gcloud run services update --image=...` — **not** `gcloud run deploy`. This command performs a pure image swap:
- ✅ Preserves environment variables, GCS volume mounts, IAM bindings, scaling, and traffic splits
- ❌ Do NOT use `gcloud run deploy` unless creating the service for the first time (it accepts config-altering flags that may accidentally override existing settings)

---

## 📄 Deploy Script (`scripts/deploy.sh`)

The automation script MUST be located at:

```
.agents/skills/cloud-run-deployer/scripts/deploy.sh
```

And it MUST be kept executable:

```bash
chmod +x .agents/skills/cloud-run-deployer/scripts/deploy.sh
```

### Script Responsibilities

1. Resolve `PROJECT_ID` and `REGION` from active `gcloud` config
2. Validate that the source image `score-assistant:latest` exists locally
3. Authenticate Docker with Artifact Registry
4. Tag the image for the resolved registry coordinate
5. Push the image
6. Trigger `gcloud run services update` to swap the image surgically

---

## ⚠️ Output Requirements & Verification

- **Script**: `.agents/skills/cloud-run-deployer/scripts/deploy.sh`
- **Security Checks**:
  - No GCP credentials, service account keys, or tokens may be embedded in the script
  - All GCP values (project, region, repository) MUST be resolved dynamically from `gcloud config`
- **Verification**:
  - After the service update completes, the script MUST print the live service URL
  - Confirm that the new revision is serving traffic via:
    ```bash
    gcloud run services describe score-assistant --region=<REGION> --format='value(status.url)'
    ```
