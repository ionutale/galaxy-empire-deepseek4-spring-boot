package game

import (
	"context"
	"time"

	"github.com/jackc/pgx/v5"
)

// ---------------- PlanetShip ----------------

func (r *Repo) ShipsByPlanet(ctx context.Context, planetID int64) ([]PlanetShip, error) {
	rows, err := r.db.Query(ctx, `SELECT id, planet_id, ship_type, quantity FROM planet_ship WHERE planet_id=$1`, planetID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []PlanetShip
	for rows.Next() {
		var s PlanetShip
		if err := rows.Scan(&s.ID, &s.PlanetID, &s.ShipType, &s.Quantity); err != nil {
			return nil, err
		}
		out = append(out, s)
	}
	return out, rows.Err()
}

func (r *Repo) ShipByPlanetAndType(ctx context.Context, planetID int64, st string) (*PlanetShip, error) {
	var s PlanetShip
	err := r.db.QueryRow(ctx, `SELECT id, planet_id, ship_type, quantity FROM planet_ship WHERE planet_id=$1 AND ship_type=$2`,
		planetID, st).Scan(&s.ID, &s.PlanetID, &s.ShipType, &s.Quantity)
	if isNoRows(err) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &s, nil
}

// UpsertShip sets the absolute quantity for (planet, type), creating the row if
// needed, and returns the stored row.
func (r *Repo) UpsertShipQuantity(ctx context.Context, planetID int64, st string, quantity int) error {
	_, err := r.db.Exec(ctx,
		`INSERT INTO planet_ship (planet_id, ship_type, quantity) VALUES ($1,$2,$3)
		 ON CONFLICT (planet_id, ship_type) DO UPDATE SET quantity=EXCLUDED.quantity`,
		planetID, st, quantity)
	return err
}

func (r *Repo) DeletePlanetShips(ctx context.Context, planetID int64) error {
	_, err := r.db.Exec(ctx, `DELETE FROM planet_ship WHERE planet_id=$1`, planetID)
	return err
}

func (r *Repo) AddShipQuantity(ctx context.Context, planetID int64, st string, delta int) error {
	_, err := r.db.Exec(ctx,
		`INSERT INTO planet_ship (planet_id, ship_type, quantity) VALUES ($1,$2,$3)
		 ON CONFLICT (planet_id, ship_type) DO UPDATE SET quantity=planet_ship.quantity+$3`,
		planetID, st, delta)
	return err
}

// ---------------- PlanetDefense ----------------

func (r *Repo) DefensesByPlanet(ctx context.Context, planetID int64) ([]PlanetDefense, error) {
	rows, err := r.db.Query(ctx, `SELECT id, planet_id, defense_type, quantity FROM planet_defense WHERE planet_id=$1`, planetID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []PlanetDefense
	for rows.Next() {
		var d PlanetDefense
		if err := rows.Scan(&d.ID, &d.PlanetID, &d.DefenseType, &d.Quantity); err != nil {
			return nil, err
		}
		out = append(out, d)
	}
	return out, rows.Err()
}

func (r *Repo) AddDefenseQuantity(ctx context.Context, planetID int64, dt string, delta int) error {
	_, err := r.db.Exec(ctx,
		`INSERT INTO planet_defense (planet_id, defense_type, quantity) VALUES ($1,$2,$3)
		 ON CONFLICT (planet_id, defense_type) DO UPDATE SET quantity=planet_defense.quantity+$3`,
		planetID, dt, delta)
	return err
}

func (r *Repo) SetDefenseQuantity(ctx context.Context, id int64, quantity int) error {
	_, err := r.db.Exec(ctx, `UPDATE planet_defense SET quantity=$1 WHERE id=$2`, quantity, id)
	return err
}

// ---------------- ShipyardQueue ----------------

const shipyardCols = `id, planet_id, ship_type, defense_type, quantity, built_quantity, metal_cost, crystal_cost, gas_cost, started_at, completes_at, completed`

func (r *Repo) ShipyardActiveByPlanet(ctx context.Context, planetID int64) ([]ShipyardQueue, error) {
	return r.queryShipyard(ctx,
		`SELECT `+shipyardCols+` FROM shipyard_queue WHERE planet_id=$1 AND completed=false ORDER BY started_at`, planetID)
}

func (r *Repo) ShipyardDue(ctx context.Context, now time.Time) ([]ShipyardQueue, error) {
	return r.queryShipyard(ctx, `SELECT `+shipyardCols+` FROM shipyard_queue WHERE completed=false AND completes_at<=$1`, now)
}

func (r *Repo) ShipyardByID(ctx context.Context, id int64) (*ShipyardQueue, error) {
	q, err := r.scanShipyard(r.db.QueryRow(ctx, `SELECT `+shipyardCols+` FROM shipyard_queue WHERE id=$1`, id))
	if isNoRows(err) {
		return nil, nil
	}
	return q, err
}

func (r *Repo) InsertShipyard(ctx context.Context, q *ShipyardQueue) error {
	return r.db.QueryRow(ctx,
		`INSERT INTO shipyard_queue (planet_id, ship_type, defense_type, quantity, built_quantity, metal_cost, crystal_cost, gas_cost, started_at, completes_at, completed)
		 VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11) RETURNING id`,
		q.PlanetID, q.ShipType, q.DefenseType, q.Quantity, q.BuiltQuantity, q.MetalCost, q.CrystalCost, q.GasCost,
		q.StartedAt, q.CompletesAt, q.Completed).Scan(&q.ID)
}

func (r *Repo) UpdateShipyard(ctx context.Context, q *ShipyardQueue) error {
	_, err := r.db.Exec(ctx, `UPDATE shipyard_queue SET built_quantity=$1, completes_at=$2, completed=$3 WHERE id=$4`,
		q.BuiltQuantity, q.CompletesAt, q.Completed, q.ID)
	return err
}

func (r *Repo) scanShipyard(row pgx.Row) (*ShipyardQueue, error) {
	var q ShipyardQueue
	err := row.Scan(&q.ID, &q.PlanetID, &q.ShipType, &q.DefenseType, &q.Quantity, &q.BuiltQuantity,
		&q.MetalCost, &q.CrystalCost, &q.GasCost, &q.StartedAt, &q.CompletesAt, &q.Completed)
	if err != nil {
		return nil, err
	}
	return &q, nil
}

func (r *Repo) queryShipyard(ctx context.Context, sql string, args ...any) ([]ShipyardQueue, error) {
	rows, err := r.db.Query(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []ShipyardQueue
	for rows.Next() {
		var q ShipyardQueue
		if err := rows.Scan(&q.ID, &q.PlanetID, &q.ShipType, &q.DefenseType, &q.Quantity, &q.BuiltQuantity,
			&q.MetalCost, &q.CrystalCost, &q.GasCost, &q.StartedAt, &q.CompletesAt, &q.Completed); err != nil {
			return nil, err
		}
		out = append(out, q)
	}
	return out, rows.Err()
}

// ---------------- Fleet ----------------

const fleetCols = `id, origin_planet_id, target_planet_id, player_id, mission, departure_time, arrival_time, return_time, status, metal_loot, crystal_loot, gas_loot`

func (r *Repo) FleetsByOrigin(ctx context.Context, planetID int64) ([]Fleet, error) {
	return r.queryFleets(ctx, `SELECT `+fleetCols+` FROM fleet WHERE origin_planet_id=$1`, planetID)
}

func (r *Repo) FleetByID(ctx context.Context, id int64) (*Fleet, error) {
	f, err := r.scanFleet(r.db.QueryRow(ctx, `SELECT `+fleetCols+` FROM fleet WHERE id=$1`, id))
	if isNoRows(err) {
		return nil, nil
	}
	return f, err
}

func (r *Repo) FleetsArrived(ctx context.Context, now time.Time) ([]Fleet, error) {
	return r.queryFleets(ctx, `SELECT `+fleetCols+` FROM fleet WHERE status=$1 AND arrival_time<=$2`, StatusEnRoute, now)
}

func (r *Repo) FleetsReturned(ctx context.Context, now time.Time) ([]Fleet, error) {
	return r.queryFleets(ctx, `SELECT `+fleetCols+` FROM fleet WHERE status=$1 AND return_time<=$2`, StatusReturning, now)
}

func (r *Repo) InsertFleet(ctx context.Context, f *Fleet) error {
	return r.db.QueryRow(ctx,
		`INSERT INTO fleet (origin_planet_id, target_planet_id, player_id, mission, departure_time, arrival_time, return_time, status, metal_loot, crystal_loot, gas_loot)
		 VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11) RETURNING id`,
		f.OriginPlanetID, f.TargetPlanetID, f.PlayerID, f.Mission, f.DepartureTime, f.ArrivalTime, f.ReturnTime,
		f.Status, f.MetalLoot, f.CrystalLoot, f.GasLoot).Scan(&f.ID)
}

func (r *Repo) UpdateFleet(ctx context.Context, f *Fleet) error {
	_, err := r.db.Exec(ctx,
		`UPDATE fleet SET status=$1, return_time=$2, metal_loot=$3, crystal_loot=$4, gas_loot=$5 WHERE id=$6`,
		f.Status, f.ReturnTime, f.MetalLoot, f.CrystalLoot, f.GasLoot, f.ID)
	return err
}

func (r *Repo) scanFleet(row pgx.Row) (*Fleet, error) {
	var f Fleet
	err := row.Scan(&f.ID, &f.OriginPlanetID, &f.TargetPlanetID, &f.PlayerID, &f.Mission, &f.DepartureTime,
		&f.ArrivalTime, &f.ReturnTime, &f.Status, &f.MetalLoot, &f.CrystalLoot, &f.GasLoot)
	if err != nil {
		return nil, err
	}
	return &f, nil
}

func (r *Repo) queryFleets(ctx context.Context, sql string, args ...any) ([]Fleet, error) {
	rows, err := r.db.Query(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Fleet
	for rows.Next() {
		var f Fleet
		if err := rows.Scan(&f.ID, &f.OriginPlanetID, &f.TargetPlanetID, &f.PlayerID, &f.Mission, &f.DepartureTime,
			&f.ArrivalTime, &f.ReturnTime, &f.Status, &f.MetalLoot, &f.CrystalLoot, &f.GasLoot); err != nil {
			return nil, err
		}
		out = append(out, f)
	}
	return out, rows.Err()
}

// ---------------- FleetShip ----------------

func (r *Repo) FleetShips(ctx context.Context, fleetID int64) ([]FleetShip, error) {
	rows, err := r.db.Query(ctx, `SELECT id, fleet_id, ship_type, quantity FROM fleet_ship WHERE fleet_id=$1`, fleetID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []FleetShip
	for rows.Next() {
		var s FleetShip
		if err := rows.Scan(&s.ID, &s.FleetID, &s.ShipType, &s.Quantity); err != nil {
			return nil, err
		}
		out = append(out, s)
	}
	return out, rows.Err()
}

func (r *Repo) InsertFleetShip(ctx context.Context, s *FleetShip) error {
	return r.db.QueryRow(ctx,
		`INSERT INTO fleet_ship (fleet_id, ship_type, quantity) VALUES ($1,$2,$3) RETURNING id`,
		s.FleetID, s.ShipType, s.Quantity).Scan(&s.ID)
}

func (r *Repo) UpdateFleetShipQuantity(ctx context.Context, id int64, quantity int) error {
	_, err := r.db.Exec(ctx, `UPDATE fleet_ship SET quantity=$1 WHERE id=$2`, quantity, id)
	return err
}

func (r *Repo) DeleteFleetShips(ctx context.Context, fleetID int64) error {
	_, err := r.db.Exec(ctx, `DELETE FROM fleet_ship WHERE fleet_id=$1`, fleetID)
	return err
}

// ---------------- CombatReport ----------------

const combatCols = `id, attacker_id, defender_id, attacker_planet_id, defender_planet_id, timestamp, result, attacker_ships_before, defender_ships_before, attacker_ships_lost, defender_ships_lost, debris_metal, debris_crystal, resources_looted, rounds`

func (r *Repo) CombatReportsByPlanet(ctx context.Context, planetID int64) ([]CombatReport, error) {
	rows, err := r.db.Query(ctx,
		`SELECT `+combatCols+` FROM combat_report WHERE attacker_planet_id=$1 OR defender_planet_id=$1 ORDER BY timestamp DESC`, planetID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []CombatReport
	for rows.Next() {
		c, err := scanCombatRows(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, *c)
	}
	return out, rows.Err()
}

func (r *Repo) CombatReportByID(ctx context.Context, id int64) (*CombatReport, error) {
	row := r.db.QueryRow(ctx, `SELECT `+combatCols+` FROM combat_report WHERE id=$1`, id)
	c, err := scanCombatRow(row)
	if isNoRows(err) {
		return nil, nil
	}
	return c, err
}

func (r *Repo) InsertCombatReport(ctx context.Context, c *CombatReport) error {
	return r.db.QueryRow(ctx,
		`INSERT INTO combat_report (attacker_id, defender_id, attacker_planet_id, defender_planet_id, timestamp, result,
		 attacker_ships_before, defender_ships_before, attacker_ships_lost, defender_ships_lost, debris_metal, debris_crystal, resources_looted, rounds)
		 VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14) RETURNING id`,
		c.AttackerID, c.DefenderID, c.AttackerPlanetID, c.DefenderPlanetID, c.Timestamp, c.Result,
		c.AttackerShipsBefore, c.DefenderShipsBefore, c.AttackerShipsLost, c.DefenderShipsLost,
		c.DebrisMetal, c.DebrisCrystal, c.ResourcesLooted, c.Rounds).Scan(&c.ID)
}

func scanCombatRow(row pgx.Row) (*CombatReport, error) {
	var c CombatReport
	err := row.Scan(&c.ID, &c.AttackerID, &c.DefenderID, &c.AttackerPlanetID, &c.DefenderPlanetID, &c.Timestamp,
		&c.Result, &c.AttackerShipsBefore, &c.DefenderShipsBefore, &c.AttackerShipsLost, &c.DefenderShipsLost,
		&c.DebrisMetal, &c.DebrisCrystal, &c.ResourcesLooted, &c.Rounds)
	if err != nil {
		return nil, err
	}
	return &c, nil
}

func scanCombatRows(rows pgx.Rows) (*CombatReport, error) { return scanCombatRow(rows) }

// ---------------- DebrisField ----------------

func (r *Repo) DebrisByPlanet(ctx context.Context, planetID int64) (*DebrisField, error) {
	var d DebrisField
	err := r.db.QueryRow(ctx, `SELECT id, planet_id, metal, crystal FROM debris_field WHERE planet_id=$1`, planetID).
		Scan(&d.ID, &d.PlanetID, &d.Metal, &d.Crystal)
	if isNoRows(err) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &d, nil
}

// UpsertDebris adds the given amounts to a planet's debris field, creating it if
// absent, and returns the resulting row.
func (r *Repo) AddDebris(ctx context.Context, planetID int64, metal, crystal float64) error {
	_, err := r.db.Exec(ctx,
		`INSERT INTO debris_field (planet_id, metal, crystal) VALUES ($1,$2,$3)
		 ON CONFLICT (planet_id) DO UPDATE SET metal=debris_field.metal+$2, crystal=debris_field.crystal+$3`,
		planetID, metal, crystal)
	return err
}

func (r *Repo) SetDebris(ctx context.Context, planetID int64, metal, crystal float64) error {
	_, err := r.db.Exec(ctx, `UPDATE debris_field SET metal=$1, crystal=$2 WHERE planet_id=$3`, metal, crystal, planetID)
	return err
}

// ---------------- EspionageReport ----------------

func (r *Repo) EspionageByTarget(ctx context.Context, planetID int64) ([]EspionageReport, error) {
	rows, err := r.db.Query(ctx,
		`SELECT id, attacker_id, defender_id, target_planet_id, timestamp, resources_json, ships_json, buildings_json, technologies_json, defenses_json
		 FROM espionage_report WHERE target_planet_id=$1 ORDER BY timestamp DESC`, planetID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []EspionageReport
	for rows.Next() {
		var e EspionageReport
		if err := rows.Scan(&e.ID, &e.AttackerID, &e.DefenderID, &e.TargetPlanetID, &e.Timestamp,
			&e.ResourcesJSON, &e.ShipsJSON, &e.BuildingsJSON, &e.TechnologiesJSON, &e.DefensesJSON); err != nil {
			return nil, err
		}
		out = append(out, e)
	}
	return out, rows.Err()
}

func (r *Repo) InsertEspionage(ctx context.Context, e *EspionageReport) error {
	return r.db.QueryRow(ctx,
		`INSERT INTO espionage_report (attacker_id, defender_id, target_planet_id, timestamp, resources_json, ships_json, buildings_json, technologies_json, defenses_json)
		 VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING id`,
		e.AttackerID, e.DefenderID, e.TargetPlanetID, e.Timestamp, e.ResourcesJSON, e.ShipsJSON, e.BuildingsJSON,
		e.TechnologiesJSON, e.DefensesJSON).Scan(&e.ID)
}
