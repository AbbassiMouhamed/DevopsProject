# SmartLingua — Kubernetes: Full Deployment & Orchestration Guide

This document covers everything from zero to a running cluster, plus how to
**demonstrate the orchestration capabilities** (scaling, self-healing, rolling
updates, service discovery, monitoring) for a presentation or review.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Build Images](#2-build-images)
3. [Pre-flight: Keycloak Realm ConfigMap](#3-pre-flight-keycloak-realm-configmap)
4. [Deploy the Full Stack](#4-deploy-the-full-stack)
5. [Verify Everything Is Running](#5-verify-everything-is-running)
6. [Accessing Services](#6-accessing-services)
7. [Showcase Orchestration — Demo Scripts](#7-showcase-orchestration--demo-scripts)
   - 7.1 Self-healing (automatic pod restart)
   - 7.2 Horizontal scaling
   - 7.3 Zero-downtime rolling update
   - 7.4 Service discovery via Eureka
   - 7.5 Observability with Prometheus + Grafana
8. [Updating Secrets](#8-updating-secrets)
9. [Useful Day-2 Commands](#9-useful-day-2-commands)
10. [Teardown](#10-teardown)
11. [Architecture Overview](#11-architecture-overview)

---

## 1. Prerequisites

| Tool                 | Min version | Notes                      |
| -------------------- | ----------- | -------------------------- |
| `kubectl`            | 1.30+       | `kubectl version --client` |
| `kustomize`          | built-in    | Bundled with kubectl 1.14+ |
| Docker               | 24+         | Must be running            |
| Minikube **or** kind | latest      | For local clusters         |

```bash
# Verify your kubectl is connected
kubectl cluster-info

# Check available nodes
kubectl get nodes
```

For local testing: [Minikube](https://minikube.sigs.k8s.io/) or [kind](https://kind.sigs.k8s.io/)

---

## 2. Build Images

All images must be available inside the cluster's Docker daemon **before** you apply
the manifests. With Minikube this is a one-liner:

```bash
# Point your shell's Docker CLI at Minikube's daemon
eval $(minikube docker-env)          # Linux/macOS
# Windows PowerShell:
# & minikube -p minikube docker-env --shell powershell | Invoke-Expression

# Build every service image from the repo root
docker compose build

# Confirm the images are present
docker images | grep smartlingua
```

> **Why this matters**: All microservice Deployments use `imagePullPolicy: IfNotPresent`.
> If the image is not already in the node's cache and no registry is configured,
> the pod will stay in `ErrImagePull` forever.

---

## 3. Pre-flight: Keycloak Realm ConfigMap

> **This step must run before `kubectl apply -k k8s/`** or Keycloak will start
> without the `smartlingua` realm and all OAuth flows will break.

```bash
# Create the ConfigMap from the realm JSON checked into the repo
kubectl create namespace smartlingua --dry-run=client -o yaml | kubectl apply -f -

kubectl create configmap keycloak-realm \
  --from-file=smartlingua-realm.json=keycloak/smartlingua-realm.json \
  -n smartlingua \
  --dry-run=client -o yaml | kubectl apply -f -

# Verify
kubectl get configmap keycloak-realm -n smartlingua
```

The Keycloak Deployment mounts this ConfigMap at
`/opt/keycloak/data/import/` and starts with `--import-realm`.

---

## 4. Deploy the Full Stack

```bash
# Apply everything via kustomize (recommended)
kubectl apply -k k8s/

# Watch all pods reach Running state — Ctrl-C when done
kubectl get pods -n smartlingua -w
```

### Deploy in dependency order (manual, for debugging)

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap-env.yaml     # env + secrets first
kubectl apply -f k8s/mysql.yaml             # DB before Spring apps
kubectl apply -f k8s/keycloak.yaml          # auth before gateway
kubectl apply -f k8s/eureka.yaml            # service registry
kubectl apply -f k8s/config-server.yaml     # config before microservices
kubectl apply -f k8s/api-gateway.yaml
kubectl apply -f k8s/microservices.yaml     # all 9 microservices
kubectl apply -f k8s/frontend.yaml
kubectl apply -f k8s/monitoring.yaml        # Prometheus + Grafana
```

---

## 5. Verify Everything Is Running

```bash
# All pods should show STATUS=Running, READY=1/1
kubectl get pods -n smartlingua

# Expected output (abbreviated):
# NAME                              READY   STATUS    RESTARTS
# api-gateway-xxx                   1/1     Running   0
# config-server-xxx                 1/1     Running   0
# eureka-xxx                        1/1     Running   0
# frontend-xxx (×2)                 1/1     Running   0
# grafana-xxx                       1/1     Running   0
# keycloak-xxx                      1/1     Running   0
# messaging-xxx                     1/1     Running   0
# mysql-xxx                         1/1     Running   0
# prometheus-xxx                    1/1     Running   0
# users-xxx                         1/1     Running   0
# ... (9 microservices total)

# Check Services
kubectl get svc -n smartlingua

# Deep-dive a specific pod
kubectl describe pod -l app=users -n smartlingua

# Tail logs
kubectl logs -l app=users -n smartlingua -f

# Recent events (first place to look on failures)
kubectl get events -n smartlingua --sort-by=.lastTimestamp | tail -20
```

---

## 6. Accessing Services

### Via NodePort (Minikube)

```bash
MINIKUBE_IP=$(minikube ip)
```

| Service     | NodePort | URL                         |
| ----------- | -------- | --------------------------- |
| Frontend    | 30080    | `http://$MINIKUBE_IP:30080` |
| API Gateway | 30093    | `http://$MINIKUBE_IP:30093` |
| Prometheus  | 30090    | `http://$MINIKUBE_IP:30090` |
| Grafana     | 30300    | `http://$MINIKUBE_IP:30300` |

### Via Ingress (requires nginx-ingress controller)

```bash
minikube addons enable ingress

# Add to hosts file (Windows: C:\Windows\System32\drivers\etc\hosts)
echo "$(minikube ip)  smartlingua.local" | sudo tee -a /etc/hosts
# Browser: http://smartlingua.local
```

### Via Port-Forward (for debugging individual services)

```bash
kubectl port-forward svc/frontend     4200:80   -n smartlingua &
kubectl port-forward svc/api-gateway  8093:8093 -n smartlingua &
kubectl port-forward svc/keycloak     8080:8080 -n smartlingua &
kubectl port-forward svc/eureka       8761:8761 -n smartlingua &
kubectl port-forward svc/grafana      3000:3000 -n smartlingua &
kubectl port-forward svc/prometheus   9090:9090 -n smartlingua &
```

---

## 7. Showcase Orchestration — Demo Scripts

The following commands produce **visible, real-time proof** of Kubernetes
orchestration. Run them during a demo to show each capability.

---

### 7.1 Self-healing (automatic pod restart)

Kubernetes restarts any pod that dies. Delete one to prove it:

```bash
# Terminal 1 — watch in real time
kubectl get pods -n smartlingua -w

# Terminal 2 — kill the users pod
kubectl delete pod -l app=users -n smartlingua

# Expected: the old pod shows Terminating, a new one appears and reaches 1/1 Running
# within ~30 seconds (restartPolicy: Always is the default)
```

To prove crash recovery, exec into a pod and kill the process:

```bash
kubectl exec -it $(kubectl get pod -l app=users -n smartlingua -o name | head -1) \
  -n smartlingua -- kill 1
# Kubernetes detects the container exited, restarts it automatically
kubectl get pods -n smartlingua   # RESTARTS counter increments
```

---

### 7.2 Horizontal Scaling

Scale the `users` service to 3 replicas and observe load distribution:

```bash
# Scale up
kubectl scale deployment users --replicas=3 -n smartlingua

# Watch three pods start
kubectl get pods -l app=users -n smartlingua -w

# Verify the Service endpoints (kube-proxy load-balances across all 3)
kubectl get endpoints users -n smartlingua

# Scale back down
kubectl scale deployment users --replicas=1 -n smartlingua
```

---

### 7.3 Zero-downtime Rolling Update

Simulate a new image release and watch the rolling update happen with zero downtime:

```bash
# Tag the current image as v2
docker tag smartlingua/users:latest smartlingua/users:v2

# Patch the deployment with the new tag
kubectl set image deployment/users users=smartlingua/users:v2 -n smartlingua

# Watch the rolling update: old pods terminate one-by-one AFTER new ones are Ready
kubectl rollout status deployment/users -n smartlingua

# View the rollout history
kubectl rollout history deployment/users -n smartlingua

# Rollback if needed
kubectl rollout undo deployment/users -n smartlingua
```

---

### 7.4 Service Discovery via Eureka

All microservices register themselves with Eureka on startup. Open the dashboard
to see every service listed:

```bash
kubectl port-forward svc/eureka 8761:8761 -n smartlingua
# Browser: http://localhost:8761
```

You should see entries like `USERS`, `COURSES`, `MESSAGING`, `API-GATEWAY`, etc.,
all with status `UP`. This proves:

- Each pod self-registered via `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` env var
- The API Gateway resolves routes dynamically through Eureka (no hard-coded IPs)

---

### 7.5 Observability: Prometheus + Grafana

```bash
# Open Prometheus — check targets are all UP
kubectl port-forward svc/prometheus 9090:9090 -n smartlingua
# Browser: http://localhost:9090/targets
# Expected: all scrape targets (api-gateway, users, courses, ...) show State=UP

# Open Grafana
kubectl port-forward svc/grafana 3000:3000 -n smartlingua
# Browser: http://localhost:3000  (admin / admin)
```

In Grafana, import dashboard ID **4701** (JVM Micrometer) to see:

- Heap memory usage per microservice
- HTTP request rate and error rate
- JVM thread count, GC pause times

Run a load test to see live metrics update:

```bash
# Install hey (HTTP load generator) or use curl in a loop
for i in $(seq 1 100); do
  curl -s http://$(minikube ip):30093/users/actuator/health > /dev/null
done
# Switch back to Grafana — HTTP rate graph should spike
```

---

## 8. Updating Secrets

The default secrets in `configmap-env.yaml` use `root` passwords suitable only for
local dev. For any shared/production environment:

```bash
# Update the OpenAI API key
kubectl patch secret smartlingua-secrets -n smartlingua \
  --type='json' \
  -p='[{"op":"replace","path":"/data/OPENAI_API_KEY","value":"'$(echo -n "sk-your-key" | base64)'"}]'

# Full replacement
kubectl create secret generic smartlingua-secrets \
  --from-literal=SPRING_DATASOURCE_PASSWORD=<strong-password> \
  --from-literal=MYSQL_ROOT_PASSWORD=<strong-password> \
  --from-literal=KEYCLOAK_ADMIN=admin \
  --from-literal=KEYCLOAK_ADMIN_PASSWORD=<strong-password> \
  --from-literal=OPENAI_API_KEY=<your-key> \
  -n smartlingua \
  --dry-run=client -o yaml | kubectl apply -f -
```

For production use [Sealed Secrets](https://github.com/bitnami-labs/sealed-secrets)
or [External Secrets Operator](https://external-secrets.io/).

---

## 9. Useful Day-2 Commands

```bash
# All resources in the namespace at once
kubectl get all -n smartlingua

# Resource usage (requires metrics-server addon)
minikube addons enable metrics-server
kubectl top pods -n smartlingua
kubectl top nodes

# Exec into a running container
kubectl exec -it deployment/users -n smartlingua -- sh

# Copy a file from a pod
kubectl cp smartlingua/users-xxx:/app/logs/app.log ./app.log

# Describe a failing pod (shows Events section with the actual error)
kubectl describe pod <pod-name> -n smartlingua

# Check ConfigMap contents
kubectl get configmap smartlingua-env -n smartlingua -o yaml

# Restart all pods in a deployment (force re-read of secrets/config)
kubectl rollout restart deployment/users -n smartlingua
```

---

## 10. Teardown

```bash
# Remove all SmartLingua resources (keeps the node/cluster)
kubectl delete namespace smartlingua

# Stop Minikube
minikube stop

# Destroy Minikube VM entirely
minikube delete
```

---

## 11. Architecture Overview

```
                     Internet
                         │
                    [Ingress / NodePort]
                         │
                    ┌────┴────┐
                    │ Frontend│  (Angular, 2 replicas)
                    └────┬────┘
                         │  HTTP
                  ┌──────┴──────┐
                  │ API Gateway │  (Spring Cloud Gateway)
                  └──────┬──────┘
          ┌───────────────┼───────────────┐
          │               │               │
     [Eureka]      [Config Server]   [Keycloak]
     Service        Centralised        OAuth2/OIDC
     Registry       Config             Auth Server
          │
    ┌─────┴──────────────────────────────────────┐
    │            Microservices (9 pods)           │
    │  users  courses  quiz  exams  forum         │
    │  messaging  privetcours  adaptive-learning  │
    │  ai-assistant-service                       │
    └─────────────────┬──────────────────────────┘
                      │
                   [MySQL]
                 Persistent Volume
                      │
         ┌────────────┴────────────┐
         │                         │
    [Prometheus]               [Grafana]
    Metrics scraper            Dashboards
```

All components run in the `smartlingua` namespace. Each pod receives its configuration
from the `smartlingua-env` ConfigMap and `smartlingua-secrets` Secret, and connects to
Eureka for service-to-service resolution — no hard-coded hostnames anywhere.
