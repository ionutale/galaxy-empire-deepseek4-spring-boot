// eureka-server: the Go service-discovery registry, on :8761, replacing the
// Spring Cloud Netflix Eureka server.
package main

import (
	"log"
	"net/http"

	"galaxyempire/internal/config"
	"galaxyempire/internal/eureka"
	"galaxyempire/internal/web"
)

func main() {
	port := config.Str("SERVER_PORT", "8761")
	mux := http.NewServeMux()

	registry := eureka.NewServer()
	mux.Handle("/eureka/", registry.Handler())
	web.MountActuator(mux, "eureka-server")

	log.Printf("eureka-server listening on :%s", port)
	if err := http.ListenAndServe(":"+port, web.CountMiddleware(mux)); err != nil {
		log.Fatal(err)
	}
}
