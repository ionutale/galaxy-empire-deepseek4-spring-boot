// Package web holds small HTTP helpers shared by every service: JSON
// responses, a Spring-Actuator-compatible health endpoint, and a minimal
// Prometheus exposition so the existing prometheus.yml scrape config keeps
// working unchanged.
package web

import (
	"encoding/json"
	"fmt"
	"net/http"
	"runtime"
	"sync/atomic"
	"time"
)

// JSON writes v as a JSON response with the given status code.
func JSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if v != nil {
		_ = json.NewEncoder(w).Encode(v)
	}
}

// Error writes {"error": msg} with the given status — matching the Java
// controllers' error envelope.
func Error(w http.ResponseWriter, status int, msg string) {
	JSON(w, status, map[string]any{"error": msg})
}

var requestCount atomic.Int64
var start = time.Now()

// CountMiddleware tallies requests for the Prometheus exposition.
func CountMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requestCount.Add(1)
		next.ServeHTTP(w, r)
	})
}

// MountActuator registers /actuator/health and /actuator/prometheus on mux,
// matching the endpoints the docker-compose healthchecks and prometheus.yml
// expect.
func MountActuator(mux *http.ServeMux, app string) {
	mux.HandleFunc("/actuator/health", func(w http.ResponseWriter, r *http.Request) {
		JSON(w, http.StatusOK, map[string]any{"status": "UP"})
	})
	mux.HandleFunc("/actuator/info", func(w http.ResponseWriter, r *http.Request) {
		JSON(w, http.StatusOK, map[string]any{"app": app})
	})
	mux.HandleFunc("/actuator/prometheus", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/plain; version=0.0.4")
		var m runtime.MemStats
		runtime.ReadMemStats(&m)
		fmt.Fprintf(w, "# HELP app_up Service is up.\n# TYPE app_up gauge\napp_up{application=%q} 1\n", app)
		fmt.Fprintf(w, "# HELP app_uptime_seconds Seconds since start.\n# TYPE app_uptime_seconds counter\napp_uptime_seconds{application=%q} %.0f\n", app, time.Since(start).Seconds())
		fmt.Fprintf(w, "# HELP http_requests_total Total HTTP requests handled.\n# TYPE http_requests_total counter\nhttp_requests_total{application=%q} %d\n", app, requestCount.Load())
		fmt.Fprintf(w, "# HELP go_memstats_alloc_bytes Allocated heap bytes.\n# TYPE go_memstats_alloc_bytes gauge\ngo_memstats_alloc_bytes{application=%q} %d\n", app, m.Alloc)
		fmt.Fprintf(w, "# HELP go_goroutines Number of goroutines.\n# TYPE go_goroutines gauge\ngo_goroutines{application=%q} %d\n", app, runtime.NumGoroutine())
	})
}

// Env-style helpers live in package config to avoid import cycles.
