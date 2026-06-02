package game

import (
	"context"
	"log"
	"math"
	"strconv"
	"time"
)

// LaunchFleet is the port of FleetService.launchFleet.
func (e *Engine) LaunchFleet(ctx context.Context, originPlanetID, targetPlanetID int64, mission string,
	ships map[string]int, playerID int64, params map[string]any) (map[string]any, error) {
	var result map[string]any
	err := e.withTx(ctx, func(te *Engine) error {
		origin, err := te.repo.PlanetByID(ctx, originPlanetID)
		if err != nil {
			return err
		}
		if origin == nil {
			return badReq("Origin planet not found")
		}
		if origin.PlayerID != playerID {
			return badReq("Origin planet does not belong to player")
		}
		if originPlanetID == targetPlanetID {
			return badReq("Target must be a different planet")
		}
		if len(ships) == 0 {
			return badReq("Must send at least one ship")
		}

		switch mission {
		case MissionDeploy:
			target, err := te.repo.PlanetByID(ctx, targetPlanetID)
			if err != nil {
				return err
			}
			if target == nil {
				return badReq("Target planet not found")
			}
			if target.PlayerID != playerID {
				return badReq("Can only deploy to own planets")
			}
		case MissionTransport:
			target, err := te.repo.PlanetByID(ctx, targetPlanetID)
			if err != nil {
				return err
			}
			if target == nil {
				return badReq("Target planet not found")
			}
			if target.PlayerID != playerID {
				return badReq("Can only transport to own planets")
			}
			metal := paramFloat(params, "metal")
			crystal := paramFloat(params, "crystal")
			gas := paramFloat(params, "gas")
			if metal <= 0 && crystal <= 0 && gas <= 0 {
				return badReq("Must transport at least one resource type")
			}
			var totalCargo float64
			for st, qty := range ships {
				totalCargo += float64(te.bal.ShipCargo(st)) * float64(qty)
			}
			if metal+crystal+gas > totalCargo {
				return badReq("Resource amount exceeds cargo capacity")
			}
			ok, err := te.checkAndDeduct(ctx, originPlanetID, metal, crystal, gas)
			if err != nil {
				return err
			}
			if !ok {
				return badReq("Insufficient resources at origin planet")
			}
		case MissionColonize:
			if ships[ColonyShip] <= 0 {
				return badReq("Colonize mission requires at least 1 Colony Ship")
			}
		case MissionSpy:
			target, err := te.repo.PlanetByID(ctx, targetPlanetID)
			if err != nil {
				return err
			}
			if target == nil {
				return badReq("Target planet not found")
			}
			if target.PlayerID == playerID {
				return badReq("Cannot spy on own planet")
			}
			if ships[EspionageProbe] <= 0 {
				return badReq("Spy mission requires at least 1 Espionage Probe")
			}
		case MissionRecycle:
			df, err := te.repo.DebrisByPlanet(ctx, targetPlanetID)
			if err != nil {
				return err
			}
			if df == nil || (df.Metal <= 0 && df.Crystal <= 0) {
				return badReq("No debris field at target planet")
			}
			if ships[Recycler] <= 0 {
				return badReq("Recycle mission requires at least 1 Recycler")
			}
		}

		// Deduct ships from origin.
		for st, qty := range ships {
			ps, err := te.repo.ShipByPlanetAndType(ctx, originPlanetID, st)
			if err != nil {
				return err
			}
			if ps == nil {
				return badReq("No " + st + " at origin planet")
			}
			if ps.Quantity < qty {
				return badReq("Insufficient " + st + " at origin planet")
			}
			if err := te.repo.UpsertShipQuantity(ctx, originPlanetID, st, ps.Quantity-qty); err != nil {
				return err
			}
		}

		travelTimeSecs := te.bal.TravelTimeSeconds(1)
		now := time.Now().UTC()
		fleet := &Fleet{
			OriginPlanetID: originPlanetID, TargetPlanetID: targetPlanetID, PlayerID: playerID,
			Mission: mission, DepartureTime: now, ArrivalTime: now.Add(time.Duration(travelTimeSecs) * time.Second),
			Status: StatusEnRoute,
		}
		if mission == MissionTransport {
			fleet.MetalLoot = paramFloat(params, "metal")
			fleet.CrystalLoot = paramFloat(params, "crystal")
			fleet.GasLoot = paramFloat(params, "gas")
		}
		if err := te.repo.InsertFleet(ctx, fleet); err != nil {
			return err
		}
		for st, qty := range ships {
			if err := te.repo.InsertFleetShip(ctx, &FleetShip{FleetID: fleet.ID, ShipType: st, Quantity: qty}); err != nil {
				return err
			}
		}

		result = map[string]any{
			"fleetId":           fleet.ID,
			"mission":           mission,
			"arrivalTime":       formatInstant(fleet.ArrivalTime),
			"travelTimeSeconds": travelTimeSecs,
		}
		return nil
	})
	return result, err
}

