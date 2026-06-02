package game

import (
	"context"
	"math"
	"math/rand"
	"time"
)

// combatant is a mutable ship group used during simulation; it unifies the
// FleetShip / PlanetShip polymorphism the Java CombatService works with.
type combatant struct {
	shipType string
	quantity int
}

// resolveCombat is the port of CombatService.resolveCombat. It runs on the
// transaction-bound engine passed by the fleet processor.
func (e *Engine) resolveCombat(ctx context.Context, fleet *Fleet, attackerShips []FleetShip) (*CombatReport, error) {
	targetPlanetID := fleet.TargetPlanetID
	defenderShips, err := e.repo.ShipsByPlanet(ctx, targetPlanetID)
	if err != nil {
		return nil, err
	}
	defenderDefenses, err := e.repo.DefensesByPlanet(ctx, targetPlanetID)
	if err != nil {
		return nil, err
	}

	attacker := make([]*combatant, 0, len(attackerShips))
	attackerBefore := map[string]int{}
	for _, fs := range attackerShips {
		attacker = append(attacker, &combatant{fs.ShipType, fs.Quantity})
		attackerBefore[fs.ShipType] = fs.Quantity
	}
	defender := make([]*combatant, 0, len(defenderShips))
	defenderBefore := map[string]int{}
	for _, ps := range defenderShips {
		defender = append(defender, &combatant{ps.ShipType, ps.Quantity})
		defenderBefore[ps.ShipType] = ps.Quantity
	}

	// Pre-combat: planetary defenses fire once each at the incoming fleet.
	for i := range defenderDefenses {
		pd := &defenderDefenses[i]
		if pd.Quantity <= 0 || pd.DefenseType == SmallShield || pd.DefenseType == LargeShield {
			continue
		}
		for n := 0; n < pd.Quantity; n++ {
			target := pickRandomTarget(attacker)
			if target == "" {
				break
			}
			shield := e.bal.ShipShield(target)
			damage := e.bal.DefenseAttack(pd.DefenseType) - shield
			if damage > 0 {
				hull := e.bal.ShipHull(target)
				destroyed := damage / hull
				if destroyed < 1 {
					destroyed = 1
				}
				for _, c := range attacker {
					if c.shipType == target && c.quantity > 0 {
						actual := min(destroyed, c.quantity)
						c.quantity -= actual
						break
					}
				}
			}
		}
	}

	var debrisMetal, debrisCrystal float64
	roundData := []map[string]any{}
	const maxRounds = 6

	for round := 0; round < maxRounds; round++ {
		attacker = removeZero(attacker)
		defender = removeZero(defender)
		if len(attacker) == 0 || len(defender) == 0 {
			break
		}
		roundAttackerLosses := map[string]int{}
		roundDefenderLosses := map[string]int{}

		for _, c := range snapshot(attacker) {
			if c.quantity <= 0 {
				continue
			}
			e.fireShipGroup(c.shipType, c.quantity, defender, roundDefenderLosses)
		}
		for _, c := range snapshot(defender) {
			if c.quantity <= 0 {
				continue
			}
			e.fireShipGroup(c.shipType, c.quantity, attacker, roundAttackerLosses)
		}

		attacker = removeZero(attacker)
		defender = removeZero(defender)

		roundData = append(roundData, map[string]any{
			"round":          round + 1,
			"attackerLosses": roundAttackerLosses,
			"defenderLosses": roundDefenderLosses,
		})
	}

	attackerLost := map[string]int{}
	defenderLost := map[string]int{}
	for _, rd := range roundData {
		mergeLosses(attackerLost, rd["attackerLosses"].(map[string]int))
		mergeLosses(defenderLost, rd["defenderLosses"].(map[string]int))
	}
	for t, count := range attackerLost {
		debrisMetal += e.bal.ShipMetalCost(t) * 0.3 * float64(count)
		debrisCrystal += e.bal.ShipCrystalCost(t) * 0.3 * float64(count)
	}
	for t, count := range defenderLost {
		debrisMetal += e.bal.ShipMetalCost(t) * 0.3 * float64(count)
		debrisCrystal += e.bal.ShipCrystalCost(t) * 0.3 * float64(count)
	}

	attackerDefeated := len(attacker) == 0
	defenderDefeated := len(defender) == 0

	survivalRate := 0.5
	if attackerDefeated {
		survivalRate = 1.0
	}
	for i := range defenderDefenses {
		pd := &defenderDefenses[i]
		if pd.Quantity > 0 {
			destroyed := int(math.Round(float64(pd.Quantity) * (1 - survivalRate)))
			if destroyed > 0 {
				debrisMetal += e.bal.DefenseMetalCost(pd.DefenseType) * 0.3 * float64(destroyed)
				debrisCrystal += e.bal.DefenseCrystalCost(pd.DefenseType) * 0.3 * float64(destroyed)
				pd.Quantity -= destroyed
			}
		}
	}

	result := "DRAW"
	var lootedMetal, lootedCrystal, lootedGas float64
	targetPlanet, err := e.repo.PlanetByID(ctx, targetPlanetID)
	if err != nil {
		return nil, err
	}

	if defenderDefeated && !attackerDefeated {
		result = "ATTACKER_WIN"
		if targetPlanet != nil {
			var totalCargo int64
			for _, c := range attacker {
				totalCargo += int64(e.bal.ShipCargo(c.shipType)) * int64(c.quantity)
			}
			lootedMetal = math.Min(targetPlanet.Metal, float64(totalCargo/3))
			lootedCrystal = math.Min(targetPlanet.Crystal, (float64(totalCargo)-lootedMetal)/2)
			lootedGas = math.Min(targetPlanet.Gas, float64(totalCargo)-lootedMetal-lootedCrystal)
			targetPlanet.Metal -= lootedMetal
			targetPlanet.Crystal -= lootedCrystal
			targetPlanet.Gas -= lootedGas
			if err := e.repo.UpdatePlanetResources(ctx, targetPlanet); err != nil {
				return nil, err
			}
		}
	} else if attackerDefeated && !defenderDefeated {
		result = "DEFENDER_WIN"
	}

	fleet.MetalLoot = lootedMetal
	fleet.CrystalLoot = lootedCrystal
	fleet.GasLoot = lootedGas

	var defenderPlayerID int64
	if targetPlanet != nil {
		defenderPlayerID = targetPlanet.PlayerID
	}

	// Persist defender ships: survivors only.
	if err := e.repo.DeletePlanetShips(ctx, targetPlanetID); err != nil {
		return nil, err
	}
	for _, c := range defender {
		if c.quantity > 0 {
			if err := e.repo.UpsertShipQuantity(ctx, targetPlanetID, c.shipType, c.quantity); err != nil {
				return nil, err
			}
		}
	}
	// Persist defense quantities.
	for i := range defenderDefenses {
		if err := e.repo.SetDefenseQuantity(ctx, defenderDefenses[i].ID, defenderDefenses[i].Quantity); err != nil {
			return nil, err
		}
	}
	// Persist attacker survivors back onto the fleet.
	if err := e.repo.DeleteFleetShips(ctx, fleet.ID); err != nil {
		return nil, err
	}
	for _, c := range attacker {
		if c.quantity > 0 {
			if err := e.repo.InsertFleetShip(ctx, &FleetShip{FleetID: fleet.ID, ShipType: c.shipType, Quantity: c.quantity}); err != nil {
				return nil, err
			}
		}
	}

	if debrisMetal > 0 || debrisCrystal > 0 {
		if err := e.repo.AddDebris(ctx, targetPlanetID, debrisMetal, debrisCrystal); err != nil {
			return nil, err
		}
	}

	report := &CombatReport{
		AttackerID:          fleet.PlayerID,
		DefenderID:          defenderPlayerID,
		AttackerPlanetID:    fleet.OriginPlanetID,
		DefenderPlanetID:    targetPlanetID,
		Timestamp:           time.Now().UTC(),
		Result:              result,
		AttackerShipsBefore: toJSON(attackerBefore),
		DefenderShipsBefore: toJSON(defenderBefore),
		AttackerShipsLost:   toJSON(attackerLost),
		DefenderShipsLost:   toJSON(defenderLost),
		DebrisMetal:         debrisMetal,
		DebrisCrystal:       debrisCrystal,
		ResourcesLooted:     toJSON(map[string]float64{"metal": lootedMetal, "crystal": lootedCrystal, "gas": lootedGas}),
		Rounds:              toJSON(roundData),
	}
	if err := e.repo.InsertCombatReport(ctx, report); err != nil {
		return nil, err
	}

	if result == "ATTACKER_WIN" {
		if err := e.processQuestEvent(ctx, QuestEvent{fleet.PlayerID, "BATTLE_WON", "", 1}); err != nil {
			return nil, err
		}
	}
	return report, nil
}

