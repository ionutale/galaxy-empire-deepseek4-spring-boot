package game

import (
	"context"
	"fmt"
	"time"
)

func formatInstant(t time.Time) string { return t.UTC().Format(time.RFC3339Nano) }

// BuildingService.queueUpgrade
func (e *Engine) QueueUpgrade(ctx context.Context, planetID int64, gridPosition int, playerID int64) (map[string]any, error) {
	var result map[string]any
	err := e.withTx(ctx, func(te *Engine) error {
		planet, err := te.repo.PlanetByID(ctx, planetID)
		if err != nil {
			return err
		}
		if planet == nil {
			return badReq("Planet not found")
		}
		if planet.PlayerID != playerID {
			return badReq("Not your planet")
		}

		count, err := te.repo.ConstructionCountActive(ctx, planetID)
		if err != nil {
			return err
		}
		if count >= te.maxQueue {
			return badReq(fmt.Sprintf("Max queue reached (%d)", te.maxQueue))
		}

		building, err := te.repo.BuildingByPlanetAndGrid(ctx, planetID, gridPosition)
		if err != nil {
			return err
		}
		if building == nil {
			return badReq("No building at this position")
		}
		targetLevel := building.Level + 1

		existing, err := te.repo.ConstructionActiveByPlanet(ctx, planetID)
		if err != nil {
			return err
		}
		for _, q := range existing {
			if q.BuildingType == building.BuildingType {
				return badReq("This building is already being upgraded")
			}
		}

		metalCost := te.bal.MetalCost(building.BuildingType, targetLevel)
		crystalCost := te.bal.CrystalCost(building.BuildingType, targetLevel)
		gasCost := te.bal.GasCost(building.BuildingType, targetLevel)

		if err := te.recalculate(ctx, planet); err != nil {
			return err
		}
		ok, err := te.checkAndDeduct(ctx, planetID, metalCost, crystalCost, gasCost)
		if err != nil {
			return err
		}
		if !ok {
			return badReq("Insufficient resources")
		}

		rfLevel := 0
		if rf, err := te.repo.BuildingByPlanetAndType(ctx, planetID, RobotFactory); err != nil {
			return err
		} else if rf != nil {
			rfLevel = rf.Level
		}

		timeSeconds := te.bal.ConstructionTimeSeconds(building.BuildingType, targetLevel, rfLevel)
		now := time.Now().UTC()
		q := &ConstructionQueue{
			PlanetID: planetID, BuildingType: building.BuildingType, TargetLevel: targetLevel,
			MetalCost: metalCost, CrystalCost: crystalCost, GasCost: gasCost,
			StartedAt: now, CompletesAt: now.Add(time.Duration(timeSeconds) * time.Second),
		}
		if err := te.repo.InsertConstruction(ctx, q); err != nil {
			return err
		}
		result = map[string]any{
			"queueId":          q.ID,
			"buildingType":     building.BuildingType,
			"targetLevel":      targetLevel,
			"completesAt":      formatInstant(q.CompletesAt),
			"remainingSeconds": timeSeconds,
		}
		return nil
	})
	return result, err
}

// BuildingService.completeConstruction
func (e *Engine) completeConstruction(ctx context.Context, queueID int64) (*ConstructionQueue, error) {
	var saved *ConstructionQueue
	err := e.withTx(ctx, func(te *Engine) error {
		q, err := te.repo.ConstructionByID(ctx, queueID)
		if err != nil {
			return err
		}
		if q == nil {
			return badReq("Queue entry not found")
		}
		if q.Completed {
			return badReq("Already completed")
		}
		building, err := te.repo.BuildingByPlanetAndType(ctx, q.PlanetID, q.BuildingType)
		if err != nil {
			return err
		}
		if building == nil {
			return badReq("Building not found")
		}
		if err := te.repo.UpdateBuildingLevel(ctx, building.ID, q.TargetLevel); err != nil {
			return err
		}
		q.Completed = true
		if err := te.repo.UpdateConstruction(ctx, q); err != nil {
			return err
		}
		if planet, err := te.repo.PlanetByID(ctx, q.PlanetID); err == nil && planet != nil {
			if err := te.processQuestEvent(ctx, QuestEvent{planet.PlayerID, "BUILDING_UPGRADED", q.BuildingType, 1}); err != nil {
				return err
			}
		}
		saved = q
		return nil
	})
	return saved, err
}

// BuildingService.speedUpConstruction
func (e *Engine) SpeedUpConstruction(ctx context.Context, queueID, playerID int64) error {
	return e.withTx(ctx, func(te *Engine) error {
		q, err := te.repo.ConstructionByID(ctx, queueID)
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
		return te.repo.UpdateConstruction(ctx, q)
	})
}

// BuildingService.getUpgradeCost
func (e *Engine) GetUpgradeCost(ctx context.Context, planetID int64, gridPosition int) (map[string]any, error) {
	building, err := e.repo.BuildingByPlanetAndGrid(ctx, planetID, gridPosition)
	if err != nil {
		return nil, err
	}
	if building == nil {
		return nil, badReq("No building at this position")
	}
	targetLevel := building.Level + 1
	return map[string]any{
		"buildingType": building.BuildingType,
		"currentLevel": building.Level,
		"targetLevel":  targetLevel,
		"metal":        e.bal.MetalCost(building.BuildingType, targetLevel),
		"crystal":      e.bal.CrystalCost(building.BuildingType, targetLevel),
		"gas":          e.bal.GasCost(building.BuildingType, targetLevel),
		"timeSeconds":  e.bal.ConstructionTimeSeconds(building.BuildingType, targetLevel, 0),
	}, nil
}

func (e *Engine) ConstructionQueueByPlanet(ctx context.Context, planetID int64) ([]ConstructionQueue, error) {
	q, err := e.repo.ConstructionActiveByPlanet(ctx, planetID)
	if err != nil {
		return nil, err
	}
	if q == nil {
		return []ConstructionQueue{}, nil
	}
	return q, nil
}