func (e *Engine) GetFleetDetail(ctx context.Context, fleetID int64) (map[string]any, error) {
	fleet, err := e.repo.FleetByID(ctx, fleetID)
	if err != nil {
		return nil, err
	}
	if fleet == nil {
		return nil, badReq("Fleet not found")
	}
	ships, err := e.repo.FleetShips(ctx, fleetID)
	if err != nil {
		return nil, err
	}
	shipMap := map[string]int{}
	for _, s := range ships {
		shipMap[s.ShipType] = s.Quantity
	}
	return map[string]any{"fleet": fleet, "ships": shipMap}, nil
}

func (e *Engine) GetPlanetFleets(ctx context.Context, planetID int64) ([]Fleet, error) {
	f, err := e.repo.FleetsByOrigin(ctx, planetID)
	if err != nil {
		return nil, err
	}
	if f == nil {
		return []Fleet{}, nil
	}
	return f, nil
}

func (e *Engine) RecallFleet(ctx context.Context, fleetID, playerID int64) error {
	return e.withTx(ctx, func(te *Engine) error {
		fleet, err := te.repo.FleetByID(ctx, fleetID)
		if err != nil {
			return err
		}
		if fleet == nil {
			return badReq("Fleet not found")
		}
		if fleet.PlayerID != playerID {
			return badReq("Fleet does not belong to player")
		}
		if fleet.Status != StatusEnRoute {
			return badReq("Fleet cannot be recalled")
		}
		fleet.Status = StatusReturning
		travel := fleet.ArrivalTime.Sub(fleet.DepartureTime)
		rt := time.Now().UTC().Add(travel)
		fleet.ReturnTime = &rt
		return te.repo.UpdateFleet(ctx, fleet)
	})
}

// processArrivals handles EN_ROUTE fleets whose arrival time has passed.
func (e *Engine) processArrivals(ctx context.Context, now time.Time) {
	arrivals, err := e.repo.FleetsArrived(ctx, now)
	if err != nil {
		log.Printf("processArrivals query: %v", err)
		return
	}
	for i := range arrivals {
		fleet := arrivals[i]
		if err := e.withTx(ctx, func(te *Engine) error { return te.processArrival(ctx, &fleet) }); err != nil {
			log.Printf("Failed to process fleet %d: %v", fleet.ID, err)
			fleet.Status = StatusArrived
			_ = e.repo.UpdateFleet(ctx, &fleet)
		}
	}
}

