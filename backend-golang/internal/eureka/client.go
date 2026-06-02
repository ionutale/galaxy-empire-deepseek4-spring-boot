package eureka

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"os"
	"strings"
	"sync/atomic"
	"time"
)

// Client registers a service with the registry, heartbeats, and resolves other
// apps to base URLs for the gateway. If the registry is unreachable it degrades
// gracefully: Resolve falls back to {APP}_SERVICE_URL env vars, so the stack
// still routes even before the registry settles.
type Client struct {
	registryURL string
	app         string
	instanceID  string
	ipAddr      string
	port        int
	http        *http.Client
	rr          map[string]*uint64
}

func NewClient(registryURL, app string, port int) *Client {
	ip := localIP()
	return &Client{
		registryURL: strings.TrimRight(registryURL, "/"),
		app:         strings.ToUpper(app),
		instanceID:  fmt.Sprintf("%s:%s:%d", hostname(), strings.ToLower(app), port),
		ipAddr:      ip,
		port:        port,
		http:        &http.Client{Timeout: 5 * time.Second},
		rr:          map[string]*uint64{},
	}
}

// Start registers and begins the heartbeat loop. Errors are logged-and-ignored
// so a missing registry never blocks service startup.
func (c *Client) Start(ctx context.Context) {
	c.register()
	go func() {
		t := time.NewTicker(30 * time.Second)
		defer t.Stop()
		for {
			select {
			case <-ctx.Done():
				c.deregister()
				return
			case <-t.C:
				if !c.heartbeat() {
					c.register()
				}
			}
		}
	}()
}

func (c *Client) register() {
	inst := Instance{
		App: c.app, InstanceID: c.instanceID, IPAddr: c.ipAddr,
		Port: c.port, Status: "UP",
	}
	body, _ := json.Marshal(inst)
	req, err := http.NewRequest(http.MethodPost,
		fmt.Sprintf("%s/apps/%s", c.registryURL, c.app), bytes.NewReader(body))
	if err != nil {
		return
	}
	req.Header.Set("Content-Type", "application/json")
	if resp, err := c.http.Do(req); err == nil {
		resp.Body.Close()
	}
}

func (c *Client) heartbeat() bool {
	req, err := http.NewRequest(http.MethodPut,
		fmt.Sprintf("%s/apps/%s/%s", c.registryURL, c.app, c.instanceID), nil)
	if err != nil {
		return false
	}
	resp, err := c.http.Do(req)
	if err != nil {
		return false
	}
	resp.Body.Close()
	return resp.StatusCode == http.StatusOK
}

func (c *Client) deregister() {
	req, _ := http.NewRequest(http.MethodDelete,
		fmt.Sprintf("%s/apps/%s/%s", c.registryURL, c.app, c.instanceID), nil)
	if resp, err := c.http.Do(req); err == nil {
		resp.Body.Close()
	}
}

// Resolve returns a base URL (http://host:port) for the named app, round-robin
// across healthy instances, falling back to the {APP}_SERVICE_URL env var.
func (c *Client) Resolve(app string) string {
	app = strings.ToUpper(app)
	resp, err := c.http.Get(fmt.Sprintf("%s/apps/%s", c.registryURL, app))
	if err == nil {
		defer resp.Body.Close()
		var insts []Instance
		if json.NewDecoder(resp.Body).Decode(&insts) == nil && len(insts) > 0 {
			ctr, ok := c.rr[app]
			if !ok {
				var z uint64
				ctr = &z
				c.rr[app] = ctr
			}
			i := atomic.AddUint64(ctr, 1) % uint64(len(insts))
			inst := insts[i]
			return fmt.Sprintf("http://%s:%d", inst.IPAddr, inst.Port)
		}
	}
	if url := os.Getenv(strings.ReplaceAll(app, "-", "_") + "_URL"); url != "" {
		return url
	}
	return ""
}

func localIP() string {
	conn, err := net.Dial("udp", "8.8.8.8:80")
	if err == nil {
		defer conn.Close()
		return conn.LocalAddr().(*net.UDPAddr).IP.String()
	}
	if h, err := os.Hostname(); err == nil {
		if addrs, err := net.LookupHost(h); err == nil && len(addrs) > 0 {
			return addrs[0]
		}
	}
	return "127.0.0.1"
}

func hostname() string {
	if h, err := os.Hostname(); err == nil {
		return h
	}
	return "localhost"
}
