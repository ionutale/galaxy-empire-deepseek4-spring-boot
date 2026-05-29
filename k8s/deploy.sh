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
