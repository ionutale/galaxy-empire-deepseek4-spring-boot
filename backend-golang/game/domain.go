// Package game is the Go port of the Spring game-service. It keeps the same
// domain model, balance formulas, REST surface and STOMP push behaviour so it
// is a drop-in replacement against the same Postgres database and frontend.
package game

import "time"

// ---- enum value sets (stored as VARCHAR, matching the Java @Enumerated STRING) ----

const (
	// BuildingType
	MetalMine      = "METAL_MINE"
	CrystalMine    = "CRYSTAL_MINE"
	GasMine        = "GAS_MINE"
	SolarPlant     = "SOLAR_PLANT"
	FusionReactor  = "FUSION_REACTOR"
	MetalStorage   = "METAL_STORAGE"
	CrystalStorage = "CRYSTAL_STORAGE"
	GasStorage     = "GAS_STORAGE"
	RobotFactory   = "ROBOT_FACTORY"
	Shipyard       = "SHIPYARD"
	ResearchLab    = "RESEARCH_LAB"
	Radar          = "RADAR"
	AllianceDepot  = "ALLIANCE_DEPOT"
	NaniteFactory  = "NANITE_FACTORY"
	Terraformer    = "TERRAFORMER"
	Silo           = "SILO"
	MoonBase       = "MOON_BASE"
)

// BuildingTypes preserves the declaration order of the Java enum.
var BuildingTypes = []string{
	MetalMine, CrystalMine, GasMine, SolarPlant, FusionReactor,
	MetalStorage, CrystalStorage, GasStorage, RobotFactory, Shipyard,
	ResearchLab, Radar, AllianceDepot, NaniteFactory, Terraformer, Silo, MoonBase,
}

const (
	// ShipType
	LightFighter   = "LIGHT_FIGHTER"
	HeavyFighter   = "HEAVY_FIGHTER"
	Cruiser        = "CRUISER"
	Battleship     = "BATTLESHIP"
	SmallCargo     = "SMALL_CARGO"
	LargeCargo     = "LARGE_CARGO"
	ColonyShip     = "COLONY_SHIP"
	Recycler       = "RECYCLER"
	EspionageProbe = "ESPIONAGE_PROBE"
)

var ShipTypes = []string{
	LightFighter, HeavyFighter, Cruiser, Battleship, SmallCargo,
	LargeCargo, ColonyShip, Recycler, EspionageProbe,
}

const (
	// DefenseType
	RocketLauncher = "ROCKET_LAUNCHER"
	LightLaser     = "LIGHT_LASER"
	HeavyLaser     = "HEAVY_LASER"
	IonCannon      = "ION_CANNON"
	PlasmaTurret   = "PLASMA_TURRET"
	SmallShield    = "SMALL_SHIELD"
	LargeShield    = "LARGE_SHIELD"
)

var DefenseTypes = []string{
	RocketLauncher, LightLaser, HeavyLaser, IonCannon, PlasmaTurret, SmallShield, LargeShield,
}

const (
	// Technology
	EnergyTech      = "ENERGY_TECH"
	LaserTech       = "LASER_TECH"
	IonTech         = "ION_TECH"
	PlasmaTech      = "PLASMA_TECH"
	CombustionDrive = "COMBUSTION_DRIVE"
	ImpulseDrive    = "IMPULSE_DRIVE"
	HyperspaceDrive = "HYPERSPACE_DRIVE"
	WeaponTech      = "WEAPON_TECH"
	ShieldingTech   = "SHIELDING_TECH"
	ArmorTech       = "ARMOR_TECH"
	ComputerTech    = "COMPUTER_TECH"
	EspionageTech   = "ESPIONAGE_TECH"
	GravitonTech    = "GRAVITON_TECH"
)

var Technologies = []string{
	EnergyTech, LaserTech, IonTech, PlasmaTech, CombustionDrive, ImpulseDrive,
	HyperspaceDrive, WeaponTech, ShieldingTech, ArmorTech, ComputerTech,
	EspionageTech, GravitonTech,
}

const (
	// FleetMission
	MissionAttack    = "ATTACK"
	MissionDeploy    = "DEPLOY"
	MissionTransport = "TRANSPORT"
	MissionColonize  = "COLONIZE"
	MissionSpy       = "SPY"
	MissionRecycle   = "RECYCLE"

	// FleetStatus
	StatusEnRoute   = "EN_ROUTE"
	StatusArrived   = "ARRIVED"
	StatusReturning = "RETURNING"
	StatusRecalled  = "RECALLED"
)

// ---- entities (JSON tags match the Java Jackson output the frontend consumes) ----

type Planet struct {
	ID          int64     `json:"id"`
	PlayerID    int64     `json:"playerId"`
	Name        string    `json:"name"`
	Galaxy      int       `json:"galaxy"`
	SystemID    int       `json:"systemId"`
	Slot        int       `json:"slot"`
	Metal       float64   `json:"metal"`
	Crystal     float64   `json:"crystal"`
	Gas         float64   `json:"gas"`
	Energy      float64   `json:"energy"`
	Temperature int       `json:"temperature"`
	LastUpdated time.Time `json:"lastUpdated"`
	CreatedAt   time.Time `json:"createdAt"`
}

type Building struct {
	ID           int64  `json:"id"`
	PlanetID     int64  `json:"planetId"`
	BuildingType string `json:"buildingType"`
	Level        int    `json:"level"`
	GridPosition int    `json:"gridPosition"`
}

