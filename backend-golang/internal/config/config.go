// Package config provides env helpers and a minimal Spring-Cloud-Config-style
// server. The Go services configure themselves from environment variables (the
// same variables the Java config files ultimately resolve), and the
// config-server stands in for Spring's, serving the raw YAML on :8888 so the
// topology and prometheus scrape target are preserved.
package config

import (
	"encoding/json"
	"net/http"
	"os"
	"strconv"
)

func Str(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func Int(key string, def int) int {
	if v := os.Getenv(key); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return def
}

func Float(key string, def float64) float64 {
	if v := os.Getenv(key); v != "" {
		if f, err := strconv.ParseFloat(v, 64); err == nil {
			return f
		}
	}
	return def
}

// Atoi parses s to an int, returning 0 on failure.
func Atoi(s string) int {
	n, _ := strconv.Atoi(s)
	return n
}

func Bool(key string, def bool) bool {
	if v := os.Getenv(key); v != "" {
		if b, err := strconv.ParseBool(v); err == nil {
			return b
		}
	}
	return def
}

// Server serves config documents keyed by application name.
type Server struct {
	docs map[string]string
}

func NewServer(docs map[string]string) *Server {
	return &Server{docs: docs}
}

// Handler exposes GET /{application}/{profile} returning a Spring-Cloud-Config
// shaped JSON, plus GET /{application}-{profile}.yml returning the raw YAML.
func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /{application}/{profile}", func(w http.ResponseWriter, r *http.Request) {
		app := r.PathValue("application")
		yaml := s.docs[app]
		resp := map[string]any{
			"name":     app,
			"profiles": []string{r.PathValue("profile")},
			"propertySources": []map[string]any{
				{"name": app + ".yml", "source": map[string]any{"raw": yaml}},
			},
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(resp)
	})
	mux.HandleFunc("GET /{file}", func(w http.ResponseWriter, r *http.Request) {
		file := r.PathValue("file")
		// Strip optional .yml / .yaml suffix and any -profile suffix.
		name := file
		for _, ext := range []string{".yml", ".yaml"} {
			if len(name) > len(ext) && name[len(name)-len(ext):] == ext {
				name = name[:len(name)-len(ext)]
			}
		}
		if yaml, ok := s.docs[name]; ok {
			w.Header().Set("Content-Type", "text/yaml")
			_, _ = w.Write([]byte(yaml))
			return
		}
		http.NotFound(w, r)
	})
	return mux
}
