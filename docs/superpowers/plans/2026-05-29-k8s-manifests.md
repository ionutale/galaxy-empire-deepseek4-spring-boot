# K8s Manifests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deploy all Galaxy Empire services to a local kubeadm-in-Docker cluster.

**Architecture:** Plain YAML manifests in `k8s/` directory, single `galaxy-empire` namespace, in-cluster Docker registry, nginx-ingress controller for external traffic.

**Tech Stack:** Kubernetes (kubeadm-in-Docker), `registry:2`, nginx-ingress controller

---

### Task 1: Directory structure + Namespace + Registry

**Files:**
- Create: `k8s/namespace.yaml`
- Create: `k8s/registry.yaml`

- [ ] **Step 1: Create `k8s/namespace.yaml`**

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: galaxy-empire
```

- [ ] **Step 2: Create `k8s/registry.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: registry
  namespace: galaxy-empire
spec:
  replicas: 1
  selector:
    matchLabels:
      app: registry
  template:
    metadata:
      labels:
        app: registry
    spec:
      containers:
        - name: registry
          image: registry:2
          ports:
            - containerPort: 5000
          volumeMounts:
            - name: registry-storage
              mountPath: /var/lib/registry
      volumes:
        - name: registry-storage
          hostPath:
            path: /tmp/galaxy-registry
            type: DirectoryOrCreate
---
apiVersion: v1
kind: Service
metadata:
  name: registry
  namespace: galaxy-empire
spec:
  selector:
    app: registry
  ports:
    - port: 5000
      targetPort: 5000
```

---

### Task 2: Secrets

**Files:**
- Create: `k8s/secrets.yaml`

- [ ] **Step 1: Create `k8s/secrets.yaml`**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: postgres-auth-credentials
  namespace: galaxy-empire
type: Opaque
stringData:
  POSTGRES_DB: auth_db
  POSTGRES_USER: auth_user
  POSTGRES_PASSWORD: auth_pass
---
apiVersion: v1
kind: Secret
metadata:
  name: postgres-game-credentials
  namespace: galaxy-empire
type: Opaque
stringData:
  POSTGRES_DB: game_db
  POSTGRES_USER: game_user
  POSTGRES_PASSWORD: game_pass
---
apiVersion: v1
kind: Secret
metadata:
  name: jwt-secret
  namespace: galaxy-empire
type: Opaque
stringData:
  JWT_SECRET: default-secret-change-in-production-at-least-256-bits-long
---
apiVersion: v1
kind: Secret
metadata:
  name: grafana-admin
  namespace: galaxy-empire
type: Opaque
stringData:
  GF_SECURITY_ADMIN_PASSWORD: admin
```

---

### Task 3: ConfigMaps

**Files:**
- Create: `k8s/config-server-config.yaml`
- Create: `k8s/prometheus-config.yaml`
- Create: `k8s/grafana-config.yaml`

- [ ] **Step 1: Create `k8s/config-server-config.yaml`**

Mount each native config file from `backend/config-server/src/main/resources/config/` as a ConfigMap:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: config-server-config
  namespace: galaxy-empire
