package game

import (
	"context"
	"time"
)

// QuestService.processQuestEvent
func (e *Engine) processQuestEvent(ctx context.Context, event QuestEvent) error {
	matching, err := e.repo.QuestDefsByRequirement(ctx, event.RequirementType)
	if err != nil {
		return err
	}
	for i := range matching {
		qd := &matching[i]
		var resetDate *time.Time
		if qd.Daily {
			resetDate = today()
		}
		qp, err := e.repo.QuestProgressFor(ctx, event.PlayerID, qd.ID, resetDate)
		if err != nil {
			return err
		}
		if qp == nil {
			qp = &QuestProgress{PlayerID: event.PlayerID, QuestDefinitionID: qd.ID, LastResetDate: resetDate}
			if err := e.repo.InsertQuestProgress(ctx, qp); err != nil {
				return err
			}
		}
		if qp.Completed || qp.Claimed {
			continue
		}
		qp.Progress += event.Value
		if qp.Progress >= qd.RequirementValue {
			qp.Completed = true
			now := time.Now().UTC()
			qp.CompletedAt = &now
		}
		if err := e.repo.UpdateQuestProgress(ctx, qp); err != nil {
			return err
		}
	}
	return nil
}

// QuestService.getAvailableQuests
func (e *Engine) GetAvailableQuests(ctx context.Context, playerID int64) ([]map[string]any, error) {
	achievements, err := e.repo.QuestDefsByDaily(ctx, false)
	if err != nil {
		return nil, err
	}
	dailies, err := e.repo.QuestDefsByDaily(ctx, true)
	if err != nil {
		return nil, err
	}
	out := make([]map[string]any, 0, len(achievements)+len(dailies))

	for i := range achievements {
		qd := &achievements[i]
		qp, err := e.repo.QuestProgressFor(ctx, playerID, qd.ID, nil)
		if err != nil {
			return nil, err
		}
		if qp != nil && qp.Claimed {
			continue
		}
		out = append(out, buildQuestInfo(qd, qp))
	}
	t := today()
	for i := range dailies {
		qd := &dailies[i]
		qp, err := e.repo.QuestProgressFor(ctx, playerID, qd.ID, t)
		if err != nil {
			return nil, err
		}
		out = append(out, buildQuestInfo(qd, qp))
	}
	return out, nil
}

func buildQuestInfo(qd *QuestDefinition, qp *QuestProgress) map[string]any {
	var progressID any
	progress := 0
	completed := false
	claimed := false
	if qp != nil {
		progressID = qp.ID
		progress = qp.Progress
		completed = qp.Completed
		claimed = qp.Claimed
	}
	return map[string]any{
		"progressId":        progressID,
		"questDefinitionId": qd.ID,
		"title":             qd.Title,
		"description":       qd.Description,
		"icon":              qd.Icon,
		"questType":         qd.QuestType,
		"category":          qd.Category,
		"progress":          progress,
		"target":            qd.RequirementValue,
		"rewardType":        qd.RewardType,
		"rewardAmount":      qd.RewardAmount,
		"completed":         completed,
		"claimed":           claimed,
	}
}

// QuestService.claimReward
func (e *Engine) ClaimReward(ctx context.Context, playerID, progressID int64) (map[string]any, error) {
	var result map[string]any
	err := e.withTx(ctx, func(te *Engine) error {
		qp, err := te.repo.QuestProgressByID(ctx, progressID)
		if err != nil {
			return err
		}
		if qp == nil {
			return badReq("Quest progress not found")
		}
		if qp.PlayerID != playerID {
			return badReq("Not your quest")
		}
		if !qp.Completed {
			return badReq("Quest not completed")
		}
		if qp.Claimed {
			return badReq("Already claimed")
		}
		qd, err := te.repo.QuestDefByID(ctx, qp.QuestDefinitionID)
		if err != nil {
			return err
		}
		if qd == nil {
			return badReq("Quest definition not found")
		}

		switch qd.RewardType {
		case "DARK_MATTER":
			if err := te.AddDarkMatter(ctx, playerID, qd.RewardAmount); err != nil {
				return err
			}
		case "METAL", "CRYSTAL", "GAS":
			planets, err := te.getPlanetsByPlayer(ctx, playerID)
			if err != nil {
				return err
			}
			if len(planets) > 0 {
				planetID := planets[0]["id"].(int64)
				var metal, crystal, gas float64
				switch qd.RewardType {
				case "METAL":
					metal = float64(qd.RewardAmount)
				case "CRYSTAL":
					crystal = float64(qd.RewardAmount)
				case "GAS":
					gas = float64(qd.RewardAmount)
				}
				if err := te.addResources(ctx, planetID, metal, crystal, gas); err != nil {
					return err
				}
			}
		}

		qp.Claimed = true
		if err := te.repo.UpdateQuestProgress(ctx, qp); err != nil {
			return err
		}
		result = map[string]any{"success": true, "rewardType": qd.RewardType, "rewardAmount": qd.RewardAmount}
		return nil
	})
	return result, err
}
