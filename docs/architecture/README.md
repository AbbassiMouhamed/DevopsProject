# SmartLingua — Architecture

## Overview

SmartLingua is a microservices-based language-learning platform built with:

- **Backend**: Spring Boot 4.0.2 / Java 21 / Spring Cloud 2025.1.0
- **Frontend**: Angular 17
- **Identity**: Keycloak 25 (OAuth2 / OIDC)
- **Database**: MySQL 8 (one schema per microservice)
- **Service Discovery**: Spring Cloud Netflix Eureka
- **API Gateway**: Spring Cloud Gateway (WebFlux)
- **Config**: Spring Cloud Config Server (native)
- **Monitoring**: Prometheus + Grafana

---

## System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Browser / Client                            │
└────────────────────────────┬────────────────────────────────────────┘
                             │ HTTP :4200
                      ┌──────▼──────┐
                      │  Frontend   │  Angular 17
                      │  (nginx)    │  :80 internal
                      └──────┬──────┘
                             │ proxy /api/*
                      ┌──────▼──────┐
                      │ API Gateway │  Spring Cloud Gateway
                      │   :8093     │
                      └──────┬──────┘
             ┌───────────────┼───────────────┐
             │               │               │
      ┌──────▼──────┐ ┌──────▼──────┐ ┌─────▼──────┐
      │   users     │ │  courses    │ │    quiz    │
      │   :8087     │ │   :8086     │ │   :8088    │
      └──────┬──────┘ └──────┬──────┘ └─────┬──────┘
             │               │               │
      ┌──────▼───────────────▼───────────────▼──────┐
      │                  MySQL 8                     │
      │  smartlingua_users | smartlingua_courses ...  │
      └─────────────────────────────────────────────┘

Additional microservices: exams(:8089), forum(:8096), messaging(:8092),
privetcours(:8091), adaptive-learning(:8094), ai-assistant(:8095)

Infrastructure:
  Eureka        :8761   — service discovery
  Config Server :8890   — centralised config
  Keycloak      :8081   — identity provider

Monitoring:
  Prometheus    :9090   — metrics scraping
  Grafana       :3000   — dashboards
```

---

## Service Port Map

| Service           | Port                           | Database                  |
| ----------------- | ------------------------------ | ------------------------- |
| Frontend          | 4200 (host) / 80 (container)   | —                         |
| API Gateway       | 8093                           | —                         |
| Eureka            | 8761                           | —                         |
| Config Server     | 8890                           | —                         |
| Keycloak          | 8081 (host) / 8080 (container) | —                         |
| Users             | 8087                           | `smartlingua_users`       |
| Courses           | 8086                           | `smartlingua_courses`     |
| Quiz              | 8088                           | `smartlingua_quiz`        |
| Exams             | 8089                           | `smartlingua_exams`       |
| Forum             | 8096                           | `smartlingua_forum`       |
| Messaging         | 8092                           | `smartlingua_messaging`   |
| PrivetCours       | 8091                           | `smartlingua_privetcours` |
| Adaptive Learning | 8094                           | `smartlingua_adaptive`    |
| AI Assistant      | 8095                           | `smartlingua_ai`          |
| Prometheus        | 9090                           | —                         |
| Grafana           | 3000                           | —                         |
| MySQL             | 3306                           | all schemas               |

---

## Request Flow

1. User opens `http://localhost:4200`
2. Angular SPA is served by Nginx
3. API calls go to `/api/*` which Nginx proxies to `http://api-gateway:8093`
4. API Gateway authenticates JWT (Keycloak) and routes to the correct microservice via Eureka
5. Microservice processes the request, reads/writes its MySQL schema
6. All services expose `/actuator/prometheus` — Prometheus scrapes metrics every 15s
7. Grafana visualises the metrics in the SmartLingua dashboard

---

## Security

- All API endpoints are protected by OAuth2/JWT (Keycloak `smartlingua` realm)
- Inter-service communication is within the `smartlingua-net` Docker bridge network
- Non-root container users for all Spring Boot services
- JVM flags: `-Djava.security.egd=file:/dev/./urandom -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`