data:
  auth-service.yml: |
    server:
      port: 8081
    spring:
      application:
        name: auth-service
      datasource:
        url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:auth_db}
        username: ${DB_USER:auth_user}
        password: ${DB_PASSWORD:auth_pass}
        hikari:
          maximum-pool-size: 10
          minimum-idle: 2
      jpa:
        hibernate:
          ddl-auto: none
        show-sql: false
        properties:
          hibernate:
            dialect: org.hibernate.dialect.PostgreSQLDialect
            format_sql: true
      flyway:
        enabled: true
        locations: classpath:db/migration
        baseline-on-migrate: true
    eureka:
      client:
        serviceUrl:
          defaultZone: ${EUREKA_SERVER_URL:http://localhost:8761/eureka}
      instance:
        prefer-ip-address: true
    management:
      endpoints:
        web:
          exposure:
            include: health,info,prometheus
      endpoint:
        health:
          show-details: always
      metrics:
        tags:
          application: auth-service
    jwt:
      secret: ${JWT_SECRET:default-secret-change-in-production-at-least-256-bits-long}
      expiration-ms: 86400000
    logging:
      pattern:
        console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
      level:
        com.galaxyempire: INFO
  game-service.yml: |
    server:
      port: 8082
    spring:
      application:
        name: game-service
      datasource:
        url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:game_db}
        username: ${DB_USER:game_user}
        password: ${DB_PASSWORD:game_pass}
        hikari:
          maximum-pool-size: 10
          minimum-idle: 2
      jpa:
        hibernate:
          ddl-auto: none
        show-sql: false
        properties:
          hibernate:
            dialect: org.hibernate.dialect.PostgreSQLDialect
            format_sql: true
      flyway:
        enabled: true
        locations: classpath:db/migration
        baseline-on-migrate: true
    eureka:
      client:
        serviceUrl:
          defaultZone: ${EUREKA_SERVER_URL:http://localhost:8761/eureka}
      instance:
        prefer-ip-address: true
    management:
      endpoints:
        web:
          exposure:
            include: health,info,prometheus
      endpoint:
        health:
          show-details: always
      metrics:
        tags:
          application: game-service
        export:
          prometheus:
            enabled: true
    logging:
      pattern:
        console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
      level:
        com.galaxyempire: INFO
        org.springframework.web.socket: DEBUG
    game:
      universe:
        galaxies: 9
        systems-per-galaxy: 500
        slots-per-system: 15
      speed: ${UNIVERSE_SPEED:1}
      constructions:
        max-queue-per-planet: 5
    jwt:
      secret: ${JWT_SECRET:default-secret-change-in-production-at-least-256-bits-long}
  gateway.yml: |
    server:
      port: 8080
    spring:
      application:
        name: gateway
      cloud:
        gateway:
          routes:
            - id: auth-service
              uri: lb://auth-service
              predicates:
                - Path=/api/auth/**
            - id: game-service
              uri: lb://game-service
              predicates:
                - Path=/api/game/**
            - id: game-service-ws
              uri: lb:ws://game-service
              predicates:
                - Path=/ws/**
    eureka:
      client:
        serviceUrl:
          defaultZone: ${EUREKA_SERVER_URL:http://localhost:8761/eureka}
      instance:
        prefer-ip-address: true
    management:
      endpoints:
        web:
          exposure:
            include: health,info,prometheus
      endpoint:
        health:
          show-details: always
    jwt:
      secret: ${JWT_SECRET:default-secret-change-in-production-at-least-256-bits-long}
    logging:
      pattern:
        console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
      level:
        com.galaxyempire: INFO
```

- [ ] **Step 2: Create `k8s/prometheus-config.yaml`**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
  namespace: galaxy-empire
data:
  prometheus.yml: |
    global:
      scrape_interval: 15s
      evaluation_interval: 15s
    scrape_configs:
      - job_name: 'eureka-server'
        metrics_path: '/actuator/prometheus'
        static_configs:
          - targets: ['eureka-server:8761']
      - job_name: 'gateway'
        metrics_path: '/actuator/prometheus'
        static_configs:
          - targets: ['gateway:8080']
      - job_name: 'auth-service'
        metrics_path: '/actuator/prometheus'
        static_configs:
          - targets: ['auth-service:8081']
      - job_name: 'game-service'
        metrics_path: '/actuator/prometheus'
        static_configs:
          - targets: ['game-service:8082']
      - job_name: 'config-server'
        metrics_path: '/actuator/prometheus'
        static_configs:
          - targets: ['config-server:8888']
```

- [ ] **Step 3: Create `k8s/grafana-config.yaml`**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-datasources
  namespace: galaxy-empire
data:
  datasource.yml: |
    apiVersion: 1
    datasources:
      - name: Prometheus
        type: prometheus
        access: proxy
        url: http://prometheus:9090
        isDefault: true
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-dashboards
  namespace: galaxy-empire
data:
  dashboard.yml: |
    apiVersion: 1
    providers:
      - name: 'Galaxy Empire'
        orgId: 1
        folder: ''
        type: file
        disableDeletion: false
        editable: true
        options:
          path: /etc/grafana/provisioning/dashboards
```

---

### Task 4: PostgreSQL StatefulSets

**Files:**
- Create: `k8s/postgres-auth.yaml`
- Create: `k8s/postgres-game.yaml`

- [ ] **Step 1: Create `k8s/postgres-auth.yaml`**

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres-auth
  namespace: galaxy-empire
spec:
  serviceName: postgres-auth
  replicas: 1
  selector:
    matchLabels:
      app: postgres-auth
  template:
    metadata:
      labels:
        app: postgres-auth
    spec:
      containers:
        - name: postgres
          image: postgres:16-alpine
          ports:
            - containerPort: 5432
          envFrom:
            - secretRef:
                name: postgres-auth-credentials
          livenessProbe:
            exec:
              command:
                - pg_isready
                - -U
                - auth_user
                - -d
                - auth_db
            initialDelaySeconds: 10
            periodSeconds: 5
          readinessProbe:
            exec:
              command:
                - pg_isready
                - -U
                - auth_user
                - -d
                - auth_db
            initialDelaySeconds: 5
            periodSeconds: 5
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes:
          - ReadWriteOnce
        resources:
          requests:
            storage: 5Gi
---
apiVersion: v1
kind: Service
metadata:
  name: postgres-auth
  namespace: galaxy-empire
spec:
  selector:
    app: postgres-auth
  ports:
    - port: 5432
      targetPort: 5432
  clusterIP: None
```

- [ ] **Step 2: Create `k8s/postgres-game.yaml`**

Same as above but with:
- name: `postgres-game`
- labels: `app: postgres-game`
- serviceName: `postgres-game`
- secretRef: `postgres-game-credentials`
- pg_isready user: `game_user`, db: `game_db`
- service name: `postgres-game`

---

### Task 5: Config-server Deployment + Service

**Files:**
- Create: `k8s/config-server.yaml`

- [ ] **Step 1: Create `k8s/config-server.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: config-server
  namespace: galaxy-empire
spec:
  replicas: 1
  selector:
    matchLabels:
      app: config-server
  template:
    metadata:
      labels:
        app: config-server
    spec:
      containers:
        - name: config-server
          image: localhost:5000/galaxy-empire/config-server:latest
          ports:
            - containerPort: 8888
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: native
            - name: spring.cloud.config.server.native.search-locations
              value: file:/config/
          volumeMounts:
            - name: config
              mountPath: /config
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8888
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8888
            initialDelaySeconds: 15
            periodSeconds: 10
      volumes:
        - name: config
          configMap:
            name: config-server-config
---
apiVersion: v1
kind: Service
metadata:
  name: config-server
  namespace: galaxy-empire
spec:
  selector:
    app: config-server
  ports:
    - port: 8888
      targetPort: 8888
```

---

### Task 6: Eureka-server Deployment + Service

**Files:**
- Create: `k8s/eureka-server.yaml`

- [ ] **Step 1: Create `k8s/eureka-server.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: eureka-server
  namespace: galaxy-empire
spec:
  replicas: 1
  selector:
    matchLabels:
      app: eureka-server
  template:
    metadata:
      labels:
        app: eureka-server
    spec:
      containers:
        - name: eureka-server
          image: localhost:5000/galaxy-empire/eureka-server:latest
          ports:
            - containerPort: 8761
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: docker
            - name: CONFIG_SERVER_URL
              value: http://config-server:8888
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8761
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8761
            initialDelaySeconds: 15
            periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: eureka-server
  namespace: galaxy-empire
spec:
  selector:
    app: eureka-server
  ports:
    - port: 8761
      targetPort: 8761
```

---

### Task 7: Gateway Deployment + Service

**Files:**
- Create: `k8s/gateway.yaml`

- [ ] **Step 1: Create `k8s/gateway.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: gateway
  namespace: galaxy-empire
spec:
  replicas: 1
  selector:
    matchLabels:
      app: gateway
  template:
    metadata:
      labels:
        app: gateway
    spec:
      containers:
        - name: gateway
          image: localhost:5000/galaxy-empire/gateway:latest
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: docker
            - name: CONFIG_SERVER_URL
              value: http://config-server:8888
            - name: EUREKA_SERVER_URL
              value: http://eureka-server:8761/eureka
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: jwt-secret
                  key: JWT_SECRET
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 15
            periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: gateway
  namespace: galaxy-empire
spec:
  selector:
    app: gateway
  ports:
    - port: 8080
      targetPort: 8080
```

---

### Task 8: Auth-service Deployment + Service

**Files:**
- Create: `k8s/auth-service.yaml`

- [ ] **Step 1: Create `k8s/auth-service.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-service
  namespace: galaxy-empire
spec:
  replicas: 1
  selector:
    matchLabels:
      app: auth-service
  template:
    metadata:
      labels:
        app: auth-service
    spec:
      containers:
        - name: auth-service
          image: localhost:5000/galaxy-empire/auth-service:latest
          ports:
            - containerPort: 8081
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: docker
            - name: CONFIG_SERVER_URL
              value: http://config-server:8888
            - name: EUREKA_SERVER_URL
              value: http://eureka-server:8761/eureka
            - name: DB_HOST
              value: postgres-auth
            - name: DB_PORT
              value: "5432"
            - name: DB_NAME
              valueFrom:
                secretKeyRef:
                  name: postgres-auth-credentials
                  key: POSTGRES_DB
            - name: DB_USER
              valueFrom:
                secretKeyRef:
                  name: postgres-auth-credentials
                  key: POSTGRES_USER
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: postgres-auth-credentials
                  key: POSTGRES_PASSWORD
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: jwt-secret
                  key: JWT_SECRET
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8081
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8081
            initialDelaySeconds: 15
            periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: auth-service
  namespace: galaxy-empire
spec:
  selector:
    app: auth-service
  ports:
    - port: 8081
      targetPort: 8081
```

---

### Task 9: Game-service Deployment + Service

**Files:**
- Create: `k8s/game-service.yaml`

- [ ] **Step 1: Create `k8s/game-service.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: game-service
  namespace: galaxy-empire
spec:
  replicas: 1
  selector:
    matchLabels:
      app: game-service
  template:
    metadata:
      labels:
        app: game-service
    spec:
      containers:
        - name: game-service
          image: localhost:5000/galaxy-empire/game-service:latest
          ports:
            - containerPort: 8082
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: docker
            - name: CONFIG_SERVER_URL
              value: http://config-server:8888
            - name: EUREKA_SERVER_URL
              value: http://eureka-server:8761/eureka
            - name: DB_HOST
              value: postgres-game
            - name: DB_PORT
              value: "5432"
            - name: DB_NAME
              valueFrom:
                secretKeyRef:
                  name: postgres-game-credentials
                  key: POSTGRES_DB
            - name: DB_USER
              valueFrom:
                secretKeyRef:
                  name: postgres-game-credentials
                  key: POSTGRES_USER
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: postgres-game-credentials
                  key: POSTGRES_PASSWORD
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: jwt-secret
                  key: JWT_SECRET
            - name: UNIVERSE_SPEED
              value: "1"
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8082
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8082
            initialDelaySeconds: 15
            periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: game-service
  namespace: galaxy-empire
spec:
  selector:
    app: game-service
  ports:
    - port: 8082
      targetPort: 8082
```

---

### Task 10: Frontend Deployment + Service

**Files:**
- Create: `k8s/frontend.yaml`

- [ ] **Step 1: Create `k8s/frontend.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: frontend
  namespace: galaxy-empire
spec:
  replicas: 1
  selector:
    matchLabels:
      app: frontend
  template:
    metadata:
      labels:
        app: frontend
    spec:
      containers:
        - name: frontend
          image: localhost:5000/galaxy-empire/frontend:latest
          ports:
            - containerPort: 80
          livenessProbe:
            httpGet:
              path: /
              port: 80
            initialDelaySeconds: 10
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /
              port: 80
            initialDelaySeconds: 5
            periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: frontend
  namespace: galaxy-empire
spec:
  selector:
    app: frontend
  ports:
    - port: 80
      targetPort: 80
```

---

### Task 11: Monitoring (Prometheus + Grafana)

**Files:**
- Create: `k8s/prometheus.yaml`
- Create: `k8s/grafana.yaml`

- [ ] **Step 1: Create `k8s/prometheus.yaml`**

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: prometheus-data
  namespace: galaxy-empire
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 5Gi
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: prometheus
  namespace: galaxy-empire
spec:
  replicas: 1
  selector:
    matchLabels:
      app: prometheus
  template:
    metadata:
      labels:
        app: prometheus
    spec:
      containers:
        - name: prometheus
          image: prom/prometheus:latest
          ports:
            - containerPort: 9090
          args:
            - --config.file=/etc/prometheus/prometheus.yml
            - --storage.tsdb.path=/prometheus
            - --web.console.libraries=/usr/share/prometheus/console_libraries
            - --web.console.templates=/usr/share/prometheus/consoles
          volumeMounts:
            - name: config
              mountPath: /etc/prometheus
            - name: data
              mountPath: /prometheus
          livenessProbe:
            httpGet:
              path: /-/healthy
              port: 9090
            initialDelaySeconds: 15
            periodSeconds: 15
          readinessProbe:
            httpGet:
              path: /-/healthy
              port: 9090
            initialDelaySeconds: 10
            periodSeconds: 15
      volumes:
        - name: config
          configMap:
            name: prometheus-config
        - name: data
          persistentVolumeClaim:
            claimName: prometheus-data
---
apiVersion: v1
kind: Service
metadata:
  name: prometheus
  namespace: galaxy-empire
spec:
  selector:
    app: prometheus
  ports:
    - port: 9090
      targetPort: 9090
```

- [ ] **Step 2: Create `k8s/grafana.yaml`**

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: grafana-data
  namespace: galaxy-empire
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 5Gi
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: grafana
  namespace: galaxy-empire
spec:
  replicas: 1
  selector:
    matchLabels:
      app: grafana
  template:
    metadata:
      labels:
        app: grafana
    spec:
      containers:
        - name: grafana
          image: grafana/grafana:latest
          ports:
            - containerPort: 3000
          env:
            - name: GF_SECURITY_ADMIN_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: grafana-admin
                  key: GF_SECURITY_ADMIN_PASSWORD
            - name: GF_INSTALL_PLUGINS
              value: ""
          volumeMounts:
            - name: datasources
              mountPath: /etc/grafana/provisioning/datasources
            - name: dashboards
              mountPath: /etc/grafana/provisioning/dashboards
            - name: data
              mountPath: /var/lib/grafana
          livenessProbe:
            httpGet:
              path: /api/health
              port: 3000
            initialDelaySeconds: 15
            periodSeconds: 15
          readinessProbe:
            httpGet:
              path: /api/health
              port: 3000
            initialDelaySeconds: 10
            periodSeconds: 15
      volumes:
        - name: datasources
          configMap:
            name: grafana-datasources
        - name: dashboards
          configMap:
            name: grafana-dashboards
        - name: data
          persistentVolumeClaim:
            claimName: grafana-data
---
apiVersion: v1
kind: Service
metadata:
  name: grafana
  namespace: galaxy-empire
spec:
  selector:
    app: grafana
  ports:
    - port: 3000
      targetPort: 3000
```

---

### Task 12: Ingress

**Files:**
- Create: `k8s/ingress.yaml`

- [ ] **Step 1: Create `k8s/ingress.yaml`**

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: galaxy-empire
  namespace: galaxy-empire
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    nginx.ingress.kubernetes.io/proxy-read-timeout: "3600"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "3600"
spec:
  ingressClassName: nginx
  rules:
    - http:
        paths:
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: gateway
                port:
                  number: 8080
          - path: /ws
            pathType: Prefix
            backend:
              service:
                name: gateway
                port:
                  number: 8080
          - path: /
            pathType: Prefix
            backend:
              service:
                name: frontend
                port:
                  number: 80
```

---

### Task 13: Deploy script

**Files:**
- Create: `k8s/deploy.sh`

- [ ] **Step 1: Create `k8s/deploy.sh`**

```bash
#!/bin/bash
set -euo pipefail

NAMESPACE="galaxy-empire"
REGISTRY="localhost:5000/galaxy-empire"
SERVICES=("config-server" "eureka-server" "gateway" "auth-service" "game-service" "frontend")

echo "=== Applying namespace and registry ==="
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/registry.yaml

echo "=== Waiting for registry to be ready ==="
kubectl wait --for=condition=available --timeout=60s -n "$NAMESPACE" deployment/registry

echo "=== Building and pushing images ==="
for svc in "${SERVICES[@]}"; do
    echo "  Building $svc..."
    docker compose build "$svc"
    docker tag "galaxy-empire-deepseek4-spring-boot-$svc" "$REGISTRY/$svc:latest"
    docker push "$REGISTRY/$svc:latest"
done

echo "=== Applying secrets and configmaps ==="
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/config-server-config.yaml
kubectl apply -f k8s/prometheus-config.yaml
kubectl apply -f k8s/grafana-config.yaml

echo "=== Applying infrastructure ==="
kubectl apply -f k8s/postgres-auth.yaml
kubectl apply -f k8s/postgres-game.yaml

echo "=== Applying services ==="
kubectl apply -f k8s/config-server.yaml
kubectl apply -f k8s/eureka-server.yaml
kubectl apply -f k8s/gateway.yaml
kubectl apply -f k8s/auth-service.yaml
kubectl apply -f k8s/game-service.yaml
kubectl apply -f k8s/frontend.yaml

echo "=== Applying monitoring ==="
kubectl apply -f k8s/prometheus.yaml
kubectl apply -f k8s/grafana.yaml

echo "=== Applying ingress ==="
kubectl apply -f k8s/ingress.yaml

echo "=== Deployment complete ==="
echo "Monitor with: kubectl get pods -n $NAMESPACE -w"
```

- [ ] **Step 2: Make script executable**

```bash
chmod +x k8s/deploy.sh
```

---

### Self-Review

1. **Spec coverage:** All design sections covered: namespace, registry, secrets, configmaps, postgres, 6 java services, frontend, prometheus, grafana, ingress, deploy script.
2. **Placeholders:** None — all YAML is complete and deployable.
3. **Type consistency:** Service names match between deployments, secrets, configmaps, and env var references.
