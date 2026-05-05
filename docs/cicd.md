# SmartLingua — CI/CD & DevOps Toolchain

> **Project**: SmartLingua — Adaptive E-Learning Platform
> **Team**: GenZLeadres, 4SAE11 — Esprit School of Engineering, Academic Year 2025-2026

This document explains every DevOps tool used in SmartLingua, **why** it was chosen, and **how** it is configured.

---

## Table of Contents

1. [Jenkins — Continuous Integration & Delivery](#1-jenkins--continuous-integration--delivery)
2. [SonarQube — Code Quality & Security Analysis](#2-sonarqube--code-quality--security-analysis)
3. [Docker & Docker Compose — Containerisation](#3-docker--docker-compose--containerisation)
4. [Maven — Java Build Tool](#4-maven--java-build-tool)
5. [Spring Cloud — Microservice Infrastructure](#5-spring-cloud--microservice-infrastructure)
6. [Keycloak — Identity & Access Management](#6-keycloak--identity--access-management)
7. [Prometheus — Metrics Collection](#7-prometheus--metrics-collection)
8. [Grafana — Observability Dashboard](#8-grafana--observability-dashboard)
9. [Pipeline Flow Summary](#9-pipeline-flow-summary)

---

## 1. Jenkins — Continuous Integration & Delivery

### Why Jenkins?

Jenkins is the industry-standard open-source automation server. It was chosen because:

- **Declarative Pipelines** (Jenkinsfile as code) keep pipeline configuration version-controlled alongside the application source.
- **Parallel execution** support lets all 12 backend microservices build and test concurrently, reducing CI time significantly.
- **Plugin ecosystem** (JUnit, JaCoCo, SonarQube Scanner, SSH) covers every stage needed without custom tooling.
- **Self-hosted** — no vendor lock-in and no data leaves the team's infrastructure, which matters for PFE academic integrity.

### How It Works

Two separate pipelines cover the monorepo:

#### `Jenkinsfile-backend`

| Stage                 | What it does                                                                                                                     |
| --------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| **Test**              | Runs `./mvnw test` for all 12 services in parallel. Uses Maven Wrapper so no global Maven installation is required on the agent. |
| **Build**             | Runs `./mvnw clean package` for all 12 services in parallel. Archives JARs and JaCoCo XML reports.                               |
| **SonarQube Backend** | Runs `mvn sonar:sonar` targeting `$SONAR_PROJECT_KEY=smartlingua-backend`. Passes JaCoCo XML paths for coverage upload.          |
| **Deploy Backend**    | Triggered on `main` branch only. SSH into the deployment server, pulls latest code, runs `docker compose up -d`.                 |

#### `Jenkinsfile-frontend`

| Stage                  | What it does                                                                                                |
| ---------------------- | ----------------------------------------------------------------------------------------------------------- |
| **Install**            | Runs `npm install` inside `frontend/`.                                                                      |
| **Build**              | Runs `npm run build` (Angular production build).                                                            |
| **Test**               | Runs `npm test -- --watch=false --browsers=ChromeHeadless --code-coverage`. Archives LCOV coverage reports. |
| **SonarQube Frontend** | Runs `sonar-scanner` with TypeScript/LCOV settings.                                                         |
| **Deploy Frontend**    | Triggered on `main` branch only. SSH deploy with Docker Compose.                                            |

#### Jenkins Credentials Required

| Credential ID             | Type            | Used by               |
| ------------------------- | --------------- | --------------------- |
| `backend-deploy-host`     | Secret text     | Deploy Backend stage  |
| `backend-deploy-user`     | Secret text     | Deploy Backend stage  |
| `backend-deploy-ssh-key`  | SSH Private Key | Deploy Backend stage  |
| `frontend-deploy-host`    | Secret text     | Deploy Frontend stage |
| `frontend-deploy-user`    | Secret text     | Deploy Frontend stage |
| `frontend-deploy-ssh-key` | SSH Private Key | Deploy Frontend stage |

#### Environment Variables (Agent)

| Variable         | Purpose                                                                   |
| ---------------- | ------------------------------------------------------------------------- |
| `SONAR_HOST_URL` | SonarQube server URL                                                      |
| `SONAR_TOKEN`    | Authentication token for SonarQube                                        |
| `CHROME_BIN`     | Path to Chrome for headless Angular tests (e.g. `/usr/bin/google-chrome`) |

---

## 2. SonarQube — Code Quality & Security Analysis

### Why SonarQube?

- **Static analysis at scale** — scans all 12 microservices and the Angular frontend in a single pass.
- **OWASP/CWE vulnerability detection** — catches common security issues (SQL injection, XSS, hardcoded secrets) before they reach production.
- **Coverage gates** — integrates with JaCoCo (Java) and LCOV (Angular) so coverage regressions break the build.
- **Technical debt tracking** — gives supervisors and jury members a quantified view of code quality.

### How It Works

Configuration is split across two files:

#### `sonar-project.properties` (repo root)

```properties
sonar.projectKey=smartlingua
sonar.sources=backend, frontend/src
sonar.tests=backend, frontend/src
sonar.test.inclusions=**/*Test*.java, **/*.spec.ts
sonar.exclusions=**/target/**, **/node_modules/**, **/dist/**
sonar.coverage.jacoco.xmlReportPaths=backend/**/target/site/jacoco/jacoco.xml
sonar.javascript.lcov.reportPaths=frontend/coverage/**/lcov.info
```

#### Per-service scan (CI)

Each backend stage in `Jenkinsfile-backend` passes:

```sh
-Dsonar.projectKey=smartlingua-backend
-Dsonar.coverage.jacoco.xmlReportPaths=backend/**/target/site/jacoco/jacoco.xml
```

Frontend stage passes:

```sh
-Dsonar.projectKey=smartlingua-frontend
-Dsonar.javascript.lcov.reportPaths=frontend/coverage/**/lcov.info
```

#### Manual scan (PowerShell script)

```powershell
# Set env vars first
$env:SONAR_HOST_URL = "http://localhost:9000"
$env:SONAR_TOKEN    = "squ_..."

# Run scan for all backend services
.\backend\scripts\run-sonar.ps1
```

---

## 3. Docker & Docker Compose — Containerisation

### Why Docker?

- **Environment parity** — Keycloak and MySQL run identically on every developer's machine and on the CI server.
- **Isolation** — services do not pollute the host OS; easily torn down and recreated.
- **Docker Compose** provides single-command orchestration for the infrastructure and monitoring stacks.

### How It Works

#### `backend/docker-compose.yml` — Infrastructure stack

Starts two services required by all microservices:

| Service    | Image                            | Port   | Purpose                               |
| ---------- | -------------------------------- | ------ | ------------------------------------- |
| `mysql`    | `mysql:8.0`                      | `3306` | Shared database for all microservices |
| `keycloak` | `quay.io/keycloak/keycloak:25.0` | `8081` | IAM — issues JWT tokens               |

```bash
# Start infrastructure (run first)
docker compose -f backend/docker-compose.yml up -d
```

#### `docker-compose-monitoring.yml` — Observability stack

| Service      | Image                     | Port   | Purpose                  |
| ------------ | ------------------------- | ------ | ------------------------ |
| `prometheus` | `prom/prometheus:v2.53.0` | `9090` | Metrics scraping         |
| `grafana`    | `grafana/grafana:11.1.0`  | `3000` | Dashboards (admin/admin) |

```bash
# Start monitoring (run after services are up)
docker compose -f docker-compose-monitoring.yml up -d
```

---

## 4. Maven — Java Build Tool

### Why Maven?

- **Convention over configuration** — standard directory layout, lifecycle, and plugin system means every developer and CI agent can build without project-specific instructions.
- **Maven Wrapper (`mvnw`)** — each microservice ships with its own wrapper (Maven 3.9.12), ensuring reproducible builds regardless of the agent's installed Maven version.
- **JaCoCo plugin** — integrated in every `pom.xml` for automatic coverage report generation during `verify` phase.

### How It Works

Every microservice directory contains:

```
pom.xml          ← Spring Boot 4.0.2 parent, Java 21, dependencies
mvnw / mvnw.cmd  ← Maven Wrapper (3.9.12)
```

Key Maven properties set in each `pom.xml`:

```xml
<java.version>21</java.version>
<maven.compiler.source>21</maven.compiler.source>
<maven.compiler.target>21</maven.compiler.target>
```

Common commands:

```bash
# From any microservice directory
./mvnw clean test           # Run unit tests
./mvnw clean package        # Build executable JAR
./mvnw clean verify         # Build + JaCoCo coverage report
./mvnw spring-boot:run      # Run locally
```

---

## 5. Spring Cloud — Microservice Infrastructure

### Why Spring Cloud?

Spring Cloud provides production-ready microservice patterns out of the box — service discovery, centralised configuration, and API routing — so the team can focus on business logic rather than plumbing.

### Components Used

#### Config Server (`backend/config-server`, port 8890)

- **Why**: Centralises application configuration. Changes to shared settings (database URLs, Keycloak realm, CORS origins) are applied to all services without redeployment.
- **How**: Uses the `native` profile — reads `.properties` files from the classpath. All services import config via `spring.config.import=optional:configserver:http://localhost:8890`.

#### Eureka Discovery Server (`backend/eureka`, port 8761)

- **Why**: Enables service-to-service communication by name (e.g. `lb://users`) instead of hardcoded IP addresses. Essential for horizontal scaling.
- **How**: Each microservice registers at startup: `eureka.client.service-url.defaultZone=http://localhost:8761/eureka`. The API Gateway resolves routes to live instances automatically.

#### API Gateway (`backend/apiGateway`, port 8093)

- **Why**: Single entry point for all client requests. Handles cross-cutting concerns: JWT validation, CORS, rate limiting, and routing. The Angular frontend communicates exclusively through this gateway.
- **How**: Built on Spring Cloud Gateway (WebFlux/reactive). Routes defined in `application.properties`:

| Route prefix               | Upstream service            |
| -------------------------- | --------------------------- |
| `/courses/**`              | `lb://courses`              |
| `/api/users/**`            | `lb://users`                |
| `/quiz/**`                 | `lb://quiz`                 |
| `/exams/**`                | `lb://exams`                |
| `/forum/**`                | `lb://forum`                |
| `/privetcours/**`          | `lb://privetcours`          |
| `/messaging/**`            | `lb://messaging`            |
| `/api/adaptive/**`         | `lb://adaptive-learning`    |
| `/ai-assistant-service/**` | `lb://ai-assistant-service` |

---

## 6. Keycloak — Identity & Access Management

### Why Keycloak?

- **Industry-standard OAuth2/OIDC** provider — no custom auth code to maintain or audit.
- **Role-based access control (RBAC)** — realm roles (`ROLE_STUDENT`, `ROLE_TEACHER`, `ROLE_ADMIN`) are embedded in JWT tokens and enforced by Spring Security in every service.
- **Angular integration** — `keycloak-angular` library handles silent SSO, token refresh, and bearer header injection transparently.

### How It Works

#### Server

Keycloak runs in Docker on port `8081`. The realm `smartlingua` is pre-configured via `keycloak/realm-smartlingua.json`.

```bash
# Keycloak starts with the infrastructure stack
docker compose -f backend/docker-compose.yml up -d
# Admin console: http://localhost:8081/admin  (admin/admin)
```

#### Backend (Spring Security)

Each microservice has an OAuth2 Resource Server configuration:

```java
// Validates JWT against Keycloak's public keys
spring.security.oauth2.resourceserver.jwt.issuer-uri=
    http://localhost:8081/realms/smartlingua
```

The `JwtRoleConverter` class (in `users` service) extracts roles from `realm_access.roles` and `resource_access.<client>.roles` claims and maps them to Spring `GrantedAuthority` objects.

#### Frontend (Angular)

```typescript
// frontend/src/app/core/keycloak.config.ts
export const keycloakConnectConfig = {
  url: "http://localhost:8081",
  realm: "smartlingua",
  clientId: "angular", // Public client, no secret
};
```

Guards enforce role-based routing:

| Guard          | Allowed roles          | Protected area |
| -------------- | ---------------------- | -------------- |
| `authGuard`    | Any authenticated user | `/dashboard`   |
| `studentGuard` | `ROLE_STUDENT`         | `/student/**`  |
| `teacherGuard` | `ROLE_TEACHER`         | `/teacher/**`  |
| `adminGuard`   | `ROLE_ADMIN`           | `/admin/**`    |

---

## 7. Prometheus — Metrics Collection

### Why Prometheus?

- **Pull-based scraping** — Prometheus periodically calls `/actuator/prometheus` on each service; no agent to deploy on every host.
- **Spring Boot Actuator integration** — all Spring Boot services expose Micrometer metrics automatically with a single dependency.
- **PromQL** — powerful query language consumed directly by Grafana for dashboards and alerts.

### How It Works

All Spring Boot services expose metrics via Micrometer:

```properties
# In every application.properties
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.prometheus.metrics.export.enabled=true
management.metrics.tags.application=${spring.application.name}
```

Prometheus scrape targets are defined in `monitoring/prometheus.yml`. Scrape interval is 15 seconds.

| Service              | Scrape target                                   |
| -------------------- | ----------------------------------------------- |
| config-server        | `host.docker.internal:8890/actuator/prometheus` |
| eureka               | `host.docker.internal:8761/actuator/prometheus` |
| apigateway           | `host.docker.internal:8093/actuator/prometheus` |
| users                | `host.docker.internal:8087/actuator/prometheus` |
| courses              | `host.docker.internal:8086/actuator/prometheus` |
| quiz                 | `host.docker.internal:8088/actuator/prometheus` |
| exams                | `host.docker.internal:8089/actuator/prometheus` |
| messaging            | `host.docker.internal:8092/actuator/prometheus` |
| privetcours          | `host.docker.internal:8091/actuator/prometheus` |
| adaptive-learning    | `host.docker.internal:8094/actuator/prometheus` |
| ai-assistant-service | `host.docker.internal:8095/actuator/prometheus` |
| forum                | `host.docker.internal:8096/actuator/prometheus` |

> `host.docker.internal` resolves to the host machine from within the Prometheus Docker container, enabling scraping of services running directly on the host (non-containerised dev mode).

---

## 8. Grafana — Observability Dashboard

### Why Grafana?

- **Unified visualisation** — connects to Prometheus data source and renders live dashboards with minimal configuration.
- **Pre-built Spring Boot dashboards** — import dashboard ID `4701` (JVM Micrometer) from Grafana.com to get instant heap, GC, thread, and HTTP metrics for all services.
- **Alerting** — can send notifications to Slack, email, or webhook when metrics cross thresholds.

### How It Works

Grafana runs on port `3000` (default credentials: `admin/admin` — change on first login).

```bash
# Access dashboard
http://localhost:3000

# Add Prometheus data source
Settings → Data Sources → Add → Prometheus → URL: http://prometheus:9090
```

#### Recommended Dashboards

| Dashboard              | Grafana ID | Covers                              |
| ---------------------- | ---------- | ----------------------------------- |
| JVM (Micrometer)       | 4701       | Heap, GC, threads, HTTP per service |
| Spring Boot Statistics | 6756       | Request rate, error rate, latency   |

---

## 9. Pipeline Flow Summary

```
Developer pushes code
        │
        ▼
  Jenkins detects change
        │
   ┌────┴────┐
   │ Backend │                    │ Frontend │
   └────┬────┘                    └─────┬────┘
        │                               │
  Parallel Test (12 services)    npm install
        │                               │
  Parallel Build (12 services)   ng build (prod)
        │                               │
  JUnit/JaCoCo reports           Karma + ChromeHeadless
        │                               │
  SonarQube Backend               SonarQube Frontend
        │                               │
  [branch=main?]                  [branch=main?]
        │ yes                           │ yes
  SSH → docker compose up -d      SSH → deploy
        │
        ▼
  Prometheus scrapes /actuator/prometheus every 15s
        │
        ▼
  Grafana dashboards show live service health
```

---

_Generated as part of the SmartLingua PFE DevOps documentation — Esprit School of Engineering, 2025-2026._
