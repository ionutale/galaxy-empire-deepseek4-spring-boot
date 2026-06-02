package game

import "math"

// Balancer is the Go port of GameBalancer — every formula matches the Java
// source so costs, times, production and combat stats are identical.
type Balancer struct {
	speed float64
}

func NewBalancer(speed float64) *Balancer { return &Balancer{speed: speed} }

func (b *Balancer) MetalCost(t string, level int) float64 {
	switch t {
	case MetalMine:
		return math.Floor(60 * math.Pow(1.5, float64(level-1)))
	case CrystalMine:
		return math.Floor(48 * math.Pow(1.6, float64(level-1)))
	case GasMine:
		return math.Floor(225 * math.Pow(1.5, float64(level-1)))
	case SolarPlant:
		return math.Floor(75 * math.Pow(1.5, float64(level-1)))
	case MetalStorage, CrystalStorage, GasStorage:
		return math.Floor(200 * math.Pow(2, float64(level-1)))
	case RobotFactory:
		return math.Floor(400 * math.Pow(2, float64(level-1)))
	case ResearchLab:
		return math.Floor(200 * math.Pow(1.5, float64(level-1)))
	case Shipyard:
		return math.Floor(400 * math.Pow(2, float64(level-1)))
	default:
		return math.Floor(200 * math.Pow(1.5, float64(level-1)))
	}
}

func (b *Balancer) CrystalCost(t string, level int) float64 {
	switch t {
	case MetalMine:
		return math.Floor(15 * math.Pow(1.5, float64(level-1)))
	case CrystalMine:
		return math.Floor(24 * math.Pow(1.6, float64(level-1)))
	case GasMine:
		return math.Floor(75 * math.Pow(1.5, float64(level-1)))
	case SolarPlant:
		return math.Floor(30 * math.Pow(1.5, float64(level-1)))
	case MetalStorage, CrystalStorage, GasStorage:
		return math.Floor(200 * math.Pow(2, float64(level-1)))
	case RobotFactory:
		return math.Floor(200 * math.Pow(2, float64(level-1)))
	case ResearchLab:
		return math.Floor(400 * math.Pow(1.5, float64(level-1)))
	case Shipyard:
		return math.Floor(200 * math.Pow(2, float64(level-1)))
	default:
		return math.Floor(200 * math.Pow(1.5, float64(level-1)))
	}
}

func (b *Balancer) GasCost(t string, level int) float64 {
	switch t {
	case MetalMine, CrystalMine, GasMine:
		return 0
	case SolarPlant:
		return math.Floor(15 * math.Pow(1.5, float64(level-1)))
	case ResearchLab:
		return math.Floor(200 * math.Pow(1.5, float64(level-1)))
	default:
		return 0
	}
}

func (b *Balancer) ConstructionTimeSeconds(t string, level, robotFactoryLevel int) int {
	var baseTime float64
	switch t {
	case MetalMine:
		baseTime = 3600
	case CrystalMine:
		baseTime = 4800
	case GasMine:
		baseTime = 6000
	case SolarPlant:
		baseTime = 2400
	case MetalStorage, CrystalStorage, GasStorage:
		baseTime = 1200
	case RobotFactory:
		baseTime = 4800
	case ResearchLab:
		baseTime = 7200
	case Shipyard:
		baseTime = 9600
	default:
		baseTime = 3600
	}
	tm := baseTime * math.Pow(1.5, float64(level-1)) / (1 + float64(robotFactoryLevel))
	return int(math.Max(math.Round(tm/b.speed), 10))
}

func (b *Balancer) MetalProductionPerHour(level, temperature int, energyPositive bool) float64 {
	base := 30 * float64(level) * math.Pow(1.1, float64(level))
	if !energyPositive {
		base *= 0.5
	}
	return base * b.speed
}

func (b *Balancer) CrystalProductionPerHour(level, temperature int, energyPositive bool) float64 {
	base := 20 * float64(level) * math.Pow(1.1, float64(level))
	if !energyPositive {
		base *= 0.5
	}
	return base * b.speed
}

func (b *Balancer) GasProductionPerHour(level, temperature int, energyPositive bool) float64 {
	tempFactor := 1.0 + (100-float64(temperature))/200.0
	base := 10 * float64(level) * math.Pow(1.1, float64(level)) * tempFactor
	if !energyPositive {
		base *= 0.5
	}
	return base * b.speed
}

