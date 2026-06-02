package game

import (
	"context"
	"fmt"
	"math/rand"
	"time"
)

var starterGrid = []struct {
	bt    string
	level int
	pos   int
}{
	{MetalMine, 1, 0}, {CrystalMine, 1, 1}, {GasMine, 1, 2}, {SolarPlant, 1, 3},
	{MetalStorage, 1, 4}, {CrystalStorage, 1, 5}, {GasStorage, 1, 6},
	{RobotFactory, 0, 7}, {ResearchLab, 0, 8}, {Shipyard, 0, 9},
}

func (e *Engine) createPlanetWithBuildings(ctx context.Context, p *Planet) error {
	if err := e.repo.InsertPlanet(ctx, p); err != nil {
		return err
	}
	for _, s := range starterGrid {
		b := &Building{PlanetID: p.ID, BuildingType: s.bt, Level: s.level, GridPosition: s.pos}
		if err := e.repo.InsertBuilding(ctx, b); err != nil {
			return err
		}
	}
	return nil
}

func newPlanet(playerID int64, name string, galaxy, systemID, slot, temperature int) *Planet {
	now := time.Now().UTC()
	return &Planet{
		PlayerID: playerID, Name: name, Galaxy: galaxy, SystemID: systemID, Slot: slot,
		Metal: 500, Crystal: 500, Gas: 500, Energy: 0, Temperature: temperature,
		LastUpdated: now, CreatedAt: now,
	}
}

func (e *Engine) CreateStarterPlanet(ctx context.Context, playerID int64) (*Planet, error) {
	var planet *Planet
	err := e.withTx(ctx, func(te *Engine) error {
		galaxy := 1
		systemID := rand.Intn(500) + 1
		slot := rand.Intn(15) + 1
		for {
			exists, err := te.repo.PlanetExistsAt(ctx, galaxy, systemID, slot)
			if err != nil {
				return err
			}
			if !exists {
				break
			}
			systemID = rand.Intn(500) + 1
			slot = rand.Intn(15) + 1
		}
		planet = newPlanet(playerID, "Home Planet", galaxy, systemID, slot, randomTemperature(slot))
		return te.createPlanetWithBuildings(ctx, planet)
	})
	return planet, err
}

func (e *Engine) CreatePlanetAt(ctx context.Context, playerID int64, galaxy, systemID, slot int) (*Planet, error) {
	if galaxy < 1 || galaxy > 9 || systemID < 1 || systemID > 500 || slot < 1 || slot > 15 {
		return nil, badReq("Invalid coordinates")
	}
	var planet *Planet
	err := e.withTx(ctx, func(te *Engine) error {
		exists, err := te.repo.PlanetExistsAt(ctx, galaxy, systemID, slot)
		if err != nil {
			return err
		}
		if exists {
			return badReq("Planet already exists at these coordinates")
		}
		planet = newPlanet(playerID, "Colony", galaxy, systemID, slot, randomTemperature(slot))
		return te.createPlanetWithBuildings(ctx, planet)
	})
	return planet, err
}

func randomTemperature(slot int) int {
	switch slot {
	case 1, 2, 3:
		return rand.Intn(60) + 40 // [40,100)
	case 4, 5, 6:
		return rand.Intn(50) - 10 // [-10,40)
	default:
		return rand.Intn(120) - 120 // [-120,0)
	}
}

// recalculate accrues production since lastUpdated and persists the planet,
// reproducing the visibility the Java code gets from the JPA persistence
// context.
func (e *Engine) recalculate(ctx context.Context, p *Planet) error {
	buildings, err := e.repo.BuildingsByPlanet(ctx, p.ID)
	if err != nil {
		return err
	}

	var energyProduced, energyConsumed float64
	for _, b := range buildings {
		switch b.BuildingType {
		case MetalMine:
			energyConsumed += e.bal.MineEnergyConsumptionFor(MetalMine, b.Level)
		case CrystalMine:
			energyConsumed += e.bal.MineEnergyConsumptionFor(CrystalMine, b.Level)
		case GasMine:
			energyConsumed += e.bal.MineEnergyConsumptionFor(GasMine, b.Level)
		case SolarPlant:
			energyProduced += e.bal.SolarPlantEnergy(b.Level)
		}
	}

	fusion := firstLevel(buildings, FusionReactor)
	energyTech := 0
	if t, err := e.repo.TechByPlayerAndType(ctx, p.PlayerID, EnergyTech); err != nil {
		return err
	} else if t != nil {
		energyTech = t.Level
	}
	fusionEnergy := e.bal.FusionEnergy(fusion, energyTech)

	p.Energy = energyProduced + fusionEnergy - energyConsumed
	energyPositive := p.Energy >= 0

	var metalProd, crystalProd, gasProd float64
	for _, b := range buildings {
		switch b.BuildingType {
		case MetalMine:
			metalProd += e.bal.MetalProductionPerHour(b.Level, p.Temperature, energyPositive)
		case CrystalMine:
			crystalProd += e.bal.CrystalProductionPerHour(b.Level, p.Temperature, energyPositive)
		case GasMine:
			gasProd += e.bal.GasProductionPerHour(b.Level, p.Temperature, energyPositive)
		}
	}

	// ResourceService.recalculateResources: uncapped accrual + lastUpdated.
	now := time.Now().UTC()
	hours := now.Sub(p.LastUpdated).Seconds() / 3600.0
	if hours > 0 {
		p.Metal += metalProd * hours
		p.Crystal += crystalProd * hours
		p.Gas += gasProd * hours
	}
	p.LastUpdated = now
	return e.repo.UpdatePlanetResources(ctx, p)
}

func (e *Engine) getPlanetWithResources(ctx context.Context, planetID int64) (*Planet, error) {
	p, err := e.repo.PlanetByID(ctx, planetID)
	if err != nil {
		return nil, err
	}
	if p == nil {
		return nil, badReq("Planet not found")
	}
	if err := e.recalculate(ctx, p); err != nil {
		return nil, err
	}
	return p, nil
}

func (e *Engine) getPlanetDetails(ctx context.Context, planetID int64) (map[string]any, error) {
	p, err := e.getPlanetWithResources(ctx, planetID)
	if err != nil {
		return nil, err
	}
	buildings, err := e.repo.BuildingsByPlanet(ctx, planetID)
	if err != nil {
		return nil, err
	}
	if buildings == nil {
		buildings = []Building{}
	}
	return map[string]any{
		"id":          p.ID,
		"playerId":    p.PlayerID,
		"name":        p.Name,
		"coordinates": coords(p),
		"temperature": p.Temperature,
		"resources": map[string]any{
			"metal":   p.Metal,
			"crystal": p.Crystal,
			"gas":     p.Gas,
			"energy":  p.Energy,
		},
		"buildings": buildings,
	}, nil
}

func (e *Engine) getPlanetsByPlayer(ctx context.Context, playerID int64) ([]map[string]any, error) {
	planets, err := e.repo.PlanetsByPlayerOrdered(ctx, playerID)
	if err != nil {
		return nil, err
	}
	out := make([]map[string]any, 0, len(planets))
	for i := range planets {
		p := &planets[i]
		out = append(out, map[string]any{"id": p.ID, "name": p.Name, "coordinates": coords(p)})
	}
	return out, nil
}

func coords(p *Planet) string {
	return fmt.Sprintf("%d:%d:%d", p.Galaxy, p.SystemID, p.Slot)
}
