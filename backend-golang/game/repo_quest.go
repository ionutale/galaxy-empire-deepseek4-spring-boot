package game

import (
	"context"
	"time"

	"github.com/jackc/pgx/v5"
)

const questDefCols = `id, quest_type, category, requirement_type, requirement_value, reward_type, reward_amount, title, description, icon, sort_order, daily`

func (r *Repo) QuestDefsByDaily(ctx context.Context, daily bool) ([]QuestDefinition, error) {
	return r.queryQuestDefs(ctx,
		`SELECT `+questDefCols+` FROM quest_definition WHERE daily=$1 ORDER BY sort_order`, daily)
}

func (r *Repo) QuestDefsByRequirement(ctx context.Context, requirementType string) ([]QuestDefinition, error) {
	return r.queryQuestDefs(ctx,
		`SELECT `+questDefCols+` FROM quest_definition WHERE requirement_type=$1`, requirementType)
}

func (r *Repo) QuestDefByID(ctx context.Context, id int64) (*QuestDefinition, error) {
	var q QuestDefinition
	var desc, icon *string
	err := r.db.QueryRow(ctx, `SELECT `+questDefCols+` FROM quest_definition WHERE id=$1`, id).
		Scan(&q.ID, &q.QuestType, &q.Category, &q.RequirementType, &q.RequirementValue, &q.RewardType,
			&q.RewardAmount, &q.Title, &desc, &icon, &q.SortOrder, &q.Daily)
	if isNoRows(err) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	q.Description = derefStr(desc)
	q.Icon = derefStr(icon)
	return &q, nil
}

func (r *Repo) queryQuestDefs(ctx context.Context, sql string, args ...any) ([]QuestDefinition, error) {
	rows, err := r.db.Query(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []QuestDefinition
	for rows.Next() {
		var q QuestDefinition
		var desc, icon *string
		if err := rows.Scan(&q.ID, &q.QuestType, &q.Category, &q.RequirementType, &q.RequirementValue,
			&q.RewardType, &q.RewardAmount, &q.Title, &desc, &icon, &q.SortOrder, &q.Daily); err != nil {
			return nil, err
		}
		q.Description = derefStr(desc)
		q.Icon = derefStr(icon)
		out = append(out, q)
	}
	return out, rows.Err()
}

const questProgCols = `id, player_id, quest_definition_id, progress, completed, completed_at, claimed, last_reset_date`

// QuestProgressFor finds a player's progress for a quest on a given reset date.
// A nil resetDate matches a NULL last_reset_date (achievements), mirroring
// Spring Data's null-parameter handling.
func (r *Repo) QuestProgressFor(ctx context.Context, playerID, questDefID int64, resetDate *time.Time) (*QuestProgress, error) {
	var row pgx.Row
	if resetDate == nil {
		row = r.db.QueryRow(ctx,
			`SELECT `+questProgCols+` FROM quest_progress WHERE player_id=$1 AND quest_definition_id=$2 AND last_reset_date IS NULL`,
			playerID, questDefID)
	} else {
		row = r.db.QueryRow(ctx,
			`SELECT `+questProgCols+` FROM quest_progress WHERE player_id=$1 AND quest_definition_id=$2 AND last_reset_date=$3`,
			playerID, questDefID, *resetDate)
	}
	q, err := scanQuestProgress(row)
	if isNoRows(err) {
		return nil, nil
	}
	return q, err
}

func (r *Repo) QuestProgressByID(ctx context.Context, id int64) (*QuestProgress, error) {
	q, err := scanQuestProgress(r.db.QueryRow(ctx, `SELECT `+questProgCols+` FROM quest_progress WHERE id=$1`, id))
	if isNoRows(err) {
		return nil, nil
	}
	return q, err
}

func (r *Repo) InsertQuestProgress(ctx context.Context, q *QuestProgress) error {
	return r.db.QueryRow(ctx,
		`INSERT INTO quest_progress (player_id, quest_definition_id, progress, completed, completed_at, claimed, last_reset_date)
		 VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING id`,
		q.PlayerID, q.QuestDefinitionID, q.Progress, q.Completed, q.CompletedAt, q.Claimed, q.LastResetDate).Scan(&q.ID)
}

func (r *Repo) UpdateQuestProgress(ctx context.Context, q *QuestProgress) error {
	_, err := r.db.Exec(ctx,
		`UPDATE quest_progress SET progress=$1, completed=$2, completed_at=$3, claimed=$4 WHERE id=$5`,
		q.Progress, q.Completed, q.CompletedAt, q.Claimed, q.ID)
	return err
}

func scanQuestProgress(row pgx.Row) (*QuestProgress, error) {
	var q QuestProgress
	err := row.Scan(&q.ID, &q.PlayerID, &q.QuestDefinitionID, &q.Progress, &q.Completed,
		&q.CompletedAt, &q.Claimed, &q.LastResetDate)
	if err != nil {
		return nil, err
	}
	return &q, nil
}

func derefStr(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}
