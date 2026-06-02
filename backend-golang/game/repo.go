package game

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

// Querier is satisfied by both *pgxpool.Pool and pgx.Tx, so repository methods
// run identically inside or outside a transaction.
type Querier interface {
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

// Repo is the data-access layer. It is the union of all the Spring JPA
// repositories; methods are grouped across repo*.go by aggregate.
type Repo struct {
	db   Querier
	pool *pgxpool.Pool
}

func NewRepo(pool *pgxpool.Pool) *Repo { return &Repo{db: pool, pool: pool} }

// WithTx runs fn against a Repo bound to a transaction, committing on success
// — the equivalent of a @Transactional service method.
func (r *Repo) WithTx(ctx context.Context, fn func(*Repo) error) error {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return err
	}
	txRepo := &Repo{db: tx, pool: r.pool}
	if err := fn(txRepo); err != nil {
		_ = tx.Rollback(ctx)
		return err
	}
	return tx.Commit(ctx)
}

func isNoRows(err error) bool { return errors.Is(err, pgx.ErrNoRows) }

// ---------------- Planet ----------------

const planetCols = `id, player_id, name, galaxy, system_id, slot, metal, crystal, gas, energy, temperature, last_updated, created_at`

func scanPlanet(row pgx.Row) (*Planet, error) {
	var p Planet
	err := row.Scan(&p.ID, &p.PlayerID, &p.Name, &p.Galaxy, &p.SystemID, &p.Slot,
		&p.Metal, &p.Crystal, &p.Gas, &p.Energy, &p.Temperature, &p.LastUpdated, &p.CreatedAt)
	if err != nil {
		return nil, err
	}
	return &p, nil
}

func (r *Repo) PlanetByID(ctx context.Context, id int64) (*Planet, error) {
	p, err := scanPlanet(r.db.QueryRow(ctx, `SELECT `+planetCols+` FROM planet WHERE id=$1`, id))
	if isNoRows(err) {
		return nil, nil
	}
	return p, err
}

func (r *Repo) PlanetExistsAt(ctx context.Context, galaxy, systemID, slot int) (bool, error) {
	var exists bool
	err := r.db.QueryRow(ctx,
		`SELECT EXISTS(SELECT 1 FROM planet WHERE galaxy=$1 AND system_id=$2 AND slot=$3)`,
		galaxy, systemID, slot).Scan(&exists)
	return exists, err
}

func (r *Repo) PlanetsByPlayerOrdered(ctx context.Context, playerID int64) ([]Planet, error) {
	return r.queryPlanets(ctx,
		`SELECT `+planetCols+` FROM planet WHERE player_id=$1 ORDER BY created_at`, playerID)
}

func (r *Repo) SystemIDsByGalaxy(ctx context.Context, galaxy int) ([]int, error) {
	rows, err := r.db.Query(ctx, `SELECT system_id FROM planet WHERE galaxy=$1`, galaxy)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []int
	for rows.Next() {
		var s int
		if err := rows.Scan(&s); err != nil {
			return nil, err
		}
		out = append(out, s)
	}
	return out, rows.Err()
}

func (r *Repo) PlanetsByGalaxyAndSystem(ctx context.Context, galaxy, systemID int) ([]Planet, error) {
	return r.queryPlanets(ctx,
		`SELECT `+planetCols+` FROM planet WHERE galaxy=$1 AND system_id=$2`, galaxy, systemID)
}

func (r *Repo) AllPlanets(ctx context.Context) ([]Planet, error) {
	return r.queryPlanets(ctx, `SELECT `+planetCols+` FROM planet`)
}

