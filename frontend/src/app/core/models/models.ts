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
