import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription, interval } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { GameService } from '../core/services/game.service';
import { PlanetStateService } from '../core/services/planet-state.service';
import { AuthService } from '../core/services/auth.service';
import { PlanetResourcesResponse } from '../core/models/models';

@Component({
  selector: 'app-resource-bar',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="resource-bar" *ngIf="resources">
      <span class="resource planet-name">Planet</span>
      <span class="resource metal">&#9679; Metal: <b>{{ fmt(resources.metal) }}</b>
        <span class="cap">/ {{ fmt(resources.metalStorage) }}</span>
        <span class="rate" title="Per hour">+{{ fmt(resources.metalRate) }}/h</span>
      </span>
      <span class="resource crystal">&#9679; Crystal: <b>{{ fmt(resources.crystal) }}</b>
        <span class="cap">/ {{ fmt(resources.crystalStorage) }}</span>
        <span class="rate" title="Per hour">+{{ fmt(resources.crystalRate) }}/h</span>
      </span>
      <span class="resource gas">&#9679; Gas: <b>{{ fmt(resources.gas) }}</b>
        <span class="cap">/ {{ fmt(resources.gasStorage) }}</span>
        <span class="rate" title="Per hour">+{{ fmt(resources.gasRate) }}/h</span>
      </span>
      <span class="resource energy" [class.negative]="resources.energy < 0">
        &#9889; Energy: <b>{{ resources.energy >= 0 ? '+' : '' }}{{ resources.energy | number:'1.0-0' }}</b>
      </span>
      <span class="resource dark-matter">&#9670; Dark Matter: <b>{{ darkMatter }}</b></span>
    </div>
  `,
  styles: [`
    .resource-bar { display: flex; gap: 20px; padding: 6px 16px; background: #111; border-bottom: 1px solid #333; font-size: 12px; align-items: center; flex-wrap: wrap; }
    .resource { white-space: nowrap; }
    .planet-name { color: #ffd700; font-weight: bold; margin-right: 8px; }
    .metal { color: #4af; }
    .crystal { color: #4dd; }
    .gas { color: #f44; }
    .energy { color: #ff0; }
    .energy.negative { color: #f44; }
    .dark-matter { color: #a855f7; }
    .cap { color: #555; font-size: 11px; }
    .rate { color: #4a4; font-size: 10px; margin-left: 2px; }
  `]
})
export class ResourceBarComponent implements OnInit, OnDestroy {
  resources: PlanetResourcesResponse | null = null;
  darkMatter = 0;
  private sub = new Subscription();

  constructor(
    private gameService: GameService,
    private planetState: PlanetStateService,
    private auth: AuthService
  ) {}

  ngOnInit() {
    this.sub.add(
      this.planetState.activePlanetId$.pipe(
        switchMap(planetId => {
          if (!planetId) return [];
          return interval(10000).pipe(
            switchMap(() => this.gameService.getPlanetResources(planetId))
          );
        })
      ).subscribe(data => {
        this.resources = data;
      })
    );

    this.sub.add(
      interval(10000).pipe(
        switchMap(() => {
          const pid = this.auth.getPlayerId();
          return pid ? this.gameService.getDarkMatter(pid) : [];
        })
      ).subscribe(data => {
        this.darkMatter = data.darkMatter;
      })
    );
  }

  ngOnDestroy() {
    this.sub.unsubscribe();
  }

  fmt(n: number): string {
    if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M';
    if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K';
    return Math.floor(n).toLocaleString();
  }
}