func (b *Balancer) SolarPlantEnergy(level int) float64 {
	return 20 * float64(level) * math.Pow(1.1, float64(level))
}

func (b *Balancer) FusionEnergy(level, energyTechLevel int) float64 {
	baseMultiplier := 1.05 + 0.01*float64(energyTechLevel)
	return 30 * float64(level) * math.Pow(baseMultiplier, float64(level))
}

func (b *Balancer) MineEnergyConsumption(level int) float64 {
	return 10 * float64(level) * math.Pow(1.1, float64(level))
}

func (b *Balancer) MineEnergyConsumptionFor(t string, level int) float64 {
	switch t {
	case MetalMine, CrystalMine, GasMine:
		return b.MineEnergyConsumption(level)
	default:
		return 0
	}
}

func (b *Balancer) StorageCapacity(level int) float64 {
	return 5000 * math.Pow(2, float64(level-1))
}

func (b *Balancer) TechnologyMetalCost(tech string, level int) float64 {
	var base float64
	switch tech {
	case EnergyTech:
		base = 200
	case LaserTech:
		base = 100
	case IonTech:
		base = 250
	case PlasmaTech:
		base = 500
	case CombustionDrive:
		base = 200
	case ImpulseDrive:
		base = 1000
	case HyperspaceDrive:
		base = 2000
	case WeaponTech:
		base = 400
	case ShieldingTech:
		base = 200
	case ArmorTech:
		base = 200
	case ComputerTech:
		base = 100
	case EspionageTech:
		base = 200
	case GravitonTech:
		base = 5000
	}
	return math.Floor(base*math.Pow(2, float64(level))) * b.speed
}

func (b *Balancer) TechnologyCrystalCost(tech string, level int) float64 {
	var base float64
	switch tech {
	case EnergyTech:
		base = 100
	case LaserTech:
		base = 50
	case IonTech:
		base = 150
	case PlasmaTech:
		base = 300
	case CombustionDrive:
		base = 100
	case ImpulseDrive:
		base = 500
	case HyperspaceDrive:
		base = 1000
	case WeaponTech:
		base = 200
	case ShieldingTech:
		base = 400
	case ArmorTech:
		base = 100
	case ComputerTech:
		base = 200
	case EspionageTech:
		base = 400
	case GravitonTech:
		base = 5000
	}
	return math.Floor(base*math.Pow(2, float64(level))) * b.speed
}

func (b *Balancer) TechnologyGasCost(tech string, level int) float64 {
	var base float64
	switch tech {
	case PlasmaTech, ImpulseDrive, EspionageTech:
		base = 100
	case HyperspaceDrive:
		base = 500
	case GravitonTech:
		base = 1000
	default:
		base = 0
	}
	if base == 0 {
		return 0
	}
	return math.Floor(base*math.Pow(2, float64(level))) * b.speed
}

func (b *Balancer) ResearchTimeSeconds(tech string, level int, researchLabLevel float64) int {
	var base float64
	switch tech {
	case EnergyTech:
		base = 600
	case LaserTech:
		base = 400
	case IonTech:
		base = 800
	case PlasmaTech:
		base = 2000
	case CombustionDrive:
		base = 600
	case ImpulseDrive:
		base = 1800
	case HyperspaceDrive:
		base = 3600
	case WeaponTech:
		base = 1200
	case ShieldingTech:
		base = 1200
	case ArmorTech:
		base = 600
	case ComputerTech:
		base = 400
	case EspionageTech:
		base = 1200
	case GravitonTech:
		base = 14400
	}
	tm := base * math.Pow(2, float64(level)) / (1 + researchLabLevel)
	return int(math.Ceil(tm / b.speed))
}

