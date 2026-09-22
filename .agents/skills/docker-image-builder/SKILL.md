---
name: docker-image-builder
description: Automates compilation of the React frontend, packages the Spring Boot backend, and builds a secure, minimal-size Java 25 Docker image.
---

# Skill: Docker Image Builder

## ℹ️ Objective
Package the frontend and backend into a single-unit production-ready Docker image while ensuring the image size is as small as possible and strictly complies with modern security protocols (running as a non-root user).

---

## 🛠️ Docker Build Rules

### 1. Build Process Orchestration
To prevent bloat in the Docker build context (which occurs if we build frontend/backend entirely inside standard multi-stage Docker containers with thick node_modules/Maven caches), we use a **Local Pre-build & Copy** pattern. The orchestration is as follows:
1. Navigate to `engineers/03-implementations/frontend/` and run:
   ```bash
   npm run build
   ```
2. Clean/create the backend static resource directory `engineers/03-implementations/backend/src/main/resources/static/` and copy the contents of `engineers/03-implementations/frontend/dist/` into it.
3. Navigate to `engineers/03-implementations/backend/` and execute Maven packaging:
   ```bash
   mvn clean package -DskipTests
   ```
4. Copy the packaged JAR file (e.g. `score-assistant-backend-*.jar`) to the designated DevOps workspace `engineers/03-implementations/devops/`.
5. Trigger Docker build inside the DevOps folder using the highly optimized, lightweight Dockerfile.
6. Delete the temporary JAR from `devops/` immediately after building the image to keep the workspace clean (meaning no `.gitignore` is required).

### 2. Dockerfile Configuration (Minimal Size & Security)
The `Dockerfile` MUST be located in `engineers/03-implementations/devops/Dockerfile` and follow this structure:

```dockerfile
# 1. Base Runtime Image - Use alpine-based JRE for smallest footprint
FROM eclipse-temurin:25-jre-alpine

# 2. Security - Create a dedicated non-root user/group to prevent container escape
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# 3. Code Injection - Only copy the pre-built single JAR to keep context size < 100MB
COPY *.jar app.jar

# 4. Ownership & Permissions
RUN chown -R appuser:appgroup /app

# 5. Least Privilege Execution - Switch away from root
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 3. Build Script Automation (`build.sh`)
Create `.agents/skills/docker-image-builder/scripts/build.sh` to encapsulate the entire sequence. The script must be executable (`chmod +x build.sh`) and handle error conditions gracefully (e.g. failing fast if npm build or maven compilation fails).

---

## ⚠️ Output Requirements & Verification
- **Output Files**:
  - `engineers/03-implementations/devops/Dockerfile`
  - `.agents/skills/docker-image-builder/scripts/build.sh`
- **Security Check**:
  - The container must run as a non-root user.
  - No secret tokens, credentials, or private keys should be embedded in the Dockerfile or scripts.
- **Verification**:
  - Verify that the image can be successfully built via `docker build`.
  - The final image size must be checked (typically less than 180MB).
