package game

import (
	"context"
	"fmt"
)

// GalaxyService.getSystemList
func (e *Engine) GetSystemList(ctx context.Context, galaxy int, playerID int64) ([]map[string]any, error) {
	systemIDs, err := e.repo.SystemIDsByGalaxy(ctx, galaxy)
	if err != nil {
		return nil, err
	}
	occupied := map[int]bool{}
	for _, s := range systemIDs {
		occupied[s] = true
	}
	out := make([]map[string]any, 0, 500)
	for systemID := 1; systemID <= 500; systemID++ {
		planetCount := 0
		hasOwn := false
		if occupied[systemID] {
			planets, err := e.repo.PlanetsByGalaxyAndSystem(ctx, galaxy, systemID)
			if err != nil {
				return nil, err
			}
			planetCount = len(planets)
			for i := range planets {
				if planets[i].PlayerID == playerID {
					hasOwn = true
					break
				}
			}
		}
		out = append(out, map[string]any{
			"systemId":     systemID,
			"planetCount":  planetCount,
			"hasOwnPlanet": hasOwn,
		})
	}
	return out, nil
}

// GalaxyService.getSystemDetail
func (e *Engine) GetSystemDetail(ctx context.Context, galaxy, systemID int, playerID int64) (map[string]any, error) {
	planets, err := e.repo.PlanetsByGalaxyAndSystem(ctx, galaxy, systemID)
	if err != nil {
		return nil, err
	}
	bySlot := map[int]*Planet{}
	for i := range planets {
		bySlot[planets[i].Slot] = &planets[i]
	}

	slots := make([]map[string]any, 0, 15)
	for slot := 1; slot <= 15; slot++ {
		p := bySlot[slot]
		if p == nil {
			slots = append(slots, map[string]any{"slot": slot, "occupied": false})
			continue
		}
		ships, err := e.repo.ShipsByPlanet(ctx, p.ID)
		if err != nil {
			return nil, err
		}
		fleetCount := 0
		for _, s := range ships {
			fleetCount += s.Quantity
		}
		defenses, err := e.repo.DefensesByPlanet(ctx, p.ID)
		if err != nil {
			return nil, err
		}
		defenseCount := 0
		for _, d := range defenses {
			defenseCount += d.Quantity
		}
		debrisMetal, debrisCrystal := 0.0, 0.0
		if df, err := e.repo.DebrisByPlanet(ctx, p.ID); err != nil {
			return nil, err
		} else if df != nil {
			debrisMetal = df.Metal
			debrisCrystal = df.Crystal
		}
		slots = append(slots, map[string]any{
			"slot":          slot,
			"occupied":      true,
			"planetId":      p.ID,
			"planetName":    p.Name,
			"playerId":      p.PlayerID,
			"playerName":    fmt.Sprintf("Player %d", p.PlayerID),
			"isOwn":         p.PlayerID == playerID,
			"fleetCount":    fleetCount,
			"defenseCount":  defenseCount,
			"debrisMetal":   debrisMetal,
			"debrisCrystal": debrisCrystal,
		})
	}
	return map[string]any{"galaxy": galaxy, "systemId": systemID, "slots": slots}, nil
}
