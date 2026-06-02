package game

import (
	"context"
	"encoding/json"

	"galaxyempire/internal/stomp"
)

// Engine is the game-service business layer. It plays the combined role of the
// Spring @Service beans (BuildingService, FleetService, CombatService, …);
// methods are grouped across the service*.go files by domain. Keeping one
// struct sidesteps the constructor-injection graph while preserving behaviour.
type Engine struct {
	repo           *Repo
	bal            *Balancer
	broker         *stomp.Broker
	maxQueue       int
	debugEndpoints bool
}

func NewEngine(repo *Repo, bal *Balancer, broker *stomp.Broker, maxQueue int, debug bool) *Engine {
	return &Engine{repo: repo, bal: bal, broker: broker, maxQueue: maxQueue, debugEndpoints: debug}
}

// withTx runs fn against an Engine bound to a transaction — the analogue of a
// @Transactional service method.
func (e *Engine) withTx(ctx context.Context, fn func(*Engine) error) error {
	return e.repo.WithTx(ctx, func(tr *Repo) error {
		clone := *e
		clone.repo = tr
		return fn(&clone)
	})
}

// publish sends a JSON payload to a STOMP topic (no-op if no broker, e.g. tests).
func (e *Engine) publish(destination string, payload any) {
	if e.broker == nil {
		return
	}
	body, err := json.Marshal(payload)
	if err != nil {
		return
	}
	e.broker.Publish(destination, string(body))
}

func toJSON(v any) string {
	b, err := json.Marshal(v)
	if err != nil {
		return "{}"
	}
	return string(b)
}
