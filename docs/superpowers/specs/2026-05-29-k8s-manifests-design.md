# K8s Manifests for Galaxy Empire

## Target Environment
- Local kubeadm-in-Docker cluster (single node)
- In-cluster Docker registry (`registry:2`) at `localhost:5000`
- nginx-ingress controller for external traffic

## Namespace
Single namespace: `galaxy-empire`

## Resource Layout

### Registry
- `k8s/registry.yaml` — Deployment + ClusterIP service on port 5000, hostPath volume for image storage
- Images tagged `localhost:5000/galaxy-empire/<service>:latest`

### Secrets (`k8s/secrets.yaml`)
- `postgres-auth-credentials`: DB_USER, DB_PASSWORD, DB_NAME
- `postgres-game-credentials`: DB_USER, DB_PASSWORD, DB_NAME
- `jwt-secret`: JWT_SECRET
- `grafana-admin`: GF_SECURITY_ADMIN_PASSWORD

### ConfigMaps
- `config-server-config`: Native Spring config files mounted at the expected classpath location
- `prometheus-config`: prometheus.yml
- `grafana-datasources`: datasource provisioning YAML
- `grafana-dashboards`: dashboard provisioning YAML

### PostgreSQL
- 2 StatefulSets: `postgres-auth`, `postgres-game` (postgres:16-alpine)
- 5Gi PVC each via volumeClaimTemplates
- ClusterIP Services
- Liveness probe: `pg_isready`

### Java Services
All follow same pattern:
- Deployment, ClusterIP Service
- Liveness probe: HTTP GET /actuator/health
- Env vars from secrets/configmaps for DB, JWT, config-server URL, eureka URL
- `SPRING_PROFILES_ACTIVE: docker` (config-server gets `native`)

Service-specific:
- **config-server**: ConfigMap volume mount for native config files at `/config/`, override `spring.cloud.config.server.native.search-locations=file:/config/` via env var, port 8888
- **eureka-server**: depends on config-server, port 8761
- **gateway**: depends on eureka-server, port 8080
- **auth-service**: depends on eureka + postgres-auth, port 8081
- **game-service**: depends on eureka + postgres-game, port 8082

### Frontend
- Deployment (nginx:alpine), ClusterIP Service port 80
- Same nginx.conf — proxies /api/* and /ws/* to gateway:8080

### Ingress
- nginx-ingress controller assumed installed
- Single Ingress resource:
  - `/api/*` → gateway:8080
  - `/ws/*` → gateway:8080
  - `/*` → frontend:80

### Monitoring
- **Prometheus**: Deployment, ConfigMap for prometheus.yml, 5Gi PVC, ClusterIP :9090
- **Grafana**: Deployment, ConfigMaps for datasources/dashboards, 5Gi PVC, ClusterIP :3000

## Image Build/Push Workflow
- Build with docker-compose (produces JAR images): `docker compose build <service>`
- Re-tag for K8s: `docker tag <local-image> localhost:5000/galaxy-empire/<service>:latest`
- Push to in-cluster registry: `docker push localhost:5000/galaxy-empire/<service>:latest`
- Or use `deploy.sh` which does all of the above + `kubectl apply -f k8s/`

## File Structure
```
k8s/
  namespace.yaml
  registry.yaml
  secrets.yaml
  config-server-config.yaml        # ConfigMap for config-server native configs
  prometheus-config.yaml           # ConfigMap
  grafana-config.yaml              # ConfigMap for datasources + dashboards
  postgres-auth.yaml               # StatefulSet + Service
  postgres-game.yaml               # StatefulSet + Service
  config-server.yaml               # Deployment + Service
  eureka-server.yaml               # Deployment + Service
  gateway.yaml                     # Deployment + Service
  auth-service.yaml                # Deployment + Service
  game-service.yaml                # Deployment + Service
  frontend.yaml                    # Deployment + Service
  prometheus.yaml                  # Deployment + Service + PVC
  grafana.yaml                     # Deployment + Service + PVC
  ingress.yaml
  deploy.sh                        # One-shot: build, push, apply
```
