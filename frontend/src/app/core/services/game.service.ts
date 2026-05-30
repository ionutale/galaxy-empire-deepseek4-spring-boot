import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Planet, ConstructionQueue, Technology, ResearchQueue, ShipTypeInfo, PlanetShip, ShipyardQueue, Fleet, CombatReport, DebrisField, EspionageReport, DefenseType, PlanetDefense, SystemInfo, SlotInfo, SystemDetail, PlanetResourcesResponse, DarkMatterResponse, QuestInfo } from '../models/models';

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

  getTechnologies(): Observable<Technology[]> {
    return this.http.get<Technology[]>(`${environment.apiUrl}/game/technologies`);
  }

  getTechnology(name: string): Observable<Technology> {
    return this.http.get<Technology>(`${environment.apiUrl}/game/technologies/${name}`);
  }

  startResearch(name: string): Observable<any> {
    return this.http.post(`${environment.apiUrl}/game/technologies/${name}/research`, {});
  }

  getResearchQueue(): Observable<ResearchQueue[]> {
    return this.http.get<ResearchQueue[]>(`${environment.apiUrl}/game/research-queue`);
  }

  getPlanetShips(planetId: number): Observable<PlanetShip[]> {
    return this.http.get<PlanetShip[]>(`${environment.apiUrl}/game/planets/${planetId}/ships`);
  }

  getAvailableShips(planetId: number): Observable<ShipTypeInfo[]> {
    return this.http.get<ShipTypeInfo[]>(`${environment.apiUrl}/game/planets/${planetId}/shipyard`);
  }

  buildShips(planetId: number, shipType: string, quantity: number): Observable<any> {
    return this.http.post(`${environment.apiUrl}/game/planets/${planetId}/ships/${shipType}/build`, { quantity });
  }

  getShipyardQueue(planetId: number): Observable<ShipyardQueue[]> {
    return this.http.get<ShipyardQueue[]>(`${environment.apiUrl}/game/planets/${planetId}/shipyard-queue`);
  }

  launchFleet(planetId: number, body: any) {
    return this.http.post(`${environment.apiUrl}/game/planets/${planetId}/fleet`, body);
  }

  getPlanetFleets(planetId: number) {
    return this.http.get<Fleet[]>(`${environment.apiUrl}/game/planets/${planetId}/fleet`);
  }

  getFleetDetail(fleetId: number) {
    return this.http.get(`${environment.apiUrl}/game/fleet/${fleetId}`);
  }

  recallFleet(fleetId: number) {
    return this.http.post(`${environment.apiUrl}/game/fleet/${fleetId}/recall`, {});
  }

  getCombatReports(planetId: number) {
    return this.http.get<CombatReport[]>(`${environment.apiUrl}/game/planets/${planetId}/combat-reports`);
  }

  getDebrisField(planetId: number) {
    return this.http.get<DebrisField>(`${environment.apiUrl}/game/planets/${planetId}/debris`);
  }

  getEspionageReports(planetId: number) {
    return this.http.get<EspionageReport[]>(`${environment.apiUrl}/game/planets/${planetId}/espionage-reports`);
  }

  getDefenseTypes(planetId: number) {
    return this.http.get<DefenseType[]>(`${environment.apiUrl}/game/planets/${planetId}/defense-types`);
  }

  buildDefense(planetId: number, defenseType: string, quantity: number) {
    return this.http.post(`${environment.apiUrl}/game/planets/${planetId}/defense`, { defenseType, quantity });
  }

  getPlanetDefenses(planetId: number) {
    return this.http.get<PlanetDefense[]>(`${environment.apiUrl}/game/planets/${planetId}/defenses`);
  }

  getSystemList(galaxy: number) {
    return this.http.get<SystemInfo[]>(`${environment.apiUrl}/game/galaxies/${galaxy}/systems`);
  }

  getSystemDetail(galaxy: number, systemId: number) {
    return this.http.get<SystemDetail>(`${environment.apiUrl}/game/galaxies/${galaxy}/systems/${systemId}`);
  }

  getMyPlanets() {
    return this.http.get<{ id: number; name: string; coordinates: string }[]>(
      `${environment.apiUrl}/game/planets/my`
    );
  }

  getPlanetResources(planetId: number) {
    return this.http.get<PlanetResourcesResponse>(`${environment.apiUrl}/game/planets/${planetId}/resources`);
  }

  getDarkMatter(playerId: number) {
    return this.http.get<DarkMatterResponse>(`${environment.apiUrl}/game/players/${playerId}/dark-matter`);
  }

  addDarkMatter(playerId: number, amount: number) {
    return this.http.post<DarkMatterResponse>(`${environment.apiUrl}/game/players/${playerId}/dark-matter/add`, { amount });
  }

  speedUpBuilding(planetId: number, queueId: number) {
    return this.http.post(`${environment.apiUrl}/game/planets/${planetId}/buildings/queue/${queueId}/speed-up`, {});
  }

  speedUpShipyard(planetId: number, queueId: number) {
    return this.http.post(`${environment.apiUrl}/game/planets/${planetId}/shipyard/${queueId}/speed-up`, {});
  }

  speedUpResearch(technology: string) {
    return this.http.post(`${environment.apiUrl}/game/technologies/speed-up`, { technology });
  }

  getQuests() {
    return this.http.get<QuestInfo[]>(`${environment.apiUrl}/game/quests`);
  }

  claimQuestReward(progressId: number) {
    return this.http.post(`${environment.apiUrl}/game/quests/${progressId}/claim`, {});
  }
}
