#!/usr/bin/env bash
# ==============================================================================
# Cloud Run Deployer: Tag → Push → Deploy to GCP Cloud Run
# Prerequisite: a local Docker image matching the workspace folder name must exist.
# ==============================================================================
set -euo pipefail

# ---------------------------------------------------------------------------
# 1. Resolve GCP Configuration from active gcloud context
# ---------------------------------------------------------------------------
echo "========================================================================"
echo "☁️  Cloud Run Deployer: Resolving GCP Configuration"
echo "========================================================================"

PROJECT_ID=$(gcloud config get-value project 2>/dev/null)
REGION=$(gcloud config get-value run/region 2>/dev/null)

# Fallback: try compute/region if run/region is unset
if [ -z "${REGION}" ]; then
  REGION=$(gcloud config get-value compute/region 2>/dev/null)
fi

# Abort if either value is still unset
if [ -z "${PROJECT_ID}" ]; then
  echo "❌ Error: GCP project is not configured."
  echo "   Run: gcloud config set project <YOUR_PROJECT_ID>"
  exit 1
fi
if [ -z "${REGION}" ]; then
  echo "❌ Error: Cloud Run region is not configured."
  echo "   Run: gcloud config set run/region <YOUR_REGION>  (e.g. asia-east1)"
  exit 1
fi

# ---------------------------------------------------------------------------
# 2. Derive Registry Coordinates and Service Name
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"

REPOSITORY="${ARTIFACT_REPOSITORY:-docker}"
IMAGE_NAME=$(basename "${WORKSPACE_ROOT}" | tr '[:upper:]' '[:lower:]' | tr ' ' '-')
SERVICE_NAME="${IMAGE_NAME}"

# Find the newest timestamped tag from the local docker daemon by sorting reverse-lexicographically
TAG=$(docker images "${IMAGE_NAME}" --format '{{.Tag}}' 2>/dev/null | grep -E '^[0-9]{8}-[0-9]{6}' | sort -r | head -n 1 || echo "")

if [ -z "${TAG}" ]; then
  echo "❌ Error: No timestamped image found for '${IMAGE_NAME}' in local docker daemon."
  echo "   Please run the build script first to compile and tag a new version:"
  echo "   ./.agents/skills/docker-image-builder/scripts/build.sh"
  exit 1
fi

LOCAL_IMAGE="${IMAGE_NAME}:${TAG}"
REGISTRY_URL="${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPOSITORY}/${IMAGE_NAME}:${TAG}"

echo "   GCP Project  : ${PROJECT_ID}"
echo "   Region       : ${REGION}"
echo "   Active Tag   : ${TAG}"
echo "   Registry URL : ${REGISTRY_URL}"
echo "========================================================================"

# ---------------------------------------------------------------------------
# 3. Validate prerequisite local image
# ---------------------------------------------------------------------------
echo ""
echo "--- 🔍 Step 1: Verifying Local Docker Image ---"
if ! docker image inspect "${LOCAL_IMAGE}" > /dev/null 2>&1; then
  echo "❌ Error: Local image '${LOCAL_IMAGE}' not found."
  echo "   Build it first by running the docker-image-builder skill:"
  echo "   .agents/skills/docker-image-builder/scripts/build.sh"
  exit 1
fi
echo "✅ Local image '${LOCAL_IMAGE}' confirmed."

# ---------------------------------------------------------------------------
# 4. Authenticate Docker with Artifact Registry (one-time, skip if already set)
# ---------------------------------------------------------------------------
# NOTE: docker-credential-gcloud list only shows gcr.io endpoints, NOT pkg.dev.
# The Artifact Registry credHelper is stored in ~/.docker/config.json under
# the 'credHelpers' key — that is the authoritative source to check.
REGISTRY_HOST="${REGION}-docker.pkg.dev"
CREDHELPER=$(grep -F "${REGISTRY_HOST}" "${HOME}/.docker/config.json" 2>/dev/null || true)

if [ -z "${CREDHELPER}" ]; then
  echo ""
  echo "--- 🔑 Step 2: Configuring Artifact Registry credential helper ---"
  gcloud auth configure-docker "${REGISTRY_HOST}" --quiet
  echo "✅ Credential helper configured."
else
  echo ""
  echo "--- 🔑 Step 2: Artifact Registry credential helper already set — skipping."
fi

# ---------------------------------------------------------------------------
# 5. Tag Image for Artifact Registry
# ---------------------------------------------------------------------------
echo ""
echo "--- 🏷️  Step 3: Tagging Image for GCP Registry ---"
echo "Tagging '${LOCAL_IMAGE}' → '${REGISTRY_URL}'..."
docker tag "${LOCAL_IMAGE}" "${REGISTRY_URL}"
echo "✅ Image successfully tagged."

# ---------------------------------------------------------------------------
# 6. Push Image to Artifact Registry
# ---------------------------------------------------------------------------
echo ""
echo "--- 🚚 Step 4: Pushing Image to Artifact Registry ---"
docker push "${REGISTRY_URL}"
echo "✅ Image successfully pushed."

# ---------------------------------------------------------------------------
# 7. Update Cloud Run image only (surgical swap — no config override)
# ---------------------------------------------------------------------------
echo ""
echo "--- 🚀 Step 5: Updating Cloud Run Service Image ---"
echo "Swapping image on service '${SERVICE_NAME}' in '${REGION}'..."
# 'services update --image' ONLY replaces the container image.
# All existing env vars, volume mounts, IAM bindings, scaling, and
# traffic splits are preserved untouched.
gcloud run services update "${SERVICE_NAME}" \
  --image="${REGISTRY_URL}" \
  --region="${REGION}" \
  --project="${PROJECT_ID}" \
  --update-env-vars=APP_VERSION="${TAG}" \
  --quiet

# ---------------------------------------------------------------------------
# 8. Print live service URL
# ---------------------------------------------------------------------------
SERVICE_URL=$(gcloud run services describe "${SERVICE_NAME}" \
  --region="${REGION}" \
  --project="${PROJECT_ID}" \
  --format='value(status.url)' 2>/dev/null || echo "(URL unavailable)")

echo ""
echo "========================================================================"
echo "🎉 Cloud Run Deployment Succeeded!"
echo "   Service URL: ${SERVICE_URL}"
echo "========================================================================"
