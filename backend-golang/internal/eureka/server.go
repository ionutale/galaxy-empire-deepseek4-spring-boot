// Package eureka provides a minimal service-discovery registry plus a client,
// replacing the Spring Cloud Netflix Eureka pair. Both ends are ours, so the
// wire format is a simple JSON rather than the full Netflix schema; the
// behaviour (register / renew / evict / resolve) matches what the gateway needs
// to route lb:// targets.
package eureka

import (
	"encoding/json"
	"net/http"
	"strings"
	"sync"
	"time"
)

// Instance is a single registered service instance.
type Instance struct {
	App        string    `json:"app"`
	InstanceID string    `json:"instanceId"`
	IPAddr     string    `json:"ipAddr"`
	Port       int       `json:"port"`
	Status     string    `json:"status"`
	lastRenew  time.Time `json:"-"`
}

// Server is an in-memory registry with heartbeat-based eviction.
type Server struct {
	mu         sync.RWMutex
	apps       map[string]map[string]*Instance
	evictAfter time.Duration
}

func NewServer() *Server {
	s := &Server{apps: map[string]map[string]*Instance{}, evictAfter: 90 * time.Second}
	go s.evictLoop()
	return s
}

func (s *Server) evictLoop() {
	t := time.NewTicker(15 * time.Second)
	for range t.C {
		cutoff := time.Now().Add(-s.evictAfter)
		s.mu.Lock()
		for app, insts := range s.apps {
			for id, inst := range insts {
				if inst.lastRenew.Before(cutoff) {
					delete(insts, id)
				}
			}
			if len(insts) == 0 {
				delete(s.apps, app)
			}
		}
		s.mu.Unlock()
	}
}

// Handler returns the HTTP handler mounted under /eureka.
func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	// Register: POST /eureka/apps/{app}
	mux.HandleFunc("POST /eureka/apps/{app}", func(w http.ResponseWriter, r *http.Request) {
		var inst Instance
		if err := json.NewDecoder(r.Body).Decode(&inst); err != nil {
			http.Error(w, "bad instance", http.StatusBadRequest)
			return
		}
		inst.App = strings.ToUpper(r.PathValue("app"))
		inst.lastRenew = time.Now()
		if inst.Status == "" {
			inst.Status = "UP"
		}
		s.mu.Lock()
		if s.apps[inst.App] == nil {
			s.apps[inst.App] = map[string]*Instance{}
		}
		s.apps[inst.App][inst.InstanceID] = &inst
		s.mu.Unlock()
		w.WriteHeader(http.StatusNoContent)
	})
	// Renew (heartbeat): PUT /eureka/apps/{app}/{id}
	mux.HandleFunc("PUT /eureka/apps/{app}/{id}", func(w http.ResponseWriter, r *http.Request) {
		app := strings.ToUpper(r.PathValue("app"))
		s.mu.Lock()
		defer s.mu.Unlock()
		if insts, ok := s.apps[app]; ok {
			if inst, ok := insts[r.PathValue("id")]; ok {
				inst.lastRenew = time.Now()
				w.WriteHeader(http.StatusOK)
				return
			}
		}
		w.WriteHeader(http.StatusNotFound)
	})
	// Deregister: DELETE /eureka/apps/{app}/{id}
	mux.HandleFunc("DELETE /eureka/apps/{app}/{id}", func(w http.ResponseWriter, r *http.Request) {
		app := strings.ToUpper(r.PathValue("app"))
		s.mu.Lock()
		if insts, ok := s.apps[app]; ok {
			delete(insts, r.PathValue("id"))
		}
		s.mu.Unlock()
		w.WriteHeader(http.StatusOK)
	})
	// List one app: GET /eureka/apps/{app}
	mux.HandleFunc("GET /eureka/apps/{app}", func(w http.ResponseWriter, r *http.Request) {
		app := strings.ToUpper(r.PathValue("app"))
		s.mu.RLock()
		out := s.snapshot(app)
		s.mu.RUnlock()
		writeJSON(w, out)
	})
	// List all: GET /eureka/apps
	mux.HandleFunc("GET /eureka/apps", func(w http.ResponseWriter, r *http.Request) {
		s.mu.RLock()
		all := map[string][]*Instance{}
		for app := range s.apps {
			all[app] = s.snapshot(app)
		}
		s.mu.RUnlock()
		writeJSON(w, all)
	})
	return mux
}

func (s *Server) snapshot(app string) []*Instance {
	var out []*Instance
	for _, inst := range s.apps[app] {
		cp := *inst
		out = append(out, &cp)
	}
	return out
}

func writeJSON(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(v)
}
