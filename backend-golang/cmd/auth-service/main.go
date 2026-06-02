// auth-service: the Go port of the Spring auth-service, on :8081.
package main

import (
	"context"
	"log"
	"net/http"
	"os/signal"
	"syscall"

	"galaxyempire/auth"
	"galaxyempire/internal/config"
	"galaxyempire/internal/database"
	"galaxyempire/internal/eureka"
	"galaxyempire/internal/jwtutil"
	"galaxyempire/internal/web"
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	port := config.Str("SERVER_PORT", "8081")
	secret := config.Str("JWT_SECRET", "default-secret-change-in-production-at-least-256-bits-long")
	expMs := int64(config.Int("JWT_EXPIRATION_MS", 86400000))

	pool, err := database.Connect(ctx)
	if err != nil {
		log.Fatalf("database: %v", err)
	}
	defer pool.Close()
	if err := auth.MigrateDB(ctx, pool); err != nil {
		log.Fatalf("migrate: %v", err)
	}

	svc := auth.NewService(auth.NewRepository(pool), jwtutil.New(secret, expMs))

	mux := http.NewServeMux()
	web.MountActuator(mux, "auth-service")
	svc.RegisterRoutes(mux)

	// Register with the discovery server (best-effort).
	eureka.NewClient(config.Str("EUREKA_SERVER_URL", "http://localhost:8761/eureka"),
		"auth-service", config.Atoi(port)).Start(ctx)

	srv := &http.Server{Addr: ":" + port, Handler: web.CountMiddleware(mux)}
	go func() {
		log.Printf("auth-service listening on :%s", port)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatal(err)
		}
	}()
	<-ctx.Done()
	_ = srv.Shutdown(context.Background())
}
