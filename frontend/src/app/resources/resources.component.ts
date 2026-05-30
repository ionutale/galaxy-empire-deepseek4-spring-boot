import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { GameService } from '../core/services/game.service';
import { Planet, Building } from '../core/models/models';

@Component({
  selector: 'app-resources',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="resources-view" *ngIf="planet() as p">
      <header>
        <h2>Resources — {{ p.name }}</h2>
      </header>

      <svg viewBox="0 0 500 400" class="planet-grid">
        <rect x="0" y="0" width="500" height="400" fill="#0d0d2b" rx="8" />
        <text x="250" y="30" text-anchor="middle" fill="#666" font-size="12">
          Resource Buildings
        </text>

        <g *ngFor="let b of buildings()" (click)="selectBuilding(b)" class="building-tile">
          <rect [attr.x]="getX(b.gridPosition)" [attr.y]="getY(b.gridPosition)"
                width="100" height="60" rx="4"
                [attr.fill]="getFill(b.buildingType)"
                [attr.stroke]="selectedBuilding()?.gridPosition === b.gridPosition ? '#ffd700' : '#2a2a5e'"
                stroke-width="2" />
          <text [attr.x]="getX(b.gridPosition) + 50" [attr.y]="getY(b.gridPosition) + 25"
                text-anchor="middle" fill="#fff" font-size="11" font-weight="bold">
            {{ formatName(b.buildingType) }}
          </text>
          <text [attr.x]="getX(b.gridPosition) + 50" [attr.y]="getY(b.gridPosition) + 42"
                text-anchor="middle" fill="#aaa" font-size="10">
            Lv {{ b.level }}
          </text>
        </g>
      </svg>

      <div class="upgrade-panel" *ngIf="selectedBuilding() as sel">
        <h3>{{ formatName(sel.buildingType) }} → Level {{ sel.level + 1 }}</h3>
        <div class="costs" *ngIf="upgradeCost() as cost">
          <span class="cost metal">⛁ {{ formatNum(cost.metal) }}</span>
          <span class="cost crystal">◆ {{ formatNum(cost.crystal) }}</span>
          <span class="cost gas">◈ {{ formatNum(cost.gas) }}</span>
          <span class="time">⏱ {{ formatTime(cost.timeSeconds) }}</span>
        </div>
        <button (click)="startUpgrade()" [disabled]="upgrading()">
          {{ upgrading() ? 'Upgrading...' : 'Upgrade' }}
        </button>
      </div>
    </div>
  `,
  styles: [`
    .resources-view { padding: 1rem; max-width: 700px; margin: 0 auto; }
    header { margin-bottom: 0.5rem; }
    h2 { color: #ffd700; margin: 0; font-size: 1.2rem; }
    .resources-bar { display: flex; gap: 0.8rem; margin-top: 0.3rem; font-size: 0.85rem; }
    .res.metal { color: #a88; }
    .res.crystal { color: #8af; }
    .res.gas { color: #8f8; }
    .res.energy { color: #ff0; }
    .negative { color: #f44 !important; }
    .planet-grid { width: 100%; max-width: 500px; cursor: pointer; }
    .building-tile:hover { cursor: pointer; opacity: 0.85; }
    .upgrade-panel {
      background: #1a1a4e; padding: 1rem; border-radius: 6px;
      margin-top: 0.5rem;
    }
    .upgrade-panel h3 { color: #ffd700; margin: 0 0 0.5rem; }
    .costs { display: flex; gap: 0.8rem; font-size: 0.9rem; margin-bottom: 0.5rem; }
    .cost.metal { color: #a88; }
    .cost.crystal { color: #8af; }
    .cost.gas { color: #8f8; }
    .time { color: #aaa; }
    .upgrade-panel button {
      padding: 0.5rem 1.5rem; background: #ffd700; color: #000;
      border: none; border-radius: 4px; font-weight: bold; cursor: pointer;
    }
    .upgrade-panel button:disabled { background: #666; cursor: not-allowed; }
  `]
})
export class ResourcesComponent implements OnInit {
  planet = signal<Planet | null>(null);
  buildings = signal<Building[]>([]);
  selectedBuilding = signal<Building | null>(null);
  upgradeCost = signal<any>(null);
  upgrading = signal(false);

  constructor(private game: GameService) {}

  ngOnInit() {
    this.loadPlanet();
  }

  loadPlanet() {
    this.game.getPlanet(1).subscribe({
      next: (p) => {
        this.planet.set(p);
        this.buildings.set(p.buildings.filter(b => b.level > 0));
      }
    });
  }

  selectBuilding(b: Building) {
    this.selectedBuilding.set(b);
    this.game.getUpgradeCost(1, b.gridPosition).subscribe({
      next: (cost) => this.upgradeCost.set(cost)
    });
  }

  startUpgrade() {
    const sel = this.selectedBuilding();
    if (!sel || this.upgrading()) return;
    this.upgrading.set(true);
    this.game.upgradeBuilding(1, sel.gridPosition).subscribe({
      next: () => {
        this.upgrading.set(false);
        this.selectedBuilding.set(null);
        this.loadPlanet();
      },
      error: () => {
        this.upgrading.set(false);
      }
    });
  }

  getX(pos: number): number {
    return 20 + (pos % 4) * 120;
  }

  getY(pos: number): number {
    return 50 + Math.floor(pos / 4) * 80;
  }

  getFill(type: string): string {
    const fills: Record<string, string> = {
      'METAL_MINE': '#4a3520',
      'CRYSTAL_MINE': '#203055',
      'GAS_MINE': '#2a4a2a',
      'SOLAR_PLANT': '#4a4a00',
      'METAL_STORAGE': '#3a2a15',
      'CRYSTAL_STORAGE': '#15203a',
      'GAS_STORAGE': '#1a3a1a',
      'ROBOT_FACTORY': '#3a3a4a',
      'RESEARCH_LAB': '#2a1a4a',
      'SHIPYARD': '#3a2a2a'
    };
    return fills[type] || '#2a2a5e';
  }

  formatName(type: string): string {
    return type.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
  }

  formatNum(n: number): string {
    if (n >= 1e6) return (n / 1e6).toFixed(1) + 'M';
    if (n >= 1e3) return (n / 1e3).toFixed(1) + 'K';
    return Math.floor(n).toString();
  }

  formatTime(seconds: number): string {
    if (seconds >= 3600) return (seconds / 3600).toFixed(1) + 'h';
    if (seconds >= 60) return Math.floor(seconds / 60) + 'm';
    return seconds + 's';
  }
}
