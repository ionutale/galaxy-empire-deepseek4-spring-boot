// Package stomp implements a minimal STOMP 1.2 broker over native WebSocket —
// enough to serve Spring's SimpleBroker behaviour that the Angular
// @stomp/stompjs client depends on: CONNECT/CONNECTED, SUBSCRIBE with
// Ant-style destination patterns (/topic/planet/*), and server-pushed MESSAGE
// frames to /topic/** destinations. The client connects with a native
// WebSocket to /ws/websocket, so no SockJS framing is used.
package stomp

import (
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"

	"github.com/gorilla/websocket"
)

const nul = "\x00"

var upgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool { return true }, // setAllowedOriginPatterns("*")
}

type subscription struct {
	id          string
	destination string
}

type session struct {
	conn   *websocket.Conn
	send   chan []byte
	subs   map[string]subscription
	mu     sync.Mutex
	closed bool
}

func (s *session) enqueue(frame []byte) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed {
		return
	}
	select {
	case s.send <- frame:
	default: // drop if the client can't keep up
	}
}

// Broker tracks connected sessions and fans messages out to matching
// subscriptions.
type Broker struct {
	mu       sync.RWMutex
	sessions map[*session]struct{}
	msgID    atomic.Uint64
}

func NewBroker() *Broker {
	return &Broker{sessions: map[*session]struct{}{}}
}

// HandleWebSocket upgrades the request and serves the STOMP protocol.
func (b *Broker) HandleWebSocket(w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	sess := &session{conn: conn, send: make(chan []byte, 64), subs: map[string]subscription{}}

	b.mu.Lock()
	b.sessions[sess] = struct{}{}
	b.mu.Unlock()

	go b.writeLoop(sess)
	b.readLoop(sess)

	b.mu.Lock()
	delete(b.sessions, sess)
	b.mu.Unlock()
	sess.mu.Lock()
	sess.closed = true
	close(sess.send)
	sess.mu.Unlock()
	conn.Close()
}

func (b *Broker) writeLoop(sess *session) {
	for frame := range sess.send {
		if err := sess.conn.WriteMessage(websocket.TextMessage, frame); err != nil {
			return
		}
	}
}

func (b *Broker) readLoop(sess *session) {
	for {
		_, data, err := sess.conn.ReadMessage()
		if err != nil {
			return
		}
		text := string(data)
		// stompjs sends a lone newline as a heartbeat; ignore it.
		if strings.TrimSpace(text) == "" {
			continue
		}
		for _, raw := range strings.Split(text, nul) {
			if strings.TrimSpace(raw) == "" {
				continue
			}
			if !b.handleFrame(sess, raw) {
				return
			}
		}
	}
}

// handleFrame returns false when the session should close.
func (b *Broker) handleFrame(sess *session, raw string) bool {
	cmd, headers, _ := parseFrame(raw)
	switch cmd {
	case "CONNECT", "STOMP":
		sess.enqueue(buildFrame("CONNECTED", map[string]string{
			"version":    "1.2",
			"heart-beat": "0,0",
			"server":     "galaxy-empire-go/1.0",
		}, ""))
	case "SUBSCRIBE":
		id := headers["id"]
		dest := headers["destination"]
		if id == "" {
			id = dest
		}
		sess.mu.Lock()
		sess.subs[id] = subscription{id: id, destination: dest}
		sess.mu.Unlock()
		if rcpt := headers["receipt"]; rcpt != "" {
			sess.enqueue(buildFrame("RECEIPT", map[string]string{"receipt-id": rcpt}, ""))
		}
	case "UNSUBSCRIBE":
		sess.mu.Lock()
		delete(sess.subs, headers["id"])
		sess.mu.Unlock()
	case "SEND":
		// The frontend never publishes; application-bound SENDs are ignored.
	case "DISCONNECT":
		if rcpt := headers["receipt"]; rcpt != "" {
			sess.enqueue(buildFrame("RECEIPT", map[string]string{"receipt-id": rcpt}, ""))
		}
		return false
	}
	return true
}

// Publish sends body (already-encoded JSON) to every subscription whose pattern
// matches destination. Mirrors SimpMessagingTemplate.convertAndSend.
func (b *Broker) Publish(destination, body string) {
	b.mu.RLock()
	defer b.mu.RUnlock()
	for sess := range b.sessions {
		sess.mu.Lock()
		var matches []subscription
		for _, sub := range sess.subs {
			if matchDestination(sub.destination, destination) {
				matches = append(matches, sub)
			}
		}
		sess.mu.Unlock()
		for _, sub := range matches {
			id := b.msgID.Add(1)
			frame := buildFrame("MESSAGE", map[string]string{
				"destination":    destination,
				"subscription":   sub.id,
				"message-id":     strconv.FormatUint(id, 10),
				"content-type":   "application/json",
				"content-length": strconv.Itoa(len(body)),
			}, body)
			sess.enqueue(frame)
		}
	}
}

func parseFrame(raw string) (cmd string, headers map[string]string, body string) {
	headers = map[string]string{}
	// Normalise CRLF and trim any leading newlines.
	raw = strings.ReplaceAll(raw, "\r\n", "\n")
	raw = strings.TrimLeft(raw, "\n")
	parts := strings.SplitN(raw, "\n\n", 2)
	head := parts[0]
	if len(parts) == 2 {
		body = strings.TrimRight(parts[1], nul)
	}
	lines := strings.Split(head, "\n")
	if len(lines) == 0 {
		return "", headers, body
	}
	cmd = strings.TrimSpace(lines[0])
	for _, line := range lines[1:] {
		if i := strings.Index(line, ":"); i >= 0 {
			k := line[:i]
			v := line[i+1:]
			if _, exists := headers[k]; !exists { // first value wins, per spec
				headers[k] = v
			}
		}
	}
	return cmd, headers, body
}

func buildFrame(cmd string, headers map[string]string, body string) []byte {
	var sb strings.Builder
	sb.WriteString(cmd)
	sb.WriteString("\n")
	for k, v := range headers {
		fmt.Fprintf(&sb, "%s:%s\n", k, v)
	}
	sb.WriteString("\n")
	sb.WriteString(body)
	sb.WriteString(nul)
	return []byte(sb.String())
}

// matchDestination implements the subset of Spring's AntPathMatcher needed
// here: '*' matches a single path segment, '**' matches any remaining
// segments, exact text otherwise.
func matchDestination(pattern, dest string) bool {
	if pattern == dest {
		return true
	}
	pp := strings.Split(strings.TrimPrefix(pattern, "/"), "/")
	dp := strings.Split(strings.TrimPrefix(dest, "/"), "/")
	return antMatch(pp, dp)
}

func antMatch(pat, seg []string) bool {
	for len(pat) > 0 {
		p := pat[0]
		if p == "**" {
			if len(pat) == 1 {
				return true
			}
			for i := 0; i <= len(seg); i++ {
				if antMatch(pat[1:], seg[i:]) {
					return true
				}
			}
			return false
		}
		if len(seg) == 0 {
			return false
		}
		if p != "*" && p != seg[0] {
			return false
		}
		pat = pat[1:]
		seg = seg[1:]
	}
	return len(seg) == 0
}
