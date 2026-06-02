package game

import (
	"context"
	"fmt"
	"log"
	"time"
)

// RunGameLoop starts the background schedulers — the analogue of the Spring
// @Scheduled methods in GameLoopService: a 5s game tick (construction,
// research, shipyard completions and fleet movement) and a 10s resource tick.
func (e *Engine) RunGameLoop(ctx context.Context) {
	go e.loop(ctx, 5*time.Second, e.processGameLoop)
	go e.loop(ctx, 10*time.Second, func(ctx context.Context) {
		if err := e.tickResources(ctx); err != nil {
			log.Printf("resource tick: %v", err)
		}
	})
}

func (e *Engine) loop(ctx context.Context, every time.Duration, fn func(context.Context)) {
	t := time.NewTicker(every)
	defer t.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-t.C:
			fn(ctx)
		}
	}
}

func (e *Engine) processGameLoop(ctx context.Context) {
	now := time.Now().UTC()

	due, err := e.repo.ConstructionDue(ctx, now)
	if err != nil {
		log.Printf("construction due: %v", err)
	}
	for _, q := range due {
		completed, err := e.completeConstruction(ctx, q.ID)
		if err != nil {
			log.Printf("Failed to process construction %d: %v", q.ID, err)
			continue
		}
		e.publish(fmt.Sprintf("/topic/planet/%d", completed.PlanetID), map[string]any{
			"type":         "CONSTRUCTION_COMPLETE",
			"buildingType": completed.BuildingType,
			"newLevel":     completed.TargetLevel,
		})
	}

	e.processResearchCompletions(ctx, now)
	e.processShipyardCompletions(ctx, now)

	e.processArrivals(ctx, time.Now().UTC())
	e.processReturns(ctx, time.Now().UTC())
}

func (e *Engine) processResearchCompletions(ctx context.Context, now time.Time) {
	completed, err := e.completedResearches(ctx, now)
	if err != nil {
		log.Printf("research due: %v", err)
		return
	}
	for _, q := range completed {
		if err := e.completeResearch(ctx, q.ID); err != nil {
			log.Printf("Failed to process research %d: %v", q.ID, err)
			continue
		}
		e.publish(fmt.Sprintf("/topic/research/%d", q.PlayerID), map[string]any{
			"type":       "RESEARCH_COMPLETE",
			"technology": q.Technology,
			"level":      q.TargetLevel,
		})
	}
}

func (e *Engine) processShipyardCompletions(ctx context.Context, now time.Time) {
	completed, err := e.completedShipyardEntries(ctx, now)
	if err != nil {
		log.Printf("shipyard due: %v", err)
		return
	}
	for _, q := range completed {
		saved, err := e.completeShipyardEntry(ctx, q.ID)
		if err != nil {
			log.Printf("Failed to process shipyard %d: %v", q.ID, err)
			continue
		}
		// Defense entries have no ship type; the Java code skips their message.
		if saved.ShipType == nil {
			continue
		}
		e.publish(fmt.Sprintf("/topic/planet/%d", saved.PlanetID), map[string]any{
			"type":     "SHIP_BUILD_COMPLETE",
			"shipType": *saved.ShipType,
			"quantity": saved.Quantity,
		})
	}
}
