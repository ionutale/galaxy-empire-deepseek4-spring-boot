import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PlanetStateService {
  private activePlanetIdSource = new BehaviorSubject<number | null>(null);
  activePlanetId$ = this.activePlanetIdSource.asObservable();

  setActivePlanet(planetId: number) {
    this.activePlanetIdSource.next(planetId);
  }
}