func (r *Repo) queryPlanets(ctx context.Context, sql string, args ...any) ([]Planet, error) {
	rows, err := r.db.Query(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Planet
	for rows.Next() {
		var p Planet
		if err := rows.Scan(&p.ID, &p.PlayerID, &p.Name, &p.Galaxy, &p.SystemID, &p.Slot,
			&p.Metal, &p.Crystal, &p.Gas, &p.Energy, &p.Temperature, &p.LastUpdated, &p.CreatedAt); err != nil {
			return nil, err
		}
		out = append(out, p)
	}
	return out, rows.Err()
}

func (r *Repo) InsertPlanet(ctx context.Context, p *Planet) error {
	return r.db.QueryRow(ctx,
		`INSERT INTO planet (player_id, name, galaxy, system_id, slot, metal, crystal, gas, energy, temperature, last_updated, created_at)
		 VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12) RETURNING id`,
		p.PlayerID, p.Name, p.Galaxy, p.SystemID, p.Slot, p.Metal, p.Crystal, p.Gas, p.Energy,
		p.Temperature, p.LastUpdated, p.CreatedAt).Scan(&p.ID)
}

func (r *Repo) UpdatePlanetResources(ctx context.Context, p *Planet) error {
	_, err := r.db.Exec(ctx,
		`UPDATE planet SET metal=$1, crystal=$2, gas=$3, energy=$4, last_updated=$5 WHERE id=$6`,
		p.Metal, p.Crystal, p.Gas, p.Energy, p.LastUpdated, p.ID)
	return err
}

// ---------------- Building ----------------

const buildingCols = `id, planet_id, building_type, level, grid_position`

func (r *Repo) BuildingsByPlanet(ctx context.Context, planetID int64) ([]Building, error) {
	return r.queryBuildings(ctx, `SELECT `+buildingCols+` FROM building WHERE planet_id=$1`, planetID)
}

func (r *Repo) BuildingByPlanetAndGrid(ctx context.Context, planetID int64, grid int) (*Building, error) {
	var b Building
	err := r.db.QueryRow(ctx, `SELECT `+buildingCols+` FROM building WHERE planet_id=$1 AND grid_position=$2`,
		planetID, grid).Scan(&b.ID, &b.PlanetID, &b.BuildingType, &b.Level, &b.GridPosition)
	if isNoRows(err) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &b, nil
}

func (r *Repo) BuildingByPlanetAndType(ctx context.Context, planetID int64, bt string) (*Building, error) {
	var b Building
	err := r.db.QueryRow(ctx, `SELECT `+buildingCols+` FROM building WHERE planet_id=$1 AND building_type=$2`,
		planetID, bt).Scan(&b.ID, &b.PlanetID, &b.BuildingType, &b.Level, &b.GridPosition)
	if isNoRows(err) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &b, nil
}

func (r *Repo) BuildingsByPlayer(ctx context.Context, playerID int64) ([]Building, error) {
	return r.queryBuildings(ctx,
		`SELECT `+buildingCols+` FROM building WHERE planet_id IN (SELECT id FROM planet WHERE player_id=$1)`, playerID)
}

func (r *Repo) queryBuildings(ctx context.Context, sql string, args ...any) ([]Building, error) {
	rows, err := r.db.Query(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Building
	for rows.Next() {
		var b Building
		if err := rows.Scan(&b.ID, &b.PlanetID, &b.BuildingType, &b.Level, &b.GridPosition); err != nil {
			return nil, err
		}
		out = append(out, b)
	}
	return out, rows.Err()
}

func (r *Repo) InsertBuilding(ctx context.Context, b *Building) error {
	return r.db.QueryRow(ctx,
		`INSERT INTO building (planet_id, building_type, level, grid_position) VALUES ($1,$2,$3,$4) RETURNING id`,
		b.PlanetID, b.BuildingType, b.Level, b.GridPosition).Scan(&b.ID)
}

func (r *Repo) UpdateBuildingLevel(ctx context.Context, id int64, level int) error {
	_, err := r.db.Exec(ctx, `UPDATE building SET level=$1 WHERE id=$2`, level, id)
	return err
}

// ---------------- ConstructionQueue ----------------

const constructionCols = `id, planet_id, building_type, target_level, metal_cost, crystal_cost, gas_cost, started_at, completes_at, completed`

func (r *Repo) ConstructionActiveByPlanet(ctx context.Context, planetID int64) ([]ConstructionQueue, error) {
	return r.queryConstruction(ctx,
		`SELECT `+constructionCols+` FROM construction_queue WHERE planet_id=$1 AND completed=false ORDER BY started_at`, planetID)
}

func (r *Repo) ConstructionDue(ctx context.Context, now time.Time) ([]ConstructionQueue, error) {
	return r.queryConstruction(ctx,
		`SELECT `+constructionCols+` FROM construction_queue WHERE completed=false AND completes_at<=$1`, now)
}

func (r *Repo) ConstructionCountActive(ctx context.Context, planetID int64) (int, error) {
	var n int
	err := r.db.QueryRow(ctx,
		`SELECT COUNT(*) FROM construction_queue WHERE planet_id=$1 AND completed=false`, planetID).Scan(&n)
	return n, err
}

func (r *Repo) ConstructionByID(ctx context.Context, id int64) (*ConstructionQueue, error) {
	q, err := r.scanConstruction(r.db.QueryRow(ctx, `SELECT `+constructionCols+` FROM construction_queue WHERE id=$1`, id))
	if isNoRows(err) {
		return nil, nil
	}
	return q, err
}

func (r *Repo) InsertConstruction(ctx context.Context, q *ConstructionQueue) error {
	return r.db.QueryRow(ctx,
		`INSERT INTO construction_queue (planet_id, building_type, target_level, metal_cost, crystal_cost, gas_cost, started_at, completes_at, completed)
		 VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING id`,
		q.PlanetID, q.BuildingType, q.TargetLevel, q.MetalCost, q.CrystalCost, q.GasCost, q.StartedAt, q.CompletesAt, q.Completed).Scan(&q.ID)
}

func (r *Repo) UpdateConstruction(ctx context.Context, q *ConstructionQueue) error {
	_, err := r.db.Exec(ctx, `UPDATE construction_queue SET completes_at=$1, completed=$2 WHERE id=$3`,
		q.CompletesAt, q.Completed, q.ID)
	return err
}

func (r *Repo) scanConstruction(row pgx.Row) (*ConstructionQueue, error) {
	var q ConstructionQueue
	err := row.Scan(&q.ID, &q.PlanetID, &q.BuildingType, &q.TargetLevel, &q.MetalCost, &q.CrystalCost,
		&q.GasCost, &q.StartedAt, &q.CompletesAt, &q.Completed)
	if err != nil {
		return nil, err
	}
	return &q, nil
}

func (r *Repo) queryConstruction(ctx context.Context, sql string, args ...any) ([]ConstructionQueue, error) {
	rows, err := r.db.Query(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []ConstructionQueue
	for rows.Next() {
		var q ConstructionQueue
		if err := rows.Scan(&q.ID, &q.PlanetID, &q.BuildingType, &q.TargetLevel, &q.MetalCost, &q.CrystalCost,
			&q.GasCost, &q.StartedAt, &q.CompletesAt, &q.Completed); err != nil {
			return nil, err
		}
		out = append(out, q)
	}
	return out, rows.Err()
}

// ---------------- PlayerTechnology ----------------

func (r *Repo) TechByPlayer(ctx context.Context, playerID int64) ([]PlayerTechnology, error) {
	rows, err := r.db.Query(ctx, `SELECT id, player_id, technology, level FROM player_technology WHERE player_id=$1`, playerID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []PlayerTechnology
	for rows.Next() {
		var t PlayerTechnology
		if err := rows.Scan(&t.ID, &t.PlayerID, &t.Technology, &t.Level); err != nil {
			return nil, err
		}
		out = append(out, t)
	}
	return out, rows.Err()
}

func (r *Repo) TechByPlayerAndType(ctx context.Context, playerID int64, tech string) (*PlayerTechnology, error) {
	var t PlayerTechnology
	err := r.db.QueryRow(ctx, `SELECT id, player_id, technology, level FROM player_technology WHERE player_id=$1 AND technology=$2`,
		playerID, tech).Scan(&t.ID, &t.PlayerID, &t.Technology, &t.Level)
	if isNoRows(err) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &t, nil
}

func (r *Repo) InsertTech(ctx context.Context, t *PlayerTechnology) error {
	return r.db.QueryRow(ctx,
		`INSERT INTO player_technology (player_id, technology, level) VALUES ($1,$2,$3) RETURNING id`,
		t.PlayerID, t.Technology, t.Level).Scan(&t.ID)
}

func (r *Repo) UpdateTechLevel(ctx context.Context, id int64, level int) error {
	_, err := r.db.Exec(ctx, `UPDATE player_technology SET level=$1 WHERE id=$2`, level, id)
	return err
}

// ---------------- ResearchQueue ----------------

const researchCols = `id, player_id, technology, target_level, metal_cost, crystal_cost, gas_cost, started_at, completes_at, completed`

func (r *Repo) ResearchActiveByPlayer(ctx context.Context, playerID int64) ([]ResearchQueue, error) {
	return r.queryResearch(ctx, `SELECT `+researchCols+` FROM research_queue WHERE player_id=$1 AND completed=false`, playerID)
}

func (r *Repo) ResearchActiveByPlayerAndTech(ctx context.Context, playerID int64, tech string) (*ResearchQueue, error) {
	q, err := r.scanResearch(r.db.QueryRow(ctx,
		`SELECT `+researchCols+` FROM research_queue WHERE player_id=$1 AND completed=false AND technology=$2`, playerID, tech))
	if isNoRows(err) {
		return nil, nil
	}
	return q, err
}

func (r *Repo) ResearchDue(ctx context.Context, now time.Time) ([]ResearchQueue, error) {
	return r.queryResearch(ctx, `SELECT `+researchCols+` FROM research_queue WHERE completed=false AND completes_at<=$1`, now)
}

func (r *Repo) ResearchActiveExists(ctx context.Context, playerID int64) (bool, error) {
	var exists bool
	err := r.db.QueryRow(ctx,
		`SELECT EXISTS(SELECT 1 FROM research_queue WHERE player_id=$1 AND completed=false)`, playerID).Scan(&exists)
	return exists, err
}

func (r *Repo) ResearchByID(ctx context.Context, id int64) (*ResearchQueue, error) {
	q, err := r.scanResearch(r.db.QueryRow(ctx, `SELECT `+researchCols+` FROM research_queue WHERE id=$1`, id))
	if isNoRows(err) {
		return nil, nil
	}
	return q, err
}

func (r *Repo) InsertResearch(ctx context.Context, q *ResearchQueue) error {
	return r.db.QueryRow(ctx,
		`INSERT INTO research_queue (player_id, technology, target_level, metal_cost, crystal_cost, gas_cost, started_at, completes_at, completed)
		 VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING id`,
		q.PlayerID, q.Technology, q.TargetLevel, q.MetalCost, q.CrystalCost, q.GasCost, q.StartedAt, q.CompletesAt, q.Completed).Scan(&q.ID)
}

func (r *Repo) UpdateResearch(ctx context.Context, q *ResearchQueue) error {
	_, err := r.db.Exec(ctx, `UPDATE research_queue SET completes_at=$1, completed=$2 WHERE id=$3`,
		q.CompletesAt, q.Completed, q.ID)
	return err
}

func (r *Repo) scanResearch(row pgx.Row) (*ResearchQueue, error) {
	var q ResearchQueue
	err := row.Scan(&q.ID, &q.PlayerID, &q.Technology, &q.TargetLevel, &q.MetalCost, &q.CrystalCost,
		&q.GasCost, &q.StartedAt, &q.CompletesAt, &q.Completed)
	if err != nil {
		return nil, err
	}
	return &q, nil
}

func (r *Repo) queryResearch(ctx context.Context, sql string, args ...any) ([]ResearchQueue, error) {
	rows, err := r.db.Query(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []ResearchQueue
	for rows.Next() {
		var q ResearchQueue
		if err := rows.Scan(&q.ID, &q.PlayerID, &q.Technology, &q.TargetLevel, &q.MetalCost, &q.CrystalCost,
			&q.GasCost, &q.StartedAt, &q.CompletesAt, &q.Completed); err != nil {
			return nil, err
		}
		out = append(out, q)
	}
	return out, rows.Err()
}

// ---------------- PlayerResource (dark matter) ----------------

func (r *Repo) DarkMatter(ctx context.Context, playerID int64) (int, bool, error) {
	var dm int
	err := r.db.QueryRow(ctx, `SELECT dark_matter FROM player_resource WHERE player_id=$1`, playerID).Scan(&dm)
	if isNoRows(err) {
		return 0, false, nil
	}
	if err != nil {
		return 0, false, err
	}
	return dm, true, nil
}

// UpsertDarkMatterDelta adds delta and returns the new balance. Used for both
// granting and (with a negative delta after a balance check) spending.
func (r *Repo) SetDarkMatter(ctx context.Context, playerID int64, value int) error {
	_, err := r.db.Exec(ctx,
		`INSERT INTO player_resource (player_id, dark_matter) VALUES ($1,$2)
		 ON CONFLICT (player_id) DO UPDATE SET dark_matter=EXCLUDED.dark_matter`, playerID, value)
	return err
}
