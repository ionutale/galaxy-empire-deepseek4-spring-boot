import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { GameService } from '../core/services/game.service';
import { WebSocketService } from '../core/services/web-socket.service';
import { ShipTypeInfo, PlanetShip, ShipyardQueue, DefenseType, PlanetDefense } from '../core/models/models';

@Component({
  selector: 'app-shipyard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="shipyard-container">
      <h2>Shipyard</h2>

      <div class="existing-ships" *ngIf="planetShips.length > 0">
        <h3>Fleet at this planet</h3>
        <div class="ship-list">
          <div *ngFor="let ship of planetShips" class="ship-count">
            {{ getDisplayName(ship.shipType) }}: {{ ship.quantity }}
          </div>
        </div>
      </div>

      <div class="build-queue" *ngIf="shipyardQueue.length > 0">
        <h3>Building</h3>
        <div *ngFor="let q of shipyardQueue" class="queue-item">
          {{ getDisplayName(q.shipType) }} x{{ q.quantity }}
          <button class="speed-up" (click)="speedUpShipyard(q.id, $event)">Speed Up</button>
        </div>
      </div>

      <div class="ship-grid">
        <div *ngFor="let ship of shipTypes"
             class="ship-card"
             [class.available]="ship.available"
             [class.locked]="!ship.available">
          <div class="ship-name">{{ getDisplayName(ship.shipType) }}</div>
          <div class="ship-stats">
            <span>M: {{ ship.metalCost.toLocaleString() }}</span>
            <span>C: {{ ship.crystalCost.toLocaleString() }}</span>
            <span *ngIf="ship.gasCost > 0">G: {{ ship.gasCost.toLocaleString() }}</span>
          </div>
          <div class="ship-time">{{ formatTime(ship.timeSeconds) }}</div>
          <div class="ship-required" *ngIf="!ship.available">
            Requires Shipyard {{ ship.requiredShipyardLevel }}
          </div>
          <div class="build-controls" *ngIf="ship.available">
            <input type="number" min="1" max="9999"
                   [value]="getQuantity(ship.shipType)"
                   (change)="buildQuantities[ship.shipType] = $any($event.target).value">
            <button (click)="build(ship)">Build</button>
          </div>
          <div class="existing-count" *ngIf="getExistingShips(ship.shipType) > 0">
            Owned: {{ getExistingShips(ship.shipType) }}
          </div>
        </div>
      </div>

      <div class="section">
        <h3>Defenses</h3>
        <button (click)="toggleDefenses()" class="toggle-btn">
          {{ showDefenses ? 'Hide' : 'Show' }} Defenses
        </button>
        <div *ngIf="showDefenses">
          <div *ngIf="planetDefenses.length > 0" class="defense-list">
            <div *ngFor="let d of planetDefenses" class="defense-row">
              <span>{{ getDisplayName(d.defenseType) }}: {{ d.quantity }}</span>
            </div>
          </div>
          <div *ngIf="planetDefenses.length === 0" class="empty">No defenses built.</div>
          <div class="build-form">
            <div class="form-row">
              <label>Defense Type:</label>
              <select [(ngModel)]="selectedDefense">
                <option value="">Select...</option>
                <option *ngFor="let t of defenseTypes" [value]="t.defenseType" [disabled]="!t.available">
                  {{ getDisplayName(t.defenseType) }} ({{ t.metalCost }}M / {{ t.crystalCost }}C / {{ t.gasCost }}G)
                </option>
              </select>
            </div>
            <div class="form-row">
              <label>Quantity:</label>
              <input type="number" [(ngModel)]="defenseQuantity" min="1">
            </div>
            <button (click)="buildDefense()" [disabled]="!selectedDefense">Build</button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .shipyard-container { padding: 20px; color: #ccc; }
    .ship-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 12px; margin-top: 16px; }
    .ship-card { background: #1a1a2e; border: 1px solid #333; border-radius: 8px; padding: 16px; }
    .ship-card.available { border-color: #4a9; }
    .ship-card.locked { opacity: 0.5; }
    .ship-name { font-size: 14px; font-weight: bold; color: #fff; }
    .ship-stats { font-size: 11px; color: #aaa; margin-top: 8px; }
    .ship-stats span { margin-right: 8px; }
    .ship-time { font-size: 11px; color: #666; margin-top: 4px; }
    .ship-required { font-size: 11px; color: #c66; margin-top: 8px; }
    .build-controls { margin-top: 8px; display: flex; gap: 8px; }
    .build-controls input { width: 70px; padding: 4px; background: #222; border: 1px solid #444; color: #fff; border-radius: 4px; }
    .build-controls button { padding: 4px 12px; background: #4a9; color: #fff; border: none; border-radius: 4px; cursor: pointer; }
    .build-controls button:hover { background: #5ba; }
    .existing-count { font-size: 11px; color: #888; margin-top: 4px; }
    .existing-ships, .build-queue { background: #1a2a1a; border: 1px solid #4a9; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
    .ship-list { display: flex; gap: 16px; flex-wrap: wrap; }
    .ship-count { font-size: 13px; color: #ccc; }
    .queue-item { font-size: 13px; color: #fa0; padding: 4px 0; }
    .toggle-btn { padding: 4px 12px; background: #555; color: #fff; border: none; border-radius: 4px; cursor: pointer; font-size: 12px; margin-bottom: 8px; }
    .toggle-btn:hover { background: #666; }
    .defense-list { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
    .defense-row { font-size: 13px; color: #ccc; }
    .build-form { display: flex; flex-direction: column; gap: 8px; }
    .build-form select { padding: 4px; background: #222; border: 1px solid #444; color: #fff; border-radius: 4px; }
    .speed-up { background: #7c3aed; color: #fff; border: none; padding: 2px 8px; border-radius: 4px; cursor: pointer; font-size: 11px; margin-left: 8px; }
    .speed-up:hover { background: #6d28d9; }
  `]
})
export class ShipyardComponent implements OnInit, OnDestroy {
  planetId = 1;
  shipTypes: ShipTypeInfo[] = [];
  planetShips: PlanetShip[] = [];
  shipyardQueue: ShipyardQueue[] = [];
  buildQuantities: { [key: string]: number } = {};
  showDefenses = false;
  defenseTypes: DefenseType[] = [];
  planetDefenses: PlanetDefense[] = [];
  selectedDefense: string = '';
  defenseQuantity: number = 1;

  constructor(
    private gameService: GameService,
    private ws: WebSocketService
  ) {}

  ngOnInit() {
    this.loadShipyardData();
    this.ws.connect();
    this.ws.subscribe('/topic/planet/*', () => this.loadShipyardData());
  }

  ngOnDestroy() {}

  loadShipyardData() {
    this.gameService.getAvailableShips(this.planetId).subscribe(types => {
      this.shipTypes = types;
    });
    this.gameService.getPlanetShips(this.planetId).subscribe(ships => {
      this.planetShips = ships;
    });
    this.gameService.getShipyardQueue(this.planetId).subscribe(queue => {
      this.shipyardQueue = queue;
    });
  }

  speedUpShipyard(queueId: number, event: Event) {
    event.stopPropagation();
    this.gameService.speedUpShipyard(this.planetId, queueId).subscribe(() => {
      this.loadShipyardData();
    });
  }

  getQuantity(type: string): number {
    return this.buildQuantities[type] || 1;
  }

  build(type: ShipTypeInfo) {
    if (!type.available) return;
    const qty = this.getQuantity(type.shipType);
    this.gameService.buildShips(this.planetId, type.shipType, qty).subscribe(() => {
      this.loadShipyardData();
    });
  }

  getExistingShips(type: string): number {
    const found = this.planetShips.find(s => s.shipType === type);
    return found ? found.quantity : 0;
  }

  getDisplayName(name: string): string {
    return name.split('_').map(w => w.charAt(0) + w.slice(1).toLowerCase()).join(' ');
  }

  formatTime(seconds: number): string {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    if (h > 0) return `${h}h ${m}m`;
    if (m > 0) return `${m}m ${s}s`;
    return `${s}s`;
  }

  toggleDefenses() {
    this.showDefenses = !this.showDefenses;
    if (this.showDefenses) {
      this.loadDefenses();
    }
  }

  loadDefenses() {
    this.gameService.getDefenseTypes(this.planetId).subscribe(types => {
      this.defenseTypes = types;
    });
    this.gameService.getPlanetDefenses(this.planetId).subscribe(defs => {
      this.planetDefenses = defs;
    });
  }

  buildDefense() {
    if (!this.selectedDefense || this.defenseQuantity < 1) return;
    this.gameService.buildDefense(this.planetId, this.selectedDefense, this.defenseQuantity).subscribe({
      next: () => this.loadDefenses(),
      error: (err) => console.error(err)
    });
  }
}
