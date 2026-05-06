# SmartLingua Monitoring Guide

## 1. Backend Monitoring (Spring Boot Actuator)

All backend services expose Actuator endpoints with Prometheus metrics.

### Required endpoints

- `/actuator/health`
- `/actuator/metrics`
- `/actuator/prometheus`

### Example checks

Run a service locally, then test:

```bash
curl http://localhost:8087/actuator/health
curl http://localhost:8087/actuator/metrics
curl http://localhost:8087/actuator/prometheus
```

Replace the port according to the service you are validating.

## 2. Frontend Monitoring (Angular)

Frontend monitoring in this sprint is done with runtime and build checks:

- CI build validation: `npm run build`
- Unit test execution: `npm test --if-present`
- Browser performance monitoring: Chrome DevTools (`Performance` + `Network`)

### Local frontend checks

```bash
cd frontendw
npm install
npm run build
npm test --if-present
```

## 3. Optional Prometheus/Grafana Integration

This repository already exposes `/actuator/prometheus`, so you can connect:

- Prometheus scrape target: `http://<service-host>:<port>/actuator/prometheus`
- Grafana datasource: Prometheus

This step is optional and can be demonstrated as an extension.
