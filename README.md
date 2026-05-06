# SmartLingua — DevOps Runbook

> **Academic Context** — GenZLeadres program · Esprit School of Engineering, Tunisia · 2025–2026

SmartLingua is an e-learning platform with adaptive learning paths, course catalog, quizzes, and secure access. It uses an Angular front-end, Spring Boot 4.x microservices behind an API Gateway, Keycloak authentication, MySQL, Prometheus/Grafana monitoring, Jenkins CI/CD, and Kubernetes deployment.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Port Reference](#2-port-reference)
3. [Vagrant — Start the VM](#3-vagrant--start-the-vm)
4. [Docker Compose — Run the Full Stack](#4-docker-compose--run-the-full-stack)
5. [Prometheus & Grafana — Monitoring](#5-prometheus--grafana--monitoring)
6. [Jenkins — CI/CD](#6-jenkins--cicd)
7. [SonarQube — Code Quality](#7-sonarqube--code-quality)
8. [Running Tests](#8-running-tests)
9. [GitHub Actions](#9-github-actions)
10. [Kubernetes (K8s)](#10-kubernetes-k8s)
11. [Useful Commands](#11-useful-commands)

---

## 1. Prerequisites

Install these on your **Windows host** before starting:

| Tool | Version | Download |
|---|---|---|
| VirtualBox | 7.x | https://www.virtualbox.org |
| Vagrant | 2.4+ | https://www.vagrantup.com |
| Git | latest | https://git-scm.com |

Everything else (Docker, Java, Maven, Node.js, kubectl, Helm) is pre-installed **inside the VM** by the Vagrantfile provisioner.

---

## 2. Port Reference

| Service | URL (Windows host browser) |
|---|---|
| Frontend (Angular) | http://localhost:4200 |
| API Gateway | http://localhost:8093 |
| Eureka Dashboard | http://localhost:8761 |
| Config Server | http://localhost:8890 |
| Keycloak Admin | http://localhost:8081 |
| Jenkins UI | http://localhost:3219 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |
| SonarQube | http://localhost:9000 |
| MySQL | localhost:3306 |
| Users service | http://localhost:8087 |
| Courses service | http://localhost:8086 |
| Quiz service | http://localhost:8088 |
| Exams service | http://localhost:8089 |
| Forum service | http://localhost:8096 |
| Messaging service | http://localhost:8092 |
| Privet Cours service | http://localhost:8091 |
| Adaptive Learning | http://localhost:8094 |
| AI Assistant | http://localhost:8095 |

---

## 3. Vagrant — Start the VM

Run all commands from the project root on **Windows** (Command Prompt or PowerShell).

```cmd
:: First time — downloads box and provisions (~10 min)
vagrant up

:: SSH into the VM
vagrant ssh

:: Restart VM (apply Vagrantfile changes)
vagrant reload

:: Stop VM
vagrant halt

:: Destroy VM completely
vagrant destroy -f
```

> **Note:** After `vagrant up` completes, all ports listed above are forwarded to `localhost` automatically.

---

## 4. Docker Compose — Run the Full Stack

All commands below run **inside the VM** (`vagrant ssh` first).

```bash
cd ~/DevopsProject

# Pull latest code
git pull origin main

# Start everything (first run builds all images — takes ~10 min)
docker compose up -d --build

# Start only infrastructure (DB, Keycloak, Eureka, Config)
docker compose up -d mysql keycloak eureka config-server

# Check container status
docker ps

# Tail logs for a specific service
docker compose logs -f users

# Restart a single service
docker compose restart api-gateway

# Stop everything
docker compose down

# Stop and wipe volumes (full reset)
docker compose down -v
```

### Startup order

The services start automatically in dependency order. Wait approximately:
- **30 s** — MySQL ready
- **60 s** — Keycloak ready (realm `smartlingua` auto-imported)
- **90 s** — Eureka + Config Server ready
- **120 s** — All microservices registered

### Default credentials

| Service | Username | Password |
|---|---|---|
| Keycloak admin console | `admin` | `admin` |
| MySQL root | `root` | `root` |
| App user (student) | `student` | `student123` |
| App user (teacher) | `teacher` | `teacher123` |
| App user (admin) | `admin` | `admin123` |

---

## 5. Prometheus & Grafana — Monitoring

### Prometheus

Open http://localhost:9090 — no login required.

- **Status → Targets** — verify all 12 microservice targets show `UP`
- **Graph** — query metrics, e.g. `jvm_memory_used_bytes`

Each microservice exposes `/actuator/prometheus`. Example:

```bash
curl http://localhost:8087/actuator/prometheus   # users service
curl http://localhost:8093/actuator/prometheus   # api-gateway
```

### Grafana

Open http://localhost:3000

- Default credentials: `admin` / `admin` (change on first login)
- Prometheus datasource is pre-provisioned automatically
- **To import a JVM dashboard:**
  1. Click **+** → **Import**
  2. Enter dashboard ID `4701` (JVM Micrometer)
  3. Select the `Prometheus` datasource → **Import**

---

## 6. Jenkins — CI/CD

### Access

Open http://localhost:3219 — no login required (setup wizard is disabled).

> If this is the first time after `vagrant up`, Jenkins may take ~60 s to initialize.

### Create pipeline jobs

1. **New Item** → name: `smartlingua-backend` → **Pipeline** → OK
2. Under **Pipeline**:
   - Definition: `Pipeline script from SCM`
   - SCM: `Git`
   - Repository URL: `https://github.com/AbbassiMouhamed/DevopsProject.git`
   - Branch: `*/main`
   - Script Path: `Jenkinsfile-backend`
3. **Save** → **Build Now**

Repeat for `smartlingua-frontend` using `Jenkinsfile-frontend`.

### Configure SonarQube in Jenkins

1. **Manage Jenkins → System → SonarQube servers**
2. **Add SonarQube**:
   - Name: `sonarqube`
   - Server URL: `http://sonarqube:9000`
   - Token: add as a Jenkins Secret Text credential (credentialId: `sonar-token`)
3. **Save**

> See [Section 7](#7-sonarqube--code-quality) for how to start SonarQube and generate a token.

### Pipeline stages

**Backend pipeline** (`Jenkinsfile-backend`):

| Stage | What it does |
|---|---|
| Test | Runs `mvn test` in parallel for all 12 microservices |
| Build | Runs `mvn clean package` in parallel for all 12 microservices |
| SonarQube Backend | Runs SonarScanner with JaCoCo XML coverage |
| Deploy Backend | SSH to server, `git pull`, `docker compose up -d` |

**Frontend pipeline** (`Jenkinsfile-frontend`):

| Stage | What it does |
|---|---|
| Install | `npm install` |
| Build | `npm run build` |
| Test | `npm test` with ChromeHeadless + code coverage |
| SonarQube Frontend | Runs SonarScanner with LCOV coverage |
| Deploy Frontend | SSH to server, `git pull`, `docker compose up -d` |

### Jenkins credentials required for deploy

Add these in **Manage Jenkins → Credentials**:

| ID | Type | Value |
|---|---|---|
| `backend-deploy-host` | Secret text | Target server IP/hostname |
| `backend-deploy-user` | Secret text | SSH username |
| `backend-deploy-ssh-key` | SSH private key | Private key for SSH access |
| `frontend-deploy-host` | Secret text | Target server IP/hostname |
| `frontend-deploy-user` | Secret text | SSH username |
| `frontend-deploy-ssh-key` | SSH private key | Private key for SSH access |

---

## 7. SonarQube — Code Quality

### Start SonarQube

SonarQube runs as a Docker container alongside the stack. Start it inside the VM:

```bash
cd ~/DevopsProject
docker compose up -d sonarqube

# Wait ~60s for it to initialize, then check:
docker compose logs -f sonarqube
# Ready when you see: "SonarQube is operational"
```

### Access & first login

Open **http://localhost:9000**

- Default credentials: `admin` / `admin`
- On first login SonarQube will ask you to change the password — set it to something you remember (e.g. `admin123`)

### Generate a token

1. Go to **My Account → Security → Generate Tokens**
2. Name: `smartlingua`, type: **Global Analysis Token**
3. Copy the token — you will need it for `SONAR_TOKEN`

### Run SonarScanner manually (inside the VM)

```bash
cd ~/DevopsProject

# Backend — run from repo root
SONAR_HOST_URL=http://sonarqube:9000 \
SONAR_TOKEN=<your-token> \
sonar-scanner \
  -Dsonar.projectKey=smartlingua-backend \
  -Dsonar.sources=backend \
  -Dsonar.exclusions=**/target/**,**/node_modules/** \
  -Dsonar.coverage.jacoco.xmlReportPaths=backend/**/target/site/jacoco/jacoco.xml

# Frontend
SONAR_HOST_URL=http://sonarqube:9000 \
SONAR_TOKEN=<your-token> \
sonar-scanner \
  -Dsonar.projectKey=smartlingua-frontend \
  -Dsonar.sources=frontend/src \
  -Dsonar.javascript.lcov.reportPaths=frontend/coverage/**/lcov.info \
  -Dsonar.typescript.tsconfigPath=frontend/tsconfig.json
```

> **Note:** Use `http://sonarqube:9000` when running **inside the Docker network** (VM shell / Jenkins pipeline). Use `http://localhost:9000` only from the **Windows host browser**.

### Using `sonar-project.properties` (repo root)

```bash
# Set env vars and run from repo root
export SONAR_HOST_URL=http://sonarqube:9000
export SONAR_TOKEN=<your-token>
sonar-scanner
```

### Configure SonarQube server in Jenkins

1. **Manage Jenkins → System → SonarQube servers → Add SonarQube**
2. Name: `sonarqube`, URL: `http://sonarqube:9000`
3. Authentication token: add a **Secret Text** credential with your token, ID = `sonar-token`
4. **Save**

---

## 8. Running Tests

### Backend — all microservices

Inside the VM:

```bash
cd ~/DevopsProject

# Run tests for a single service
cd backend/microservices/users
mvn test

# Run tests for all services in parallel (same as Jenkins)
for svc in backend/apiGateway backend/config-server backend/eureka \
           backend/microservices/users backend/microservices/courses \
           backend/microservices/quiz backend/microservices/exams \
           backend/microservices/forum backend/microservices/messaging \
           backend/microservices/privetcours backend/microservices/adaptive-learning \
           backend/microservices/ai-assistant-service; do
  echo "Testing $svc..."
  (cd ~/DevopsProject/$svc && mvn test -q) &
done
wait
echo "All tests done"

# Generate JaCoCo coverage report
mvn jacoco:report -f backend/microservices/users/pom.xml
# Report at: backend/microservices/users/target/site/jacoco/index.html
```

### Frontend

```bash
cd ~/DevopsProject/frontend

npm install

# Run tests once (CI mode)
npm test -- --watch=false --browsers=ChromeHeadless

# Run tests with coverage
npm test -- --watch=false --browsers=ChromeHeadless --code-coverage
# Coverage report at: frontend/coverage/index.html
```

---

## 9. GitHub Actions

Two workflows run automatically on every push/PR:

| Workflow | File | Trigger |
|---|---|---|
| SmartLingua CI | `.github/workflows/ci.yml` | Every push and PR |
| SmartLingua CI/CD Sprint 3 | `.github/workflows/ci-cd.yml` | Push to `main`, `develop`, `feature/**` |

Both workflows:
- Spin up MySQL 8.0 as a service container
- Test all 12 backend microservices in parallel (matrix strategy)
- Build each microservice with `mvn clean install`

### Viewing results

Go to your GitHub repository → **Actions** tab → click any workflow run to see per-service logs.

### Required GitHub Secrets (for CD steps)

Add these in **GitHub → Settings → Secrets and variables → Actions**:

| Secret | Description |
|---|---|
| `SONAR_TOKEN` | SonarQube/SonarCloud authentication token |
| `SONAR_HOST_URL` | SonarQube server URL |
| `DEPLOY_HOST` | SSH target server IP |
| `DEPLOY_USER` | SSH username |
| `DEPLOY_SSH_KEY` | SSH private key (PEM format) |

---

## 10. Kubernetes (K8s)

### Prerequisites

The Vagrant VM has `kubectl`, `kubeadm`, and `kubelet` pre-installed. Initialize the cluster first if not done:

```bash
# Inside the VM — only once
sudo kubeadm init --pod-network-cidr=10.244.0.0/16

mkdir -p $HOME/.kube
sudo cp /etc/kubernetes/admin.conf $HOME/.kube/config
sudo chown $(id -u):$(id -g) $HOME/.kube/config

# Install Flannel CNI
kubectl apply -f https://raw.githubusercontent.com/flannel-io/flannel/master/Documentation/kube-flannel.yml

# Allow scheduling on control plane (single-node setup)
kubectl taint nodes --all node-role.kubernetes.io/control-plane-
```

### Deploy SmartLingua to Kubernetes

```bash
cd ~/DevopsProject

# Deploy everything (namespace, configmap, all services)
kubectl apply -k k8s/

# Or deploy step by step
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap-env.yaml
kubectl apply -f k8s/mysql.yaml
kubectl apply -f k8s/keycloak.yaml
kubectl apply -f k8s/eureka.yaml
kubectl apply -f k8s/config-server.yaml
kubectl apply -f k8s/api-gateway.yaml
kubectl apply -f k8s/microservices.yaml
kubectl apply -f k8s/frontend.yaml
kubectl apply -f k8s/monitoring.yaml
```

### Check status

```bash
# All resources in the smartlingua namespace
kubectl get all -n smartlingua

# Watch pods come up
kubectl get pods -n smartlingua -w

# Check pod logs
kubectl logs -n smartlingua deployment/users -f

# Describe a failing pod
kubectl describe pod -n smartlingua <pod-name>
```

### Tear down

```bash
# Remove all SmartLingua resources
kubectl delete -k k8s/

# Or just the namespace (removes everything inside it)
kubectl delete namespace smartlingua
```

---

## 11. Useful Commands

### Docker

```bash
# Rebuild a single service image
docker compose build users

# Rebuild Jenkins (e.g. after plugins.txt change)
docker compose build jenkins
docker compose up -d jenkins

# View resource usage
docker stats

# Remove unused images
docker image prune -f
```

### Git (Windows PowerShell — push to remote)

```powershell
cd C:\Users\abbas\Desktop\DEVOPS\Esprit-GenZLeadres-4SAE11-2026-Smartlingua
git add .
git commit -m "your message"
git push devops HEAD:main
```

### Vagrant

```cmd
:: Check VM status
vagrant status

:: Re-run provisioner without rebuilding VM
vagrant provision

:: Update base box
vagrant box update
```

### Actuator health checks (inside VM or from host)

```bash
curl http://localhost:8087/actuator/health    # users
curl http://localhost:8093/actuator/health    # api-gateway
curl http://localhost:8761/actuator/health    # eureka
curl http://localhost:8890/actuator/health    # config-server
```
