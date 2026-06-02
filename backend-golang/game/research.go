package game

import (
	"context"
	"time"
)

func (e *Engine) techLevelMap(ctx context.Context, playerID int64) (map[string]int, error) {
	techs, err := e.repo.TechByPlayer(ctx, playerID)
	if err != nil {
		return nil, err
	}
	m := map[string]int{}
	for _, t := range techs {
		m[t.Technology] = t.Level
	}
	return m, nil
}

func (e *Engine) highestResearchLabLevel(ctx context.Context, playerID int64) (float64, error) {
	buildings, err := e.repo.BuildingsByPlayer(ctx, playerID)
	if err != nil {
		return 0, err
	}
	max := 0
	for _, b := range buildings {
		if b.BuildingType == ResearchLab && b.Level > max {
			max = b.Level
		}
	}
	return float64(max), nil
}

func (e *Engine) GetTechnologies(ctx context.Context, playerID int64) ([]map[string]any, error) {
	levels, err := e.techLevelMap(ctx, playerID)
	if err != nil {
		return nil, err
	}
	active, err := e.repo.ResearchActiveByPlayer(ctx, playerID)
	if err != nil {
		return nil, err
	}
	activeTech := ""
	if len(active) > 0 {
		activeTech = active[0].Technology
	}
	labLevel, err := e.highestResearchLabLevel(ctx, playerID)
	if err != nil {
		return nil, err
	}

	out := make([]map[string]any, 0, len(Technologies))
	for _, tech := range Technologies {
		cur := levels[tech]
		out = append(out, map[string]any{
			"technology":       tech,
			"level":            cur,
			"metalCost":        e.bal.TechnologyMetalCost(tech, cur),
			"crystalCost":      e.bal.TechnologyCrystalCost(tech, cur),
			"gasCost":          e.bal.TechnologyGasCost(tech, cur),
			"timeSeconds":      e.bal.ResearchTimeSeconds(tech, cur, labLevel),
			"prerequisitesMet": e.bal.MeetsPrerequisites(tech, levels),
			"isResearching":    tech == activeTech,
		})
	}
	return out, nil
}

func (e *Engine) GetTechnologyDetails(ctx context.Context, playerID int64, tech string) (map[string]any, error) {
	levels, err := e.techLevelMap(ctx, playerID)
	if err != nil {
		return nil, err
	}
	cur := levels[tech]
	labLevel, err := e.highestResearchLabLevel(ctx, playerID)
	if err != nil {
		return nil, err
	}
	return map[string]any{
		"technology":       tech,
		"level":            cur,
		"metalCost":        e.bal.TechnologyMetalCost(tech, cur),
		"crystalCost":      e.bal.TechnologyCrystalCost(tech, cur),
		"gasCost":          e.bal.TechnologyGasCost(tech, cur),
		"timeSeconds":      e.bal.ResearchTimeSeconds(tech, cur, labLevel),
		"prerequisitesMet": e.bal.MeetsPrerequisites(tech, levels),
	}, nil
}

func (e *Engine) StartResearch(ctx context.Context, playerID int64, tech string) (map[string]any, error) {
	var result map[string]any
	err := e.withTx(ctx, func(te *Engine) error {
		exists, err := te.repo.ResearchActiveExists(ctx, playerID)
		if err != nil {
			return err
		}
		if exists {
			return badReq("Already researching a technology")
		}
		pt, err := te.repo.TechByPlayerAndType(ctx, playerID, tech)
		if err != nil {
			return err
		}
		if pt == nil {
			pt = &PlayerTechnology{PlayerID: playerID, Technology: tech, Level: 0}
			if err := te.repo.InsertTech(ctx, pt); err != nil {
				return err
			}
		}
		cur := pt.Level
		levels, err := te.techLevelMap(ctx, playerID)
		if err != nil {
			return err
		}
		if !te.bal.MeetsPrerequisites(tech, levels) {
			return badReq("Prerequisites not met for " + tech)
		}
		labLevel, err := te.highestResearchLabLevel(ctx, playerID)
		if err != nil {
			return err
		}
		timeSeconds := te.bal.ResearchTimeSeconds(tech, cur, labLevel)
		now := time.Now().UTC()
		q := &ResearchQueue{
			PlayerID: playerID, Technology: tech, TargetLevel: cur + 1,
			MetalCost:   te.bal.TechnologyMetalCost(tech, cur),
			CrystalCost: te.bal.TechnologyCrystalCost(tech, cur),
			GasCost:     te.bal.TechnologyGasCost(tech, cur),
			StartedAt:   now, CompletesAt: now.Add(time.Duration(timeSeconds) * time.Second),
		}
		if err := te.repo.InsertResearch(ctx, q); err != nil {
			return err
		}
		result = map[string]any{
			"queueId":          q.ID,
			"technology":       tech,
			"targetLevel":      cur + 1,
			"completesAt":      formatInstant(q.CompletesAt),
			"remainingSeconds": timeSeconds,
		}
		return nil
	})
	return result, err
}

func (e *Engine) GetActiveResearch(ctx context.Context, playerID int64) ([]ResearchQueue, error) {
	q, err := e.repo.ResearchActiveByPlayer(ctx, playerID)
	if err != nil {
		return nil, err
	}
	if q == nil {
		return []ResearchQueue{}, nil
	}
	return q, nil
}

func (e *Engine) completeResearch(ctx context.Context, queueID int64) error {
	return e.withTx(ctx, func(te *Engine) error {
		q, err := te.repo.ResearchByID(ctx, queueID)
		if err != nil {
			return err
		}
		if q == nil {
			return badReq("Research queue not found")
		}
		q.Completed = true
		if err := te.repo.UpdateResearch(ctx, q); err != nil {
			return err
		}
		pt, err := te.repo.TechByPlayerAndType(ctx, q.PlayerID, q.Technology)
		if err != nil {
			return err
		}
		if pt == nil {
			return badReq("Player technology not found")
		}
		if err := te.repo.UpdateTechLevel(ctx, pt.ID, q.TargetLevel); err != nil {
			return err
		}
		return te.processQuestEvent(ctx, QuestEvent{q.PlayerID, "RESEARCH_COMPLETED", q.Technology, 1})
	})
}

func (e *Engine) SpeedUpResearch(ctx context.Context, playerID int64, technology string) error {
	return e.withTx(ctx, func(te *Engine) error {
		q, err := te.repo.ResearchActiveByPlayerAndTech(ctx, playerID, technology)
		if err != nil {
			return err
		}
		if q == nil {
			return badReq("No active research for " + technology)
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
		return te.repo.UpdateResearch(ctx, q)
	})
}

func (e *Engine) completedResearches(ctx context.Context, before time.Time) ([]ResearchQueue, error) {
	return e.repo.ResearchDue(ctx, before)
}
