package game

import (
	"context"
	"math"
	"time"
)

// ---- DarkMatterService ----

func (e *Engine) GetDarkMatter(ctx context.Context, playerID int64) int {
	dm, _, _ := e.repo.DarkMatter(ctx, playerID)
	return dm
}

func (e *Engine) AddDarkMatter(ctx context.Context, playerID int64, amount int) error {
	cur, _, err := e.repo.DarkMatter(ctx, playerID)
	if err != nil {
		return err
	}
	return e.repo.SetDarkMatter(ctx, playerID, cur+amount)
}

func (e *Engine) SpendDarkMatter(ctx context.Context, playerID, amount int64) (bool, error) {
	cur, ok, err := e.repo.DarkMatter(ctx, playerID)
	if err != nil {
		return false, err
	}
	if !ok || int64(cur) < amount {
		return false, nil
	}
	return true, e.repo.SetDarkMatter(ctx, playerID, cur-int(amount))
}

// calculateSpeedUpCost mirrors DarkMatterService.calculateSpeedUpCost.
func calculateSpeedUpCost(remainingSeconds int64) int {
	if remainingSeconds <= 0 {
		return 0
	}
	c := int(math.Ceil(float64(remainingSeconds) / 1800.0))
	if c < 1 {
		return 1
	}
	return c
}

// ---- EconomyService ----

func (e *Engine) checkAndDeduct(ctx context.Context, planetID int64, metal, crystal, gas float64) (bool, error) {
	p, err := e.repo.PlanetByID(ctx, planetID)
	if err != nil || p == nil {
		return false, err
	}
	if p.Metal < metal || p.Crystal < crystal || p.Gas < gas {
		return false, nil
	}
	p.Metal -= metal
	p.Crystal -= crystal
	p.Gas -= gas
	return true, e.repo.UpdatePlanetResources(ctx, p)
}

// addResources adds resources, capped at each storage capacity (used by
// EconomyService.addResources and refund — identical logic).
func (e *Engine) addResources(ctx context.Context, planetID int64, metal, crystal, gas float64) error {
	p, err := e.repo.PlanetByID(ctx, planetID)
	if err != nil || p == nil {
		return err
	}
	caps, err := e.storageCaps(ctx, p)
	if err != nil {
		return err
	}
	p.Metal = math.Min(p.Metal+metal, caps["metalStorage"])
	p.Crystal = math.Min(p.Crystal+crystal, caps["crystalStorage"])
	p.Gas = math.Min(p.Gas+gas, caps["gasStorage"])
	return e.repo.UpdatePlanetResources(ctx, p)
}

func (e *Engine) getCurrentResources(ctx context.Context, planetID int64) (map[string]any, error) {
	p, err := e.repo.PlanetByID(ctx, planetID)
	if err != nil || p == nil {
		return nil, errNotFound("Planet not found")
	}
	rates, err := e.productionRates(ctx, p)
	if err != nil {
		return nil, err
	}
	caps, err := e.storageCaps(ctx, p)
	if err != nil {
		return nil, err
	}
	return map[string]any{
		"planetId":          planetID,
		"metal":             p.Metal,
		"crystal":           p.Crystal,
		"gas":               p.Gas,
		"energy":            rates["netEnergy"],
		"metalRate":         rates["metalRate"],
		"crystalRate":       rates["crystalRate"],
		"gasRate":           rates["gasRate"],
		"metalStorage":      caps["metalStorage"],
		"crystalStorage":    caps["crystalStorage"],
		"gasStorage":        caps["gasStorage"],
		"energyConsumption": rates["energyConsumption"],
	}, nil
}

func (e *Engine) storageCaps(ctx context.Context, p *Planet) (map[string]float64, error) {
	buildings, err := e.repo.BuildingsByPlanet(ctx, p.ID)
	if err != nil {
		return nil, err
	}
	ms := firstLevel(buildings, MetalStorage)
	cs := firstLevel(buildings, CrystalStorage)
	gs := firstLevel(buildings, GasStorage)
	return map[string]float64{
		"metalStorage":   e.bal.StorageCapacity(ms),
		"crystalStorage": e.bal.StorageCapacity(cs),
		"gasStorage":     e.bal.StorageCapacity(gs),
	}, nil
}

func (e *Engine) productionRates(ctx context.Context, p *Planet) (map[string]float64, error) {
	buildings, err := e.repo.BuildingsByPlanet(ctx, p.ID)
	if err != nil {
		return nil, err
	}
	metalMine := firstLevel(buildings, MetalMine)
	crystalMine := firstLevel(buildings, CrystalMine)
	gasMine := firstLevel(buildings, GasMine)
	solarPlant := firstLevel(buildings, SolarPlant)
	fusion := firstLevel(buildings, FusionReactor)

	solarEnergy := e.bal.SolarPlantEnergy(solarPlant)
	mineConsumption := e.bal.MineEnergyConsumption(metalMine) +
		e.bal.MineEnergyConsumption(crystalMine) +
		e.bal.MineEnergyConsumption(gasMine)

	energyTech := 0
	if t, err := e.repo.TechByPlayerAndType(ctx, p.PlayerID, EnergyTech); err != nil {
		return nil, err
	} else if t != nil {
		energyTech = t.Level
	}

	fusionEnergy := 0.0
	if fusion > 0 {
		fusionEnergy = e.bal.FusionEnergy(fusion, energyTech)
	}

	totalEnergy := solarEnergy + fusionEnergy
	netEnergy := totalEnergy - mineConsumption
	isDeficit := netEnergy < 0

	return map[string]float64{
		"metalRate":         e.bal.MetalProductionPerHour(metalMine, p.Temperature, !isDeficit),
		"crystalRate":       e.bal.CrystalProductionPerHour(crystalMine, p.Temperature, !isDeficit),
		"gasRate":           e.bal.GasProductionPerHour(gasMine, p.Temperature, !isDeficit),
		"netEnergy":         netEnergy,
		"energyConsumption": mineConsumption,
		"fusionEnergy":      fusionEnergy,
	}, nil
}

// tickResources accrues production for every planet, capped at storage.
func (e *Engine) tickResources(ctx context.Context) error {
	planets, err := e.repo.AllPlanets(ctx)
	if err != nil {
		return err
	}
	now := time.Now().UTC()
	for i := range planets {
		p := &planets[i]
		hours := now.Sub(p.LastUpdated).Seconds() / 3600.0
		if hours <= 0 {
			continue
		}
		rates, err := e.productionRates(ctx, p)
		if err != nil {
			return err
		}
		caps, err := e.storageCaps(ctx, p)
		if err != nil {
			return err
		}
		p.Metal = math.Min(p.Metal+rates["metalRate"]*hours, caps["metalStorage"])
		p.Crystal = math.Min(p.Crystal+rates["crystalRate"]*hours, caps["crystalStorage"])
		newGas := p.Gas + rates["gasRate"]*hours
		p.Gas = math.Min(math.Max(0, newGas), caps["gasStorage"])
		p.LastUpdated = now
		if err := e.repo.UpdatePlanetResources(ctx, p); err != nil {
			return err
		}
	}
	return nil
}

// firstLevel returns the level of the first building of the given type, or 0.
func firstLevel(buildings []Building, bt string) int {
	for _, b := range buildings {
		if b.BuildingType == bt {
			return b.Level
		}
	}
	return 0
}
