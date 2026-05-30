import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { GameService } from '../core/services/game.service';
import { WebSocketService } from '../core/services/web-socket.service';
import { PlanetShip, Fleet, DebrisField, EspionageReport } from '../core/models/models';

@Component({
  selector: 'app-fleet',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="fleet-container">
      <h2>Fleet Command</h2>

      <div class="section">
        <h3>Your Ships at This Planet</h3>
        <div class="ship-list" *ngIf="planetShips.length > 0">
          <div *ngFor="let ship of planetShips" class="ship-row">
            <span>{{ getDisplayName(ship.shipType) }}: {{ ship.quantity }}</span>
          </div>
        </div>
        <div *ngIf="planetShips.length === 0" class="empty">No ships at this planet.</div>
      </div>

      <div class="section">
        <h3>Launch Fleet</h3>
        <div class="launch-form">
          <div class="form-row">
            <label>Target Planet ID:</label>
            <input type="number" [(ngModel)]="targetPlanetId" min="1">
          </div>
          <div class="form-row">
            <label>Mission:</label>
            <select [(ngModel)]="mission">
              <option value="ATTACK">Attack</option>
              <option value="DEPLOY">Deploy</option>
              <option value="TRANSPORT">Transport</option>
              <option value="COLONIZE">Colonize</option>
              <option value="SPY">Spy</option>
              <option value="RECYCLE">Recycle</option>
            </select>
          </div>
          <div class="form-row" *ngFor="let ship of planetShips">
            <label>{{ getDisplayName(ship.shipType) }} ({{ ship.quantity }} available):</label>
            <input type="number" [(ngModel)]="shipQuantities[ship.shipType]" min="0" [max]="ship.quantity" value="0" (ngModelChange)="updateCargo()">
          </div>
          <div *ngIf="mission === 'TRANSPORT'" class="transport-resources">
            <h4>Resources to Transport</h4>
            <div class="form-row">
              <label>Metal:</label>
              <input type="number" [(ngModel)]="transportMetal" min="0" (ngModelChange)="updateCargo()">
            </div>
            <div class="form-row">
              <label>Crystal:</label>
              <input type="number" [(ngModel)]="transportCrystal" min="0" (ngModelChange)="updateCargo()">
            </div>
            <div class="form-row">
              <label>Gas:</label>
              <input type="number" [(ngModel)]="transportGas" min="0" (ngModelChange)="updateCargo()">
            </div>
            <div class="cargo-info">Cargo: {{ transportCargoUsed }} / {{ transportCargoTotal }}</div>
          </div>
          <div *ngIf="mission === 'COLONIZE'" class="colonize-coords">
            <h4>Target Coordinates</h4>
            <div class="form-row">
              <label>Galaxy (1-9):</label>
              <input type="number" [(ngModel)]="targetGalaxy" min="1" max="9">
            </div>
            <div class="form-row">
              <label>System (1-500):</label>
              <input type="number" [(ngModel)]="targetSystemId" min="1" max="500">
            </div>
            <div class="form-row">
              <label>Slot (1-15):</label>
              <input type="number" [(ngModel)]="targetSlot" min="1" max="15">
            </div>
          </div>
          <button (click)="launchFleet()" [disabled]="!targetPlanetId">Launch</button>
          <div *ngIf="launchError" class="error">{{ launchError }}</div>
        </div>
      </div>

      <div class="section">
        <h3>Active Fleets</h3>
        <div *ngFor="let fleet of activeFleets" class="fleet-card" [class.en-route]="fleet.status === 'EN_ROUTE'">
          <div class="fleet-mission">{{ fleet.mission }} → Planet {{ fleet.targetPlanetId }}</div>
          <div class="fleet-status">{{ fleet.status }}</div>
          <div class="fleet-eta" *ngIf="fleet.status === 'EN_ROUTE'">Arrives: {{ fleet.arrivalTime }}</div>
          <button *ngIf="fleet.status === 'EN_ROUTE'" (click)="recallFleet(fleet.id)">Recall</button>
        </div>
        <div *ngIf="activeFleets.length === 0" class="empty">No active fleets.</div>
      </div>

      <div class="section">
        <h3>Debris Field</h3>
        <div *ngIf="debrisField && (debrisField.metal > 0 || debrisField.crystal > 0)">
          <span>Metal: {{ debrisField.metal.toLocaleString() }}</span>
          <span>Crystal: {{ debrisField.crystal.toLocaleString() }}</span>
        </div>
        <div *ngIf="!debrisField || (debrisField.metal === 0 && debrisField.crystal === 0)" class="empty">
          No debris field at this planet.
        </div>
      </div>

      <div class="section">
        <h3>Espionage Reports</h3>
        <div *ngFor="let report of espionageReports" class="report-card">
          <div class="report-header">Report from {{ report.timestamp | date:'short' }}</div>
          <div class="report-data" *ngIf="report.resourcesJson !== '{}'">
            <strong>Resources:</strong> {{ formatJson(report.resourcesJson) }}
          </div>
          <div class="report-data" *ngIf="report.shipsJson !== '{}'">
            <strong>Ships:</strong> {{ formatJson(report.shipsJson) }}
          </div>
          <div class="report-data" *ngIf="report.buildingsJson !== '{}'">
            <strong>Buildings:</strong> {{ formatJson(report.buildingsJson) }}
          </div>
          <div class="report-data" *ngIf="report.technologiesJson !== '{}'">
            <strong>Technologies:</strong> {{ formatJson(report.technologiesJson) }}
          </div>
        </div>
        <div *ngIf="espionageReports.length === 0" class="empty">No espionage reports.</div>
      </div>
    </div>
  `,
  styles: [`
    .fleet-container { padding: 20px; color: #ccc; }
    .section { background: #1a1a2e; border: 1px solid #333; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
    h3 { color: #ffd700; margin: 0 0 12px 0; font-size: 14px; }
    .ship-list { display: flex; flex-wrap: wrap; gap: 8px; }
    .ship-row { font-size: 13px; color: #ccc; }
    .empty { color: #666; font-style: italic; font-size: 13px; }
    .launch-form { display: flex; flex-direction: column; gap: 8px; }
    .form-row { display: flex; gap: 8px; align-items: center; }
    .form-row label { min-width: 200px; font-size: 13px; }
    .form-row input, .form-row select { padding: 4px; background: #222; border: 1px solid #444; color: #fff; border-radius: 4px; width: 80px; }
    button { padding: 6px 16px; background: #4a9; color: #fff; border: none; border-radius: 4px; cursor: pointer; margin-top: 8px; }
    button:hover { background: #5ba; }
    button:disabled { opacity: 0.5; cursor: not-allowed; }
    .fleet-card { background: #122; border: 1px solid #4a9; border-radius: 6px; padding: 12px; margin-bottom: 8px; }
    .fleet-card.en-route { border-color: #fa0; }
    .fleet-mission { font-size: 14px; color: #fff; font-weight: bold; }
    .fleet-status { font-size: 12px; color: #888; }
    .fleet-eta { font-size: 12px; color: #4a9; }
    .error { color: #f44; font-size: 12px; margin-top: 4px; }
    .cargo-info { font-size: 12px; color: #4a9; margin-top: 4px; }
    .colonize-coords { margin-top: 8px; }
    .colonize-coords h4, .transport-resources h4 { color: #ffd700; font-size: 12px; margin: 8px 0; }
    .report-card { background: #221; border: 1px solid #a94; border-radius: 6px; padding: 8px; margin-bottom: 6px; font-size: 12px; }
    .report-header { color: #ffd700; font-weight: bold; margin-bottom: 4px; }
    .report-data { color: #ccc; margin: 2px 0; }
  `]
})
export class FleetComponent implements OnInit, OnDestroy {
  planetId = 1;
  planetShips: PlanetShip[] = [];
  activeFleets: Fleet[] = [];
  debrisField: DebrisField | null = null;
  targetPlanetId: number | null = null;
  mission: string = 'ATTACK';
  shipQuantities: { [key: string]: number } = {};
  launchError: string | null = null;

  // Transport
  transportMetal = 0;
  transportCrystal = 0;
  transportGas = 0;
  transportCargoUsed = 0;
  transportCargoTotal = 0;

  // Colonize
  targetGalaxy = 1;
  targetSystemId = 1;
  targetSlot = 1;

  // Espionage
  espionageReports: EspionageReport[] = [];

  constructor(
    private gameService: GameService,
    private ws: WebSocketService
  ) {}

  ngOnInit() {
    this.loadData();
    this.ws.connect();
    this.ws.subscribe('/topic/planet/*', () => this.loadData());
  }

  ngOnDestroy() {}

  loadData() {
    this.gameService.getPlanetShips(this.planetId).subscribe(ships => {
      this.planetShips = ships;
    });
    this.gameService.getPlanetFleets(this.planetId).subscribe(fleets => {
      this.activeFleets = fleets;
    });
    this.gameService.getDebrisField(this.planetId).subscribe(df => {
      this.debrisField = df;
    });
    this.gameService.getEspionageReports(this.planetId).subscribe(reports => {
      this.espionageReports = reports;
    });
  }

  launchFleet() {
    const ships: { [key: string]: number } = {};
    let hasShips = false;
    for (const key of Object.keys(this.shipQuantities)) {
      const qty = this.shipQuantities[key] || 0;
      if (qty > 0) {
        ships[key] = qty;
        hasShips = true;
      }
    }
    if (!hasShips) {
      this.launchError = 'Select at least one ship';
      return;
    }
    this.launchError = null;

    let body: any = { mission: this.mission, ships };

    if (this.mission === 'COLONIZE') {
      body.galaxy = this.targetGalaxy;
      body.systemId = this.targetSystemId;
      body.slot = this.targetSlot;
    } else {
      if (!this.targetPlanetId) {
        this.launchError = 'Select a target planet';
        return;
      }
      body.targetPlanetId = this.targetPlanetId;
    }

    if (this.mission === 'TRANSPORT') {
      body.metal = this.transportMetal;
      body.crystal = this.transportCrystal;
      body.gas = this.transportGas;
    }

    this.gameService.launchFleet(this.planetId, body).subscribe({
      next: () => this.loadData(),
      error: (err) => this.launchError = err.error?.error || 'Launch failed'
    });
  }

  recallFleet(fleetId: number) {
    this.gameService.recallFleet(fleetId).subscribe(() => this.loadData());
  }

  getDisplayName(name: string): string {
    return name.split('_').map(w => w.charAt(0) + w.slice(1).toLowerCase()).join(' ');
  }

  updateCargo() {
    let total = 0;
    for (const key of Object.keys(this.shipQuantities)) {
      const qty = this.shipQuantities[key] || 0;
      // Approximate cargo values based on ship type name
      const cargoMap: {[key: string]: number} = {
        'LIGHT_FIGHTER': 50, 'HEAVY_FIGHTER': 100, 'CRUISER': 800,
        'BATTLESHIP': 1500, 'SMALL_CARGO': 5000, 'LARGE_CARGO': 25000,
        'COLONY_SHIP': 7500, 'RECYCLER': 20000, 'ESPIONAGE_PROBE': 0
      };
      total += (cargoMap[key] || 0) * qty;
    }
    this.transportCargoTotal = total;
    this.transportCargoUsed = this.transportMetal + this.transportCrystal + this.transportGas;
  }

  formatJson(json: string): string {
    try {
      const obj = JSON.parse(json);
      return Object.entries(obj).map(([k, v]) => `${k}: ${v}`).join(', ');
    } catch {
      return json;
    }
  }
}
