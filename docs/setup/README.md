# SmartLingua — Setup Guide

## Prerequisites

| Tool           | Minimum Version                    | Install                             |
| -------------- | ---------------------------------- | ----------------------------------- |
| Docker Desktop | 24.x                               | https://docs.docker.com/get-docker/ |
| Docker Compose | v2.x (bundled with Docker Desktop) | -                                   |
| Java JDK       | 21 (Eclipse Temurin)               | https://adoptium.net/               |
| Node.js        | 20 LTS                             | https://nodejs.org/                 |
| Maven          | 3.9+                               | https://maven.apache.org/           |
| Git            | 2.x                                | https://git-scm.com/                |

Optional for Kubernetes / Vagrant:

- VirtualBox 7.x + Vagrant 2.4+
- kubectl 1.30+
- Helm 3.x

---

## 1. Clone & Configure

```bash
git clone https://github.com/<your-org>/smartlingua.git
cd smartlingua
```

Copy the environment template (optional — only needed for AI service):

```bash
cp .env.example .env
# Edit .env and set OPENAI_API_KEY=sk-...
```

---

## 2. Start with Docker Compose (Recommended)

```bash
# Start the full stack (first run builds all images — ~10 min)
docker compose up -d --build

# Check all services are healthy
docker compose ps

# Tail logs for a specific service
docker compose logs -f users

# Stop everything
docker compose down

# Stop and remove all data volumes
docker compose down -v
```

### Service URLs (local)

| Service            | URL                                 |
| ------------------ | ----------------------------------- |
| Frontend (Angular) | http://localhost:4200               |
| API Gateway        | http://localhost:8093               |
| Eureka Dashboard   | http://localhost:8761               |
| Keycloak Admin     | http://localhost:8081 (admin/admin) |
| Prometheus         | http://localhost:9090               |
| Grafana            | http://localhost:3000 (admin/admin) |

---

## 3. Backend-Only Local Dev

Run infrastructure (MySQL + Keycloak + Eureka) via Docker and run services locally with Maven.

```bash
# Start infra only
docker compose up -d mysql keycloak eureka config-server

# In a separate terminal, start any microservice
cd backend/microservices/users
./mvnw spring-boot:run
```

---

## 4. Running Tests

### Backend (all microservices)

```bash
cd backend/microservices/users
./mvnw test

# With coverage report (JaCoCo)
./mvnw verify
# Report: target/site/jacoco/index.html
```

### Frontend (Angular)

```bash
cd frontend
npm install
npm test                  # Interactive mode
npm run test:ci           # Headless CI mode
```

---

## 5. Environment Variables

Key variables consumed by the backend services:

| Variable                               | Default                                   | Description             |
| -------------------------------------- | ----------------------------------------- | ----------------------- |
| `SPRING_DATASOURCE_URL`                | per-service                               | JDBC connection string  |
| `SPRING_DATASOURCE_PASSWORD`           | `root`                                    | MySQL password          |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | `http://eureka:8761/eureka`               | Eureka endpoint         |
| `SPRING_CLOUD_CONFIG_URI`              | `http://config-server:8890`               | Config Server           |
| `KEYCLOAK_ISSUER_URI`                  | `http://keycloak:8080/realms/smartlingua` | JWT issuer              |
| `OPENAI_API_KEY`                       | `change-me`                               | Required for AI service |

---

## 6. Troubleshooting

**Services fail to start (dependency issues)**  
The startup order is: `mysql → keycloak → eureka → config-server → microservices → api-gateway → frontend`  
If a service crashes, check `docker compose logs <service>` for the root cause.

**Port conflicts**  
If any port is already in use, stop the conflicting process or edit the host port in `docker-compose.yml`.

**Build fails out of memory**  
Increase Docker Desktop memory to at least 8 GB in Docker Desktop → Settings → Resources.