func (e *Engine) processArrival(ctx context.Context, fleet *Fleet) error {
	ships, err := e.repo.FleetShips(ctx, fleet.ID)
	if err != nil {
		return err
	}
	if len(ships) == 0 {
		fleet.Status = StatusReturning
		rt := time.Now().UTC()
		fleet.ReturnTime = &rt
		return e.repo.UpdateFleet(ctx, fleet)
	}

	travel := fleet.ArrivalTime.Sub(fleet.DepartureTime)
	returnAfterTravel := func() {
		fleet.Status = StatusReturning
		rt := time.Now().UTC().Add(travel)
		fleet.ReturnTime = &rt
	}

	switch fleet.Mission {
	case MissionAttack:
		if _, err := e.resolveCombat(ctx, fleet, ships); err != nil {
			return err
		}
		current, err := e.repo.FleetShips(ctx, fleet.ID)
		if err != nil {
			return err
		}
		if anySurvivors(current) {
			returnAfterTravel()
		} else {
			fleet.Status = StatusArrived
			fleet.ReturnTime = nil
		}
		return e.repo.UpdateFleet(ctx, fleet)

	case MissionDeploy:
		for _, fs := range ships {
			if err := e.repo.AddShipQuantity(ctx, fleet.TargetPlanetID, fs.ShipType, fs.Quantity); err != nil {
				return err
			}
		}
		fleet.Status = StatusArrived
		fleet.ReturnTime = nil
		return e.repo.UpdateFleet(ctx, fleet)

	case MissionTransport:
		if target, err := e.repo.PlanetByID(ctx, fleet.TargetPlanetID); err != nil {
			return err
		} else if target != nil {
			if err := e.addResources(ctx, fleet.TargetPlanetID, fleet.MetalLoot, fleet.CrystalLoot, fleet.GasLoot); err != nil {
				return err
			}
		}
		returnAfterTravel()
		return e.repo.UpdateFleet(ctx, fleet)

	case MissionColonize:
		for _, fs := range ships {
			if fs.ShipType == ColonyShip && fs.Quantity > 0 {
				if err := e.repo.UpdateFleetShipQuantity(ctx, fs.ID, fs.Quantity-1); err != nil {
					return err
				}
				break
			}
		}
		current, err := e.repo.FleetShips(ctx, fleet.ID)
		if err != nil {
			return err
		}
		if anySurvivors(current) {
			returnAfterTravel()
		} else {
			fleet.Status = StatusArrived
			fleet.ReturnTime = nil
		}
		return e.repo.UpdateFleet(ctx, fleet)

	case MissionSpy:
		if err := e.processSpy(ctx, fleet, ships); err != nil {
			return err
		}
		fleet.Status = StatusReturning
		rt := time.Now().UTC()
		fleet.ReturnTime = &rt
		return e.repo.UpdateFleet(ctx, fleet)

	case MissionRecycle:
		if df, err := e.repo.DebrisByPlanet(ctx, fleet.TargetPlanetID); err != nil {
			return err
		} else if df != nil {
			recyclers := 0
			for _, fs := range ships {
				if fs.ShipType == Recycler {
					recyclers += fs.Quantity
				}
			}
			cargo := float64(recyclers) * float64(e.bal.ShipCargo(Recycler))
			collectMetal := math.Min(df.Metal, cargo)
			collectCrystal := math.Min(df.Crystal, cargo-collectMetal)
			fleet.MetalLoot = collectMetal
			fleet.CrystalLoot = collectCrystal
			if err := e.repo.SetDebris(ctx, fleet.TargetPlanetID, df.Metal-collectMetal, df.Crystal-collectCrystal); err != nil {
				return err
			}
		}
		returnAfterTravel()
		return e.repo.UpdateFleet(ctx, fleet)
	}
	return nil
}

func (e *Engine) processSpy(ctx context.Context, fleet *Fleet, ships []FleetShip) error {
	attackerEsp := 0
	if t, err := e.repo.TechByPlayerAndType(ctx, fleet.PlayerID, EspionageTech); err != nil {
		return err
	} else if t != nil {
		attackerEsp = t.Level
	}
	target, err := e.repo.PlanetByID(ctx, fleet.TargetPlanetID)
	if err != nil {
		return err
	}
	defenderEsp := 0
	if target != nil {
		if t, err := e.repo.TechByPlayerAndType(ctx, target.PlayerID, EspionageTech); err != nil {
			return err
		} else if t != nil {
			defenderEsp = t.Level
		}
	}
	for _, fs := range ships {
		if err := e.repo.UpdateFleetShipQuantity(ctx, fs.ID, 0); err != nil {
			return err
		}
	}
	if attackerEsp <= defenderEsp || target == nil {
		return nil
	}

	diff := attackerEsp - defenderEsp
	report := &EspionageReport{
		AttackerID: fleet.PlayerID, DefenderID: target.PlayerID, TargetPlanetID: fleet.TargetPlanetID,
		Timestamp:     time.Now().UTC(),
		ResourcesJSON: toJSON(map[string]float64{"metal": target.Metal, "crystal": target.Crystal, "gas": target.Gas}),
		ShipsJSON:     "{}", BuildingsJSON: "{}", TechnologiesJSON: "{}", DefensesJSON: "{}",
	}
	if diff >= 1 {
		planetShips, err := e.repo.ShipsByPlanet(ctx, fleet.TargetPlanetID)
		if err != nil {
			return err
		}
		m := map[string]int{}
		for _, ps := range planetShips {
			if ps.Quantity > 0 {
				m[ps.ShipType] = ps.Quantity
			}
		}
		report.ShipsJSON = toJSON(m)
	}
	if diff >= 2 {
		buildings, err := e.repo.BuildingsByPlanet(ctx, fleet.TargetPlanetID)
		if err != nil {
			return err
		}
		m := map[string]int{}
		for _, b := range buildings {
			if b.Level > 0 {
				m[b.BuildingType] = b.Level
			}
		}
		report.BuildingsJSON = toJSON(m)
	}
	if diff >= 3 {
		techs, err := e.repo.TechByPlayer(ctx, target.PlayerID)
		if err != nil {
			return err
		}
		m := map[string]int{}
		for _, pt := range techs {
			if pt.Level > 0 {
				m[pt.Technology] = pt.Level
			}
		}
		report.TechnologiesJSON = toJSON(m)
	}
	return e.repo.InsertEspionage(ctx, report)
}

