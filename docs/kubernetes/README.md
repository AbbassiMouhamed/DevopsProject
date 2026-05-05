# SmartLingua — Kubernetes Deployment Guide

## Prerequisites

- `kubectl` 1.30+ connected to a cluster
- `kustomize` (bundled with kubectl 1.14+)
- Docker images built and accessible (locally or in a registry)

For local testing: [Minikube](https://minikube.sigs.k8s.io/) or [kind](https://kind.sigs.k8s.io/)

---

## Quick Start (Minikube)

```bash
# Start Minikube
minikube start --cpus=4 --memory=8192

# Use Minikube's Docker daemon (builds images inside Minikube)
eval $(minikube docker-env)

# Build all images
docker compose build

# Deploy everything
kubectl apply -k k8s/

# Watch pods come up
kubectl get pods -n smartlingua -w
```

---

## Deploy

```bash
# Apply all resources using kustomize
kubectl apply -k k8s/

# Or apply individual files:
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

---

## Verify Deployment

```bash
# All pods should reach Running/Ready state
kubectl get pods -n smartlingua

# Services
kubectl get svc -n smartlingua

# Check a specific pod
kubectl describe pod -l app=users -n smartlingua

# Logs
kubectl logs -l app=users -n smartlingua -f

# Events (useful for debugging)
kubectl get events -n smartlingua --sort-by=.metadata.creationTimestamp
```

---

## Accessing Services

### NodePort (direct access)

| Service     | NodePort | URL (Minikube)                |
| ----------- | -------- | ----------------------------- |
| Frontend    | 30080    | `http://$(minikube ip):30080` |
| API Gateway | 30093    | `http://$(minikube ip):30093` |
| Prometheus  | 30090    | `http://$(minikube ip):30090` |
| Grafana     | 30300    | `http://$(minikube ip):30300` |

```bash
# Get Minikube IP
minikube ip
```

### Ingress (requires nginx-ingress controller)

```bash
# Enable Minikube ingress addon
minikube addons enable ingress

# Add to /etc/hosts (or C:\Windows\System32\drivers\etc\hosts on Windows)
echo "$(minikube ip) smartlingua.local" | sudo tee -a /etc/hosts

# Then access: http://smartlingua.local
```

### Port-Forward (for debugging)

```bash
kubectl port-forward svc/frontend 4200:80 -n smartlingua
kubectl port-forward svc/api-gateway 8093:8093 -n smartlingua
kubectl port-forward svc/grafana 3000:3000 -n smartlingua
```

---

## Updating Secrets

The default `configmap-env.yaml` uses `root` password and placeholder API keys.  
For production, use proper secrets management:

```bash
# Override OpenAI API key
kubectl patch secret smartlingua-secrets -n smartlingua \
  --type='json' \
  -p='[{"op":"replace","path":"/data/OPENAI_API_KEY","value":"'$(echo -n "sk-your-key" | base64)'"}]'
```

Or use [Sealed Secrets](https://github.com/bitnami-labs/sealed-secrets) / [External Secrets Operator](https://external-secrets.io/).

---

## Teardown

```bash
# Delete the whole namespace (removes everything)
kubectl delete namespace smartlingua

# Or remove resources while keeping the namespace
kubectl delete -k k8s/
```
