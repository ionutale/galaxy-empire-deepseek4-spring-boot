package game

import (
	"context"
	"time"
)

func (e *Engine) shipyardLevel(ctx context.Context, planetID int64) (int, error) {
	b, err := e.repo.BuildingByPlanetAndGrid(ctx, planetID, 9)
	if err != nil {
		return 0, err
	}
	if b == nil {
		return 0, nil
	}
	return b.Level, nil
}

func (e *Engine) GetShipTypes(ctx context.Context, planetID int64) ([]map[string]any, error) {
	level, err := e.shipyardLevel(ctx, planetID)
	if err != nil {
		return nil, err
	}
	out := make([]map[string]any, 0, len(ShipTypes))
	for _, t := range ShipTypes {
		req := e.bal.RequiredShipyardLevel(t)
		out = append(out, map[string]any{
			"shipType":              t,
			"metalCost":             e.bal.ShipMetalCost(t),
			"crystalCost":           e.bal.ShipCrystalCost(t),
			"gasCost":               e.bal.ShipGasCost(t),
			"timeSeconds":           e.bal.ShipBuildTimeSeconds(t, float64(level), 0),
			"requiredShipyardLevel": req,
			"available":             level >= req,
		})
	}
	return out, nil
}

func (e *Engine) GetDefenseTypes(ctx context.Context, planetID int64) ([]map[string]any, error) {
	level, err := e.shipyardLevel(ctx, planetID)
	if err != nil {
		return nil, err
	}
	out := make([]map[string]any, 0, len(DefenseTypes))
	for _, t := range DefenseTypes {
		req := e.bal.RequiredShipyardLevelForDefense(t)
		out = append(out, map[string]any{
			"defenseType":           t,
			"metalCost":             e.bal.DefenseMetalCost(t),
			"crystalCost":           e.bal.DefenseCrystalCost(t),
			"gasCost":               e.bal.DefenseGasCost(t),
			"timeSeconds":           e.bal.DefenseBuildTimeSeconds(t, float64(level), 0),
			"requiredShipyardLevel": req,
			"available":             level >= req,
		})
	}
	return out, nil
}

func (e *Engine) GetPlanetDefenses(ctx context.Context, planetID int64) ([]PlanetDefense, error) {
	d, err := e.repo.DefensesByPlanet(ctx, planetID)
	if err != nil {
		return nil, err
	}
	if d == nil {
		return []PlanetDefense{}, nil
	}
	return d, nil
}

func (e *Engine) GetPlanetShips(ctx context.Context, planetID int64) ([]PlanetShip, error) {
	s, err := e.repo.ShipsByPlanet(ctx, planetID)
	if err != nil {
		return nil, err
	}
	if s == nil {
		return []PlanetShip{}, nil
	}
	return s, nil
}

func (e *Engine) BuildShips(ctx context.Context, planetID int64, shipType string, quantity int, playerID int64) (map[string]any, error) {
	var result map[string]any
	err := e.withTx(ctx, func(te *Engine) error {
		planet, err := te.repo.PlanetByID(ctx, planetID)
		if err != nil {
			return err
		}
		if planet == nil {
			return badReq("Planet not found: " + itoa(planetID))
		}
		if planet.PlayerID != playerID {
			return badReq("Planet does not belong to player")
		}
		level, err := te.shipyardLevel(ctx, planetID)
		if err != nil {
			return err
		}
		if level < te.bal.RequiredShipyardLevel(shipType) {
			return badReq("Shipyard level too low for " + shipType)
		}
		metalCost := te.bal.ShipMetalCost(shipType) * float64(quantity)
		crystalCost := te.bal.ShipCrystalCost(shipType) * float64(quantity)
		gasCost := te.bal.ShipGasCost(shipType) * float64(quantity)
		ok, err := te.checkAndDeduct(ctx, planetID, metalCost, crystalCost, gasCost)
		if err != nil {
			return err
		}
		if !ok {
			return badReq("Insufficient resources")
		}
		timeSeconds := te.bal.ShipBuildTimeSeconds(shipType, float64(level), 0)
		now := time.Now().UTC()
		st := shipType
		q := &ShipyardQueue{
			PlanetID: planetID, ShipType: &st, Quantity: quantity, BuiltQuantity: 0,
			MetalCost: metalCost, CrystalCost: crystalCost, GasCost: gasCost,
			StartedAt: now, CompletesAt: now.Add(time.Duration(timeSeconds) * time.Second),
		}
		if err := te.repo.InsertShipyard(ctx, q); err != nil {
			return err
		}
		result = map[string]any{
			"queueId":          q.ID,
			"shipType":         shipType,
			"quantity":         quantity,
			"completesAt":      formatInstant(q.CompletesAt),
			"remainingSeconds": timeSeconds,
		}
		return nil
	})
	return result, err
}