func (e *Engine) fireShipGroup(firerType string, quantity int, targets []*combatant, losses map[string]int) {
	rapidFire := e.bal.RapidFire()
	rfForFirer := rapidFire[firerType]
	attack := e.bal.ShipAttack(firerType)

	for n := 0; n < quantity; n++ {
		canFire := true
		for canFire {
			targetType := pickRandomTarget(targets)
			if targetType == "" {
				break
			}
			shield := e.bal.ShipShield(targetType)
			damage := attack - shield
			if damage > 0 {
				if destroyShips(e, targets, targetType, damage, losses) {
					rfValue := rfForFirer[targetType]
					if rfValue > 0 {
						roll := rand.Intn(rfValue) + 1
						canFire = roll > 1
					} else {
						canFire = false
					}
				} else {
					canFire = false
				}
			} else {
				canFire = false
			}
		}
	}
}

func pickRandomTarget(targets []*combatant) string {
	available := make([]string, 0, len(targets))
	for _, c := range targets {
		if c.quantity > 0 {
			available = append(available, c.shipType)
		}
	}
	if len(available) == 0 {
		return ""
	}
	return available[rand.Intn(len(available))]
}

func destroyShips(e *Engine, targets []*combatant, targetType string, damage int, losses map[string]int) bool {
	hull := e.bal.ShipHull(targetType)
	shipsDestroyed := damage / hull
	if shipsDestroyed < 1 {
		shipsDestroyed = 1
	}
	for _, c := range targets {
		if c.shipType == targetType && c.quantity > 0 {
			actual := min(shipsDestroyed, c.quantity)
			c.quantity -= actual
			losses[targetType] += actual
			return true
		}
	}
	return false
}

func removeZero(list []*combatant) []*combatant {
	out := list[:0]
	for _, c := range list {
		if c.quantity > 0 {
			out = append(out, c)
		}
	}
	return out
}

func snapshot(list []*combatant) []*combatant {
	cp := make([]*combatant, len(list))
	copy(cp, list)
	return cp
}

func mergeLosses(total, round map[string]int) {
	for k, v := range round {
		total[k] += v
	}
}
