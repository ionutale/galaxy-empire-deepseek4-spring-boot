export interface AuthResponse {
  token: string;
  playerId: number;
  username: string;
}

export interface Planet {
  id: number;
  playerId: number;
  name: string;
  coordinates: string;
  temperature: number;
  resources: {
    metal: number;
    crystal: number;
    gas: number;
    energy: number;
  };
  buildings: Building[];
}

export interface Building {
  id: number;
  planetId: number;
  buildingType: string;
  level: number;
  gridPosition: number;
}

export interface ConstructionQueue {
  id: number;
  planetId: number;
  buildingType: string;
  targetLevel: number;
  metalCost: number;
  crystalCost: number;
  gasCost: number;
  startedAt: string;
  completesAt: string;
  completed: boolean;
}

export interface Technology {
  technology: string;
  level: number;
  metalCost: number;
  crystalCost: number;
  gasCost: number;
  timeSeconds: number;
  prerequisitesMet: boolean;
  isResearching: boolean;
}

export interface ResearchQueue {
  id: number;
  playerId: number;
  technology: string;
  targetLevel: number;
  completesAt: string;
  completed: boolean;
}

export interface ShipTypeInfo {
  shipType: string;
  metalCost: number;
  crystalCost: number;
  gasCost: number;
  timeSeconds: number;
  requiredShipyardLevel: number;
  available: boolean;
}

export interface PlanetShip {
  id: number;
  planetId: number;
  shipType: string;
  quantity: number;
}

export interface ShipyardQueue {
  id: number;
  planetId: number;
  shipType: string;
  quantity: number;
  completesAt: string;
  completed: boolean;
}

export interface Fleet {
  id: number;
  originPlanetId: number;
  targetPlanetId: number;
  playerId: number;
  mission: string;
  departureTime: string;
  arrivalTime: string;
  returnTime: string | null;
  status: string;
  metalLoot: number;
  crystalLoot: number;
  gasLoot: number;
}

export interface CombatReport {
  id: number;
  attackerId: number;
  defenderId: number;
  attackerPlanetId: number;
  defenderPlanetId: number;
  timestamp: string;
  result: string;
  attackerShipsBefore: string;
  defenderShipsBefore: string;
  attackerShipsLost: string;
  defenderShipsLost: string;
  debrisMetal: number;
  debrisCrystal: number;
  resourcesLooted: string;
}

export interface DebrisField {
  metal: number;
  crystal: number;
}

export interface EspionageReport {
  id: number;
  attackerId: number;
  defenderId: number;
  targetPlanetId: number;
  timestamp: string;
  resourcesJson: string;
  shipsJson: string;
  buildingsJson: string;
  technologiesJson: string;
  defensesJson: string;
}

export interface DefenseType {
  defenseType: string;
  metalCost: number;
  crystalCost: number;
  gasCost: number;
  timeSeconds: number;
  requiredShipyardLevel: number;
  available: boolean;
}

export interface PlanetDefense {
  id: number;
  planetId: number;
  defenseType: string;
  quantity: number;
}

export interface SystemInfo {
  systemId: number;
  planetCount: number;
  hasOwnPlanet: boolean;
}

export interface SlotInfo {
  slot: number;
  occupied: boolean;
  planetId?: number;
  planetName?: string;
  playerName?: string;
  playerId?: number;
  isOwn?: boolean;
  fleetCount?: number;
  defenseCount?: number;
  debrisMetal?: number;
  debrisCrystal?: number;
}

export interface SystemDetail {
  galaxy: number;
  systemId: number;
  slots: SlotInfo[];
}

export interface PlanetResourcesResponse {
  planetId: number;
  metal: number;
  crystal: number;
  gas: number;
  energy: number;
  metalRate: number;
  crystalRate: number;
  gasRate: number;
  metalStorage: number;
  crystalStorage: number;
  gasStorage: number;
  energyConsumption: number;
}

export interface DarkMatterResponse {
  darkMatter: number;
}

export interface QuestInfo {
  progressId: number | null;
  questDefinitionId: number;
  title: string;
  description: string;
  icon: string;
  questType: string;
  category: string;
  progress: number;
  target: number;
  rewardType: string;
  rewardAmount: number;
  completed: boolean;
  claimed: boolean;
}
