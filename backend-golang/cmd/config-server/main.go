// config-server: the Go stand-in for Spring Cloud Config, on :8888. The Go
// services self-configure from environment variables; this server keeps the
// topology intact and serves the original config documents on request.
package main

import (
	"embed"
	"log"
	"net/http"
	"strings"

	"galaxyempire/internal/config"
	"galaxyempire/internal/web"
)

//go:embed files/*.yml
var configFiles embed.FS

func main() {
	port := config.Str("SERVER_PORT", "8888")

	docs := map[string]string{}
	entries, _ := configFiles.ReadDir("files")
	for _, e := range entries {
		data, err := configFiles.ReadFile("files/" + e.Name())
		if err != nil {
			continue
		}
		name := strings.TrimSuffix(e.Name(), ".yml")
		docs[name] = string(data)
	}

	mux := http.NewServeMux()
	web.MountActuator(mux, "config-server")
	mux.Handle("/", config.NewServer(docs).Handler())

	log.Printf("config-server listening on :%s (serving %d documents)", port, len(docs))
	if err := http.ListenAndServe(":"+port, web.CountMiddleware(mux)); err != nil {
		log.Fatal(err)
	}
}