type ConstructionQueue struct {
	ID           int64     `json:"id"`
	PlanetID     int64     `json:"planetId"`
	BuildingType string    `json:"buildingType"`
	TargetLevel  int       `json:"targetLevel"`
	MetalCost    float64   `json:"metalCost"`
	CrystalCost  float64   `json:"crystalCost"`
	GasCost      float64   `json:"gasCost"`
	StartedAt    time.Time `json:"startedAt"`
	CompletesAt  time.Time `json:"completesAt"`
	Completed    bool      `json:"completed"`
}

type PlayerTechnology struct {
	ID         int64  `json:"id"`
	PlayerID   int64  `json:"playerId"`
	Technology string `json:"technology"`
	Level      int    `json:"level"`
}

type ResearchQueue struct {
	ID          int64     `json:"id"`
	PlayerID    int64     `json:"playerId"`
	Technology  string    `json:"technology"`
	TargetLevel int       `json:"targetLevel"`
	MetalCost   float64   `json:"metalCost"`
	CrystalCost float64   `json:"crystalCost"`
	GasCost     float64   `json:"gasCost"`
	StartedAt   time.Time `json:"startedAt"`
	CompletesAt time.Time `json:"completesAt"`
	Completed   bool      `json:"completed"`
}

type PlanetShip struct {
	ID       int64  `json:"id"`
	PlanetID int64  `json:"planetId"`
	ShipType string `json:"shipType"`
	Quantity int    `json:"quantity"`
}

type PlanetDefense struct {
	ID          int64  `json:"id"`
	PlanetID    int64  `json:"planetId"`
	DefenseType string `json:"defenseType"`
	Quantity    int    `json:"quantity"`
}

type ShipyardQueue struct {
	ID            int64     `json:"id"`
	PlanetID      int64     `json:"planetId"`
	ShipType      *string   `json:"shipType"`
	DefenseType   *string   `json:"defenseType"`
	Quantity      int       `json:"quantity"`
	BuiltQuantity int       `json:"builtQuantity"`
	MetalCost     float64   `json:"metalCost"`
	CrystalCost   float64   `json:"crystalCost"`
	GasCost       float64   `json:"gasCost"`
	StartedAt     time.Time `json:"startedAt"`
	CompletesAt   time.Time `json:"completesAt"`
	Completed     bool      `json:"completed"`
}

type Fleet struct {
	ID             int64      `json:"id"`
	OriginPlanetID int64      `json:"originPlanetId"`
	TargetPlanetID int64      `json:"targetPlanetId"`
	PlayerID       int64      `json:"playerId"`
	Mission        string     `json:"mission"`
	DepartureTime  time.Time  `json:"departureTime"`
	ArrivalTime    time.Time  `json:"arrivalTime"`
	ReturnTime     *time.Time `json:"returnTime"`
	Status         string     `json:"status"`
	MetalLoot      float64    `json:"metalLoot"`
	CrystalLoot    float64    `json:"crystalLoot"`
	GasLoot        float64    `json:"gasLoot"`
}

type FleetShip struct {
	ID       int64  `json:"id"`
	FleetID  int64  `json:"fleetId"`
	ShipType string `json:"shipType"`
	Quantity int    `json:"quantity"`
}

type CombatReport struct {
	ID                  int64     `json:"id"`
	AttackerID          int64     `json:"attackerId"`
	DefenderID          int64     `json:"defenderId"`
	AttackerPlanetID    int64     `json:"attackerPlanetId"`
	DefenderPlanetID    int64     `json:"defenderPlanetId"`
	Timestamp           time.Time `json:"timestamp"`
	Result              string    `json:"result"`
	AttackerShipsBefore string    `json:"attackerShipsBefore"`
	DefenderShipsBefore string    `json:"defenderShipsBefore"`
	AttackerShipsLost   string    `json:"attackerShipsLost"`
	DefenderShipsLost   string    `json:"defenderShipsLost"`
	DebrisMetal         float64   `json:"debrisMetal"`
	DebrisCrystal       float64   `json:"debrisCrystal"`
	ResourcesLooted     string    `json:"resourcesLooted"`
	Rounds              string    `json:"rounds"`
}

type DebrisField struct {
	ID       int64   `json:"id"`
	PlanetID int64   `json:"planetId"`
	Metal    float64 `json:"metal"`
	Crystal  float64 `json:"crystal"`
}

type EspionageReport struct {
	ID               int64     `json:"id"`
	AttackerID       int64     `json:"attackerId"`
	DefenderID       int64     `json:"defenderId"`
	TargetPlanetID   int64     `json:"targetPlanetId"`
	Timestamp        time.Time `json:"timestamp"`
	ResourcesJSON    string    `json:"resourcesJson"`
	ShipsJSON        string    `json:"shipsJson"`
	BuildingsJSON    string    `json:"buildingsJson"`
	TechnologiesJSON string    `json:"technologiesJson"`
	DefensesJSON     string    `json:"defensesJson"`
}

type QuestDefinition struct {
	ID               int64
	QuestType        string
	Category         string
	RequirementType  string
	RequirementValue int
	RewardType       string
	RewardAmount     int
	Title            string
	Description      string
	Icon             string
	SortOrder        int
	Daily            bool
}

type QuestProgress struct {
	ID                int64
	PlayerID          int64
	QuestDefinitionID int64
	Progress          int
	Completed         bool
	CompletedAt       *time.Time
	Claimed           bool
	LastResetDate     *time.Time
}

// QuestEvent mirrors the Java record used to drive quest progress.
type QuestEvent struct {
	PlayerID        int64
	RequirementType string
	Target          string
	Value           int
}