func (b *Balancer) MeetsPrerequisites(tech string, levels map[string]int) bool {
	get := func(t string) int { return levels[t] }
	switch tech {
	case PlasmaTech:
		return get(EnergyTech) >= 5 && get(LaserTech) >= 5
	case CombustionDrive:
		return get(EnergyTech) >= 1
	case ImpulseDrive:
		return get(CombustionDrive) >= 5 && get(EnergyTech) >= 2
	case HyperspaceDrive:
		return get(ImpulseDrive) >= 5 && get(EnergyTech) >= 3
	case WeaponTech:
		return get(LaserTech) >= 3
	case ShieldingTech:
		return get(EnergyTech) >= 3
	case EspionageTech:
		return get(ComputerTech) >= 3
	case GravitonTech:
		return get(EnergyTech) >= 10 && get(PlasmaTech) >= 5
	default:
		return true
	}
}

func (b *Balancer) ShipMetalCost(t string) float64 {
	var v float64
	switch t {
	case LightFighter:
		v = 500
	case HeavyFighter:
		v = 2500
	case Cruiser:
		v = 5000
	case Battleship:
		v = 15000
	case SmallCargo:
		v = 1000
	case LargeCargo:
		v = 3000
	case ColonyShip:
		v = 5000
	case Recycler:
		v = 2000
	case EspionageProbe:
		v = 100
	}
	return v * b.speed
}

func (b *Balancer) ShipCrystalCost(t string) float64 {
	var v float64
	switch t {
	case LightFighter:
		v = 100
	case HeavyFighter:
		v = 500
	case Cruiser:
		v = 2000
	case Battleship:
		v = 5000
	case SmallCargo:
		v = 500
	case LargeCargo:
		v = 1500
	case ColonyShip:
		v = 2500
	case Recycler:
		v = 1000
	case EspionageProbe:
		v = 50
	}
	return v * b.speed
}

func (b *Balancer) ShipGasCost(t string) float64 {
	var v float64
	switch t {
	case Cruiser:
		v = 1000
	case Battleship:
		v = 3000
	case ColonyShip:
		v = 5000
	case Recycler:
		v = 500
	default:
		v = 0
	}
	return v * b.speed
}

func (b *Balancer) ShipBuildTimeSeconds(t string, shipyardLevel, naniteLevel float64) int {
	var base float64
	switch t {
	case LightFighter:
		base = 120
	case HeavyFighter:
		base = 360
	case Cruiser:
		base = 1200
	case Battleship:
		base = 3600
	case SmallCargo:
		base = 240
	case LargeCargo:
		base = 600
	case ColonyShip:
		base = 2400
	case Recycler:
		base = 600
	case EspionageProbe:
		base = 30
	}
	return int(math.Ceil(base * b.speed / (1 + shipyardLevel + naniteLevel)))
}

func (b *Balancer) RequiredShipyardLevel(t string) int {
	switch t {
	case LightFighter, EspionageProbe:
		return 1
	case SmallCargo:
		return 2
	case Recycler, HeavyFighter:
		return 3
	case LargeCargo:
		return 4
	case Cruiser, ColonyShip:
		return 5
	case Battleship:
		return 7
	}
	return 1
}

func (b *Balancer) ShipAttack(t string) int {
	switch t {
	case LightFighter:
		return 50
	case HeavyFighter:
		return 150
	case Cruiser:
		return 400
	case Battleship:
		return 1000
	case SmallCargo, LargeCargo:
		return 5
	case ColonyShip:
		return 50
	case Recycler:
		return 1
	case EspionageProbe:
		return 0
	}
	return 0
}

func (b *Balancer) ShipShield(t string) int {
	switch t {
	case LightFighter:
		return 10
	case HeavyFighter:
		return 25
	case Cruiser:
		return 50
	case Battleship:
		return 200
	case SmallCargo:
		return 10
	case LargeCargo:
		return 25
	case ColonyShip:
		return 100
	case Recycler:
		return 10
	case EspionageProbe:
		return 0
	}
	return 0
}

func (b *Balancer) ShipHull(t string) int {
	switch t {
	case LightFighter:
		return 400
	case HeavyFighter:
		return 1000
	case Cruiser:
		return 2700
	case Battleship:
		return 6000
	case SmallCargo:
		return 400
	case LargeCargo:
		return 1200
	case ColonyShip:
		return 3000
	case Recycler:
		return 1600
	case EspionageProbe:
		return 100
	}
	return 1
}