func (e *Engine) BuildDefense(ctx context.Context, planetID int64, defenseType string, quantity int, playerID int64) (map[string]any, error) {
	var result map[string]any
	err := e.withTx(ctx, func(te *Engine) error {
		planet, err := te.repo.PlanetByID(ctx, planetID)
		if err != nil {
			return err
		}
		if planet == nil {
			return badReq("Planet not found: " + itoa(planetID))
		}
		if planet.PlayerID != playerID {
			return badReq("Planet does not belong to player")
		}
		level, err := te.shipyardLevel(ctx, planetID)
		if err != nil {
			return err
		}
		if level < te.bal.RequiredShipyardLevelForDefense(defenseType) {
			return badReq("Shipyard level too low for " + defenseType)
		}
		metalCost := te.bal.DefenseMetalCost(defenseType) * float64(quantity)
		crystalCost := te.bal.DefenseCrystalCost(defenseType) * float64(quantity)
		gasCost := te.bal.DefenseGasCost(defenseType) * float64(quantity)
		ok, err := te.checkAndDeduct(ctx, planetID, metalCost, crystalCost, gasCost)
		if err != nil {
			return err
		}
		if !ok {
			return badReq("Insufficient resources")
		}
		timeSeconds := te.bal.DefenseBuildTimeSeconds(defenseType, float64(level), 0)
		now := time.Now().UTC()
		dt := defenseType
		q := &ShipyardQueue{
			PlanetID: planetID, DefenseType: &dt, Quantity: quantity, BuiltQuantity: 0,
			MetalCost: metalCost, CrystalCost: crystalCost, GasCost: gasCost,
			StartedAt: now, CompletesAt: now.Add(time.Duration(timeSeconds) * time.Second),
		}
		if err := te.repo.InsertShipyard(ctx, q); err != nil {
			return err
		}
		result = map[string]any{
			"queueId":          q.ID,
			"defenseType":      defenseType,
			"quantity":         quantity,
			"completesAt":      formatInstant(q.CompletesAt),
			"remainingSeconds": timeSeconds,
		}
		return nil
	})
	return result, err
}

func (e *Engine) GetShipyardQueue(ctx context.Context, planetID int64) ([]ShipyardQueue, error) {
	q, err := e.repo.ShipyardActiveByPlanet(ctx, planetID)
	if err != nil {
		return nil, err
	}
	if q == nil {
		return []ShipyardQueue{}, nil
	}
	return q, nil
}

func (e *Engine) completeShipyardEntry(ctx context.Context, queueID int64) (*ShipyardQueue, error) {
	var saved *ShipyardQueue
	err := e.withTx(ctx, func(te *Engine) error {
		q, err := te.repo.ShipyardByID(ctx, queueID)
		if err != nil {
			return err
		}
		if q == nil {
			return badReq("Shipyard queue not found")
		}
		q.Completed = true
		q.BuiltQuantity = q.Quantity
		if err := te.repo.UpdateShipyard(ctx, q); err != nil {
			return err
		}
		if q.DefenseType != nil {
			if err := te.repo.AddDefenseQuantity(ctx, q.PlanetID, *q.DefenseType, q.Quantity); err != nil {
				return err
			}
			saved = q
			return nil
		}
		if q.ShipType != nil {
			if err := te.repo.AddShipQuantity(ctx, q.PlanetID, *q.ShipType, q.Quantity); err != nil {
				return err
			}
			if planet, err := te.repo.PlanetByID(ctx, q.PlanetID); err == nil && planet != nil {
				if err := te.processQuestEvent(ctx, QuestEvent{planet.PlayerID, "SHIPS_BUILT", *q.ShipType, q.Quantity}); err != nil {
					return err
				}
			}
		}
		saved = q
		return nil
	})
	return saved, err
}

func (e *Engine) SpeedUpShipyardEntry(ctx context.Context, queueID, playerID int64) error {
	return e.withTx(ctx, func(te *Engine) error {
		q, err := te.repo.ShipyardByID(ctx, queueID)
		if err != nil {
			return err
		}
		if q == nil {
			return badReq("Queue item not found")
		}
		remaining := int64(time.Until(q.CompletesAt).Seconds())
		cost := calculateSpeedUpCost(remaining)
		if cost > 0 {
			ok, err := te.SpendDarkMatter(ctx, playerID, int64(cost))
			if err != nil {
				return err
			}
			if !ok {
				return badReq("Not enough dark matter")
			}
		}
		q.CompletesAt = time.Now().UTC()
		return te.repo.UpdateShipyard(ctx, q)
	})
}

func (e *Engine) completedShipyardEntries(ctx context.Context, before time.Time) ([]ShipyardQueue, error) {
	return e.repo.ShipyardDue(ctx, before)
}