// processReturns handles RETURNING fleets whose return time has passed.
func (e *Engine) processReturns(ctx context.Context, now time.Time) {
	returns, err := e.repo.FleetsReturned(ctx, now)
	if err != nil {
		log.Printf("processReturns query: %v", err)
		return
	}
	for i := range returns {
		fleet := returns[i]
		if err := e.withTx(ctx, func(te *Engine) error { return te.processReturn(ctx, &fleet) }); err != nil {
			log.Printf("Failed to process return %d: %v", fleet.ID, err)
		}
	}
}

func (e *Engine) processReturn(ctx context.Context, fleet *Fleet) error {
	ships, err := e.repo.FleetShips(ctx, fleet.ID)
	if err != nil {
		return err
	}
	for _, fs := range ships {
		if fs.Quantity > 0 {
			if err := e.repo.AddShipQuantity(ctx, fleet.OriginPlanetID, fs.ShipType, fs.Quantity); err != nil {
				return err
			}
		}
	}
	if fleet.MetalLoot > 0 || fleet.CrystalLoot > 0 || fleet.GasLoot > 0 {
		if err := e.addResources(ctx, fleet.OriginPlanetID, fleet.MetalLoot, fleet.CrystalLoot, fleet.GasLoot); err != nil {
			return err
		}
	}
	fleet.Status = StatusArrived
	fleet.ReturnTime = nil
	return e.repo.UpdateFleet(ctx, fleet)
}

// ---- read-only fleet/report getters ----

func (e *Engine) GetPlanetCombatReports(ctx context.Context, planetID int64) ([]CombatReport, error) {
	r, err := e.repo.CombatReportsByPlanet(ctx, planetID)
	if err != nil {
		return nil, err
	}
	if r == nil {
		return []CombatReport{}, nil
	}
	return r, nil
}

func (e *Engine) GetCombatReport(ctx context.Context, reportID int64) (*CombatReport, error) {
	r, err := e.repo.CombatReportByID(ctx, reportID)
	if err != nil {
		return nil, err
	}
	if r == nil {
		return nil, badReq("Combat report not found")
	}
	return r, nil
}

func (e *Engine) GetDebrisField(ctx context.Context, planetID int64) (*DebrisField, error) {
	return e.repo.DebrisByPlanet(ctx, planetID)
}

func (e *Engine) GetPlanetEspionageReports(ctx context.Context, planetID int64) ([]EspionageReport, error) {
	r, err := e.repo.EspionageByTarget(ctx, planetID)
	if err != nil {
		return nil, err
	}
	if r == nil {
		return []EspionageReport{}, nil
	}
	return r, nil
}

func anySurvivors(ships []FleetShip) bool {
	for _, s := range ships {
		if s.Quantity > 0 {
			return true
		}
	}
	return false
}

func paramFloat(params map[string]any, key string) float64 {
	v, ok := params[key]
	if !ok || v == nil {
		return 0
	}
	switch n := v.(type) {
	case float64:
		return n
	case int:
		return float64(n)
	case int64:
		return float64(n)
	case string:
		f, _ := strconv.ParseFloat(n, 64)
		return f
	}
	return 0
}