func (b *Balancer) ShipSpeed(t string) int {
	switch t {
	case LightFighter:
		return 12500
	case HeavyFighter:
		return 10000
	case Cruiser:
		return 15000
	case Battleship:
		return 10000
	case SmallCargo:
		return 5000
	case LargeCargo:
		return 7500
	case ColonyShip:
		return 2500
	case Recycler:
		return 2000
	case EspionageProbe:
		return 10000000
	}
	return 100
}

func (b *Balancer) ShipCargo(t string) int {
	switch t {
	case LightFighter:
		return 50
	case HeavyFighter:
		return 100
	case Cruiser:
		return 800
	case Battleship:
		return 1500
	case SmallCargo:
		return 5000
	case LargeCargo:
		return 25000
	case ColonyShip:
		return 7500
	case Recycler:
		return 20000
	case EspionageProbe:
		return 0
	}
	return 0
}

// RapidFire returns the firer -> target -> shots map.
func (b *Balancer) RapidFire() map[string]map[string]int {
	return map[string]map[string]int{
		LightFighter: {EspionageProbe: 5},
		HeavyFighter: {EspionageProbe: 5, SmallCargo: 3},
		Cruiser:      {EspionageProbe: 5, LightFighter: 3},
		Battleship:   {EspionageProbe: 5, HeavyFighter: 3, Cruiser: 2, SmallCargo: 5},
	}
}

func (b *Balancer) TravelTimeSeconds(distance int64) int {
	v := int(float64(distance) / b.speed)
	if v < 10 {
		return 10
	}
	return v
}

func (b *Balancer) DefenseAttack(t string) int {
	switch t {
	case RocketLauncher:
		return 80
	case LightLaser:
		return 100
	case HeavyLaser:
		return 250
	case IonCannon:
		return 150
	case PlasmaTurret:
		return 3000
	case SmallShield, LargeShield:
		return 1
	}
	return 0
}

func (b *Balancer) DefenseShield(t string) int {
	switch t {
	case RocketLauncher:
		return 20
	case LightLaser:
		return 25
	case HeavyLaser:
		return 100
	case IonCannon:
		return 500
	case PlasmaTurret:
		return 300
	case SmallShield:
		return 2000
	case LargeShield:
		return 10000
	}
	return 0
}

func (b *Balancer) DefenseHull(t string) int {
	switch t {
	case RocketLauncher, LightLaser:
		return 200
	case HeavyLaser, IonCannon:
		return 800
	case PlasmaTurret:
		return 2000
	case SmallShield:
		return 2000
	case LargeShield:
		return 10000
	}
	return 1
}

func (b *Balancer) DefenseMetalCost(t string) float64 {
	switch t {
	case RocketLauncher:
		return 2000
	case LightLaser:
		return 1500
	case HeavyLaser:
		return 6000
	case IonCannon:
		return 2000
	case PlasmaTurret:
		return 50000
	case SmallShield:
		return 10000
	case LargeShield:
		return 50000
	}
	return 0
}

func (b *Balancer) DefenseCrystalCost(t string) float64 {
	switch t {
	case RocketLauncher:
		return 0
	case LightLaser:
		return 500
	case HeavyLaser:
		return 2000
	case IonCannon:
		return 6000
	case PlasmaTurret:
		return 50000
	case SmallShield:
		return 10000
	case LargeShield:
		return 50000
	}
	return 0
}

func (b *Balancer) DefenseGasCost(t string) float64 {
	if t == PlasmaTurret {
		return 30000
	}
	return 0
}

func (b *Balancer) DefenseBuildTimeSeconds(t string, shipyardLevel, naniteLevel float64) int {
	var base float64
	switch t {
	case RocketLauncher:
		base = 300
	case LightLaser:
		base = 240
	case HeavyLaser:
		base = 600
	case IonCannon:
		base = 1200
	case PlasmaTurret:
		base = 7200
	case SmallShield:
		base = 1200
	case LargeShield:
		base = 7200
	}
	return int(math.Ceil(base * 1.0 / (1 + shipyardLevel + naniteLevel)))
}

func (b *Balancer) RequiredShipyardLevelForDefense(t string) int {
	switch t {
	case RocketLauncher:
		return 1
	case LightLaser:
		return 2
	case SmallShield:
		return 3
	case HeavyLaser:
		return 4
	case IonCannon:
		return 5
	case LargeShield:
		return 6
	case PlasmaTurret:
		return 8
	}
	return 1
}
