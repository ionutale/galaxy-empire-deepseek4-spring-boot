# Galaxy Empire — Go backend

A drop-in Go reimplementation of the Spring Boot backend. It speaks the same
HTTP/WebSocket APIs on the same ports, against the same Postgres databases, with
JWT and BCrypt that are wire-compatible with the Java services — so you can stop
the Java stack and bring this up in its place, and the existing Angular frontend
works unchanged.

## Services

| Service        | Port | Replaces                       |
|----------------|------|--------------------------------|
| eureka-server  | 8761 | Spring Cloud Netflix Eureka    |
| config-server  | 8888 | Spring Cloud Config            |
| gateway        | 8080 | Spring Cloud Gateway           |
| auth-service   | 8081 | auth-service                   |
| game-service   | 8082 | game-service                   |

Each exposes `/actuator/health` and `/actuator/prometheus` so the existing
docker-compose healthchecks and `prometheus.yml` scrape config keep working.

## Layout

```
cmd/<service>/     one main per service binary
internal/
  jwtutil/         JWT signing/validation (HS256/384/512 chosen by key length,
                   matching jjwt — tokens interchange with the Java services)
  database/        pgx pool + idempotent embedded-migration runner
  eureka/          discovery registry (server) + client (register/heartbeat/resolve)
  config/          env helpers + Spring-Cloud-Config-style server
  stomp/           minimal STOMP-1.2-over-WebSocket broker (Ant topic matching)
  web/             JSON helpers, actuator endpoints, Prometheus exposition
auth/              auth domain/repo/service/handlers + migrations
game/              game domain, balancer, repositories, services, handlers + migrations
deploy/Dockerfile  one parameterized image (--build-arg SERVICE=<name>)
```

## Design decisions

- **Discovery & config are replicated** (per request) rather than dropped: the
  Go services register with the Go eureka-server and the gateway resolves
  `lb://` targets through it, falling back to `<APP>_SERVICE_URL` env vars when
  the registry is unavailable. The config-server serves the original YAML; the
  Go services themselves read configuration from environment variables (the
  values that config would resolve to).
- **WebSocket actually works end-to-end.** The frontend connects a native
  WebSocket to `/ws/websocket` and speaks plain STOMP; the broker implements
  CONNECT/SUBSCRIBE/MESSAGE with Ant-style topic matching (`/topic/planet/*`).
  The gateway treats `/ws` as a public path so the browser handshake (which
  cannot send an `Authorization` header) reaches the endpoint.
- **Migrations are idempotent.** On a database already provisioned by
  Flyway/Java the runner detects the sentinel table and records the versions as
  applied without re-running; on a fresh database it runs them in order.
- **`@Transactional` is preserved** via `Engine.withTx`, which binds the
  repositories to a pgx transaction for compound mutations.

## Run

Against an existing database (same one the Java stack uses):

```bash
DB_HOST=localhost DB_PORT=5432 DB_NAME=auth_db DB_USER=auth_user DB_PASSWORD=auth_pass \
  JWT_SECRET=<same-as-java> go run ./cmd/auth-service
```

Full stack via Docker (from the repo root):

```bash
docker compose -f docker-compose.golang.yml up --build
```

## Environment variables

`SERVER_PORT`, `JWT_SECRET`, `JWT_EXPIRATION_MS`, `EUREKA_SERVER_URL`,
`AUTH_SERVICE_URL` / `GAME_SERVICE_URL` (gateway fallback), `DB_HOST`,
`DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `UNIVERSE_SPEED`,
`GAME_MAX_QUEUE_PER_PLANET`, `GAME_DEBUG_ENDPOINTS_ENABLED`.

## Test

```bash
go test ./...
```
