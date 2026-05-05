# SmartLingua — CI/CD Pipelines

## Overview

Two GitHub Actions workflows power the CI/CD pipeline:

| Workflow       | File                          | Trigger                        | Purpose                            |
| -------------- | ----------------------------- | ------------------------------ | ---------------------------------- |
| CI             | `.github/workflows/ci.yml`    | Push / PR to `main`, `develop` | Build + test all services          |
| CI/CD Sprint 3 | `.github/workflows/ci-cd.yml` | Push to `main`                 | Full pipeline: CI + SonarQube + CD |

---

## CI Workflow (`ci.yml`)

### Jobs

1. **`backend-ci`** — Matrix job runs for every microservice in parallel:
   - Spins up MySQL 8 service container
   - Sets up Java 21 (Eclipse Temurin)
   - Runs `./mvnw verify` to compile, test, and generate JaCoCo coverage
   - Uploads `target/surefire-reports/` and `target/jacoco.exec` as artifacts

2. **`frontend-ci`** — Angular build and test:
   - Sets up Node.js 20
   - Runs `npm ci` → `npm run build:ci` → `npm run test:ci`

### Matrix Services

```yaml
matrix:
  service:
    - backend/config-server
    - backend/eureka
    - backend/apiGateway
    - backend/microservices/users
    - backend/microservices/courses
    - backend/microservices/quiz
    - backend/microservices/exams
    - backend/microservices/forum
    - backend/microservices/messaging
    - backend/microservices/privetcours
    - backend/microservices/adaptive-learning
    - backend/microservices/ai-assistant-service
```

---

## Full CI/CD Workflow (`ci-cd.yml`)

### Jobs (in order)

```
backend-ci ──┬──► sonar-backend  (if SONAR secrets defined)
             │
frontend-ci ─┘
             │
             └──► cd-simulated  (only on main, after all CI passes)
```

### `sonar-backend` Job

Requires these GitHub Secrets:

- `SONAR_TOKEN` — SonarQube user token
- `SONAR_HOST_URL` — your SonarQube server URL (e.g. `http://sonarqube:9000`)

Steps:

1. `mvn clean verify` — build + tests + JaCoCo coverage
2. `mvn sonar:sonar` — publish analysis to SonarQube

### `cd-simulated` Job

Simulates a production deployment (actual deployment commands can be added):

- Echoes deployment success
- Can be replaced with `ssh` + `docker compose pull && docker compose up -d` commands

---

## Adding Docker Build & Push (Optional)

To push images to GitHub Container Registry (GHCR), add this to `ci-cd.yml`:

```yaml
- name: Log in to GHCR
  uses: docker/login-action@v3
  with:
    registry: ghcr.io
    username: ${{ github.actor }}
    password: ${{ secrets.GITHUB_TOKEN }}

- name: Build and push
  uses: docker/build-push-action@v5
  with:
    context: ./backend/microservices/users
    push: true
    tags: ghcr.io/${{ github.repository }}/users:${{ github.sha }}
```

---

## Jenkins Pipelines

Legacy Jenkinsfiles are also present for teams using Jenkins:

| File                   | Purpose                           |
| ---------------------- | --------------------------------- |
| `Jenkinsfile-backend`  | Build + test all backend services |
| `Jenkinsfile-frontend` | Build Angular app                 |

These are standalone and independent of GitHub Actions.

---

## Required GitHub Secrets

| Secret            | Required For               | Value                        |
| ----------------- | -------------------------- | ---------------------------- |
| `SONAR_TOKEN`     | SonarQube analysis         | SonarQube user token         |
| `SONAR_HOST_URL`  | SonarQube analysis         | `http://your-sonarqube:9000` |
| `DOCKER_USERNAME` | (optional) Docker Hub push | Docker Hub username          |
| `DOCKER_PASSWORD` | (optional) Docker Hub push | Docker Hub password          |
