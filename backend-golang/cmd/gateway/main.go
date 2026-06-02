// gateway: the Go port of the Spring Cloud Gateway, on :8080. It validates JWTs
// (injecting X-Player-Id like the Spring JwtAuthFilter) and reverse-proxies to
// the auth- and game-services, resolving their instances via the discovery
// registry. WebSocket upgrades on /ws are proxied through transparently.
package main

import (
	"context"
	"log"
	"net/http"
	"net/http/httputil"
	"net/url"
	"strings"

	"galaxyempire/internal/config"
	"galaxyempire/internal/eureka"
	"galaxyempire/internal/jwtutil"
	"galaxyempire/internal/web"
)

// publicPaths bypass JWT validation. /ws is included so the browser WebSocket
// handshake (which cannot carry an Authorization header) can reach the STOMP
// endpoint — making live updates work end-to-end.
var publicPaths = []string{"/api/auth/", "/actuator/", "/ws"}

func main() {
	port := config.Str("SERVER_PORT", "8080")
	secret := config.Str("JWT_SECRET", "default-secret-change-in-production-at-least-256-bits-long")
	jwt := jwtutil.New(secret, 0)

	disco := eureka.NewClient(config.Str("EUREKA_SERVER_URL", "http://localhost:8761/eureka"),
		"gateway", config.Atoi(port))

	mux := http.NewServeMux()
	web.MountActuator(mux, "gateway")

	proxy := &gatewayProxy{jwt: jwt, disco: disco}
	mux.HandleFunc("/", proxy.handle)

	// Register self (so it appears in the registry / metrics), then start.
	disco.Start(context.Background())

	log.Printf("gateway listening on :%s", port)
	if err := http.ListenAndServe(":"+port, web.CountMiddleware(mux)); err != nil {
		log.Fatal(err)
	}
}

type gatewayProxy struct {
	jwt   *jwtutil.Util
	disco *eureka.Client
}

func (g *gatewayProxy) handle(w http.ResponseWriter, r *http.Request) {
	path := r.URL.Path

	// Never trust a client-supplied identity header.
	r.Header.Del("X-Player-Id")

	if !isPublic(path) {
		authHeader := r.Header.Get("Authorization")
		if !strings.HasPrefix(authHeader, "Bearer ") {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		sub, err := g.jwt.Subject(strings.TrimPrefix(authHeader, "Bearer "))
		if err != nil {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		r.Header.Set("X-Player-Id", sub)
	}

	target := g.route(path)
	if target == "" {
		web.Error(w, http.StatusServiceUnavailable, "No upstream available")
		return
	}
	u, err := url.Parse(target)
	if err != nil {
		web.Error(w, http.StatusBadGateway, "Bad upstream")
		return
	}
	httputil.NewSingleHostReverseProxy(u).ServeHTTP(w, r)
}

func (g *gatewayProxy) route(path string) string {
	switch {
	case strings.HasPrefix(path, "/api/auth/"):
		return g.disco.Resolve("auth-service")
	case strings.HasPrefix(path, "/api/game/"):
		return g.disco.Resolve("game-service")
	case strings.HasPrefix(path, "/ws"):
		return g.disco.Resolve("game-service")
	}
	return ""
}

func isPublic(path string) bool {
	for _, p := range publicPaths {
		if strings.HasPrefix(path, p) {
			return true
		}
	}
	return false
}
