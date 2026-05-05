# SmartLingua — Monitoring Guide

## Stack

| Tool       | Version | Port | Credentials   |
| ---------- | ------- | ---- | ------------- |
| Prometheus | 2.53.0  | 9090 | —             |
| Grafana    | 11.1.0  | 3000 | admin / admin |

---

## Start Monitoring

Monitoring is included in the main `docker compose` stack:

```bash
docker compose up -d prometheus grafana
```

Or start only monitoring (for development):

```bash
docker compose -f docker-compose-monitoring.yml up -d
```

---

## Prometheus

Access: http://localhost:9090

### Verified Targets

Navigate to **Status → Targets** to confirm all microservices are being scraped.

All 12 backend services expose metrics at `/actuator/prometheus` (Spring Boot Actuator + Micrometer).

### Useful Queries

```promql
# HTTP request rate per service
sum(rate(http_server_requests_seconds_count[1m])) by (application)

# JVM heap memory (MB)
jvm_memory_used_bytes{area="heap"} / 1048576

# 99th percentile response time (ms)
histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, application)) * 1000

# Error rate (5xx)
sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m])) by (application)

# Active DB connections (HikariCP)
hikaricp_connections_active

# Services that are UP
count(up == 1)
```

---

## Grafana

Access: http://localhost:3000

### Pre-provisioned Dashboard

The **SmartLingua — Microservices Overview** dashboard is automatically provisioned on startup.

Panels:

- Services UP / DOWN counters
- HTTP requests per second (per service)
- JVM Heap Memory Used (MB)
- HTTP p99 response time (ms)
- HTTP 5xx error rate
- CPU usage gauge
- Active DB connections

### Template Variable

Use the `$service` variable to filter panels by service name (multi-select, supports All).

### Datasource

Prometheus datasource is auto-provisioned at `http://prometheus:9090`.

---

## Adding Alerts (Grafana)

1. Open any panel → Edit → Alert tab
2. Set condition, e.g. `avg() of http p99 > 500ms`
3. Configure notification channel (email, Slack, PagerDuty, etc.)

### Example Alert Rule (Prometheus)

```yaml
# Add to monitoring/prometheus-rules.yml
groups:
  - name: smartlingua
    rules:
      - alert: ServiceDown
        expr: up == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Service {{ $labels.job }} is down"

      - alert: HighErrorRate
        expr: sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (application) > 0.1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High error rate on {{ $labels.application }}"
```
