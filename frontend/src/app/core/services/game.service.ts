import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Planet, ConstructionQueue } from '../models/models';

@Injectable({ providedIn: 'root' })
export class GameService {
  constructor(private http: HttpClient) {}

  createPlanet() {
    return this.http.post<{ id: number; name: string; coordinates: string }>(
      `${environment.apiUrl}/game/planets`, {}
    );
  }

  getPlanet(id: number) {
    return this.http.get<Planet>(`${environment.apiUrl}/game/planets/${id}`);
  }

  getUpgradeCost(planetId: number, gridPosition: number) {
    return this.http.get<any>(
      `${environment.apiUrl}/game/planets/${planetId}/buildings/${gridPosition}/cost`
    );
  }

  upgradeBuilding(planetId: number, gridPosition: number) {
    return this.http.post<any>(
      `${environment.apiUrl}/game/planets/${planetId}/buildings/${gridPosition}/upgrade`, {}
    );
  }

  getQueue(planetId: number) {
    return this.http.get<ConstructionQueue[]>(
      `${environment.apiUrl}/game/planets/${planetId}/queue`
    );
  }
}
