import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { GameService } from '../core/services/game.service';
import { AuthService } from '../core/services/auth.service';
import { Planet, ConstructionQueue } from '../core/models/models';
import { WebSocketService } from '../core/services/web-socket.service';

@Component({
  selector: 'app-overview',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="overview" *ngIf="planet() as p">
      <header>
        <h1>{{ p.name }}</h1>
        <span class="coords">{{ p.coordinates }}</span>
        <span class="temp">{{ p.temperature }}°C</span>
      </header>

      <div class="resources">
        <div class="res">
          <span class="label">Metal</span>
          <span class="value">{{ formatNum(p.resources.metal) }}</span>
        </div>
        <div class="res">
          <span class="label">Crystal</span>
          <span class="value">{{ formatNum(p.resources.crystal) }}</span>
        </div>
        <div class="res">
          <span class="label">Gas</span>
          <span class="value">{{ formatNum(p.resources.gas) }}</span>
        </div>
        <div class="res energy">
          <span class="label">Energy</span>
          <span class="value" [class.negative]="p.resources.energy < 0">
            {{ formatNum(p.resources.energy) }}
          </span>
        </div>
      </div>

      <div class="actions">
        <button (click)="nav('resources')">Resources</button>
        <button (click)="nav('facilities')">Facilities</button>
      </div>

      <div class="queue" *ngIf="queue().length > 0">
        <h3>Construction Queue</h3>
        <div class="queue-item" *ngFor="let q of queue()">
          <span>{{ q.buildingType }} → Lv{{ q.targetLevel }}</span>
          <span class="timer">{{ getRemaining(q) }}</span>
        </div>
      </div>
    </div>

    <div class="loading" *ngIf="!planet()">
      <p>Loading planet data...</p>
    </div>
  `,
  styles: [`
    .overview { padding: 1rem; max-width: 600px; margin: 0 auto; }
    header { text-align: center; margin-bottom: 1rem; }
    header h1 { color: #ffd700; margin: 0; font-size: 1.5rem; }
    .coords { color: #888; font-size: 0.9rem; }
    .temp { color: #69c; font-size: 0.8rem; margin-left: 0.5rem; }
    .resources { display: grid; grid-template-columns: 1fr 1fr; gap: 0.5rem; }
    .res {
      background: #1a1a4e; padding: 0.8rem; border-radius: 6px;
      display: flex; justify-content: space-between;
    }
    .res .label { color: #888; }
    .res .value { color: #fff; font-weight: bold; }
    .value.negative { color: #ff4444; }
    .actions { display: flex; gap: 0.5rem; margin-top: 1rem; }
    .actions button {
      flex: 1; padding: 0.6rem; background: #2a2a5e; color: #fff;
      border: 1px solid #444; border-radius: 4px; cursor: pointer;
    }
    .actions button:hover { background: #3a3a6e; }
    .queue { margin-top: 1rem; }
    .queue h3 { color: #ffd700; font-size: 1rem; }
    .queue-item {
      background: #1a1a4e; padding: 0.5rem; margin-top: 0.3rem;
      border-radius: 4px; display: flex; justify-content: space-between;
    }
    .timer { color: #4c4; font-size: 0.85rem; }
    .loading { text-align: center; padding: 2rem; color: #888; }
  `]
})
export class OverviewComponent implements OnInit {
  planet = signal<Planet | null>(null);
  queue = signal<ConstructionQueue[]>([]);
  private interval: any;

  constructor(
    private game: GameService,
    private auth: AuthService,
    private ws: WebSocketService,
    private router: Router
  ) {}

  ngOnInit() {
    this.loadPlanet();
    this.ws.connect();
    this.ws.subscribe('/topic/planet/*', () => this.loadPlanet());
    this.interval = setInterval(() => this.loadPlanet(), 10000);
  }

  loadPlanet() {
    const playerId = this.auth.getPlayerId();
    if (!playerId) return;
    this.game.createPlanet().subscribe({
      next: (p) => {
        this.game.getPlanet(p.id).subscribe({
          next: (planet) => this.planet.set(planet)
        });
        this.game.getQueue(p.id).subscribe({
          next: (q) => this.queue.set(q)
        });
      },
      error: () => {
        this.game.getPlanet(1).subscribe({
          next: (planet) => this.planet.set(planet)
        });
      }
    });
  }

  getRemaining(q: ConstructionQueue): string {
    const remaining = new Date(q.completesAt).getTime() - Date.now();
    if (remaining <= 0) return 'Complete!';
    const mins = Math.floor(remaining / 60000);
    const secs = Math.floor((remaining % 60000) / 1000);
    return `${mins}m ${secs}s`;
  }

  formatNum(n: number): string {
    if (n >= 1e6) return (n / 1e6).toFixed(1) + 'M';
    if (n >= 1e3) return (n / 1e3).toFixed(1) + 'K';
    return Math.floor(n).toString();
  }

  nav(view: string) {
    this.router.navigate(['/' + view]);
  }

  ngOnDestroy() {
    clearInterval(this.interval);
  }
}
