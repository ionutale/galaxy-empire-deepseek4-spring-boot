// game-service: the Go port of the Spring game-service, on :8082. Serves the
// /api/game REST surface, the /ws STOMP endpoint, and runs the background game
// loop.
package main

import (
	"context"
	"log"
	"net/http"
	"os/signal"
	"syscall"

	"galaxyempire/game"
	"galaxyempire/internal/config"
	"galaxyempire/internal/database"
	"galaxyempire/internal/eureka"
	"galaxyempire/internal/stomp"
	"galaxyempire/internal/web"
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	port := config.Str("SERVER_PORT", "8082")
	speed := config.Float("UNIVERSE_SPEED", 1)
	maxQueue := config.Int("GAME_MAX_QUEUE_PER_PLANET", 5)
	debug := config.Bool("GAME_DEBUG_ENDPOINTS_ENABLED", false)

	pool, err := database.Connect(ctx)
	if err != nil {
		log.Fatalf("database: %v", err)
	}
	defer pool.Close()
	if err := game.MigrateDB(ctx, pool); err != nil {
		log.Fatalf("migrate: %v", err)
	}

	broker := stomp.NewBroker()
	engine := game.NewEngine(game.NewRepo(pool), game.NewBalancer(speed), broker, maxQueue, debug)

	mux := http.NewServeMux()
	web.MountActuator(mux, "game-service")
	engine.RegisterRoutes(mux, broker)

	engine.RunGameLoop(ctx)

	eureka.NewClient(config.Str("EUREKA_SERVER_URL", "http://localhost:8761/eureka"),
		"game-service", config.Atoi(port)).Start(ctx)

	srv := &http.Server{Addr: ":" + port, Handler: web.CountMiddleware(mux)}
	go func() {
		log.Printf("game-service listening on :%s (speed=%.1f)", port, speed)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatal(err)
		}
	}()
	<-ctx.Done()
	_ = srv.Shutdown(context.Background())
}
