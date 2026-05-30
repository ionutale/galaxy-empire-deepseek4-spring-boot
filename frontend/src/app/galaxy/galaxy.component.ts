import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { GameService } from '../core/services/game.service';
import { SystemInfo, SlotInfo } from '../core/models/models';

@Component({
  selector: 'app-galaxy',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="galaxy-container">
      <h2>Galaxy Map</h2>

      <!-- Breadcrumb -->
      <div class="breadcrumb" *ngIf="selectedGalaxy">
        <span (click)="backToGalaxies()" class="crumb-link">Galaxies</span>
        <span *ngIf="selectedSystem === null"> &gt; Galaxy {{ selectedGalaxy }}</span>
        <ng-container *ngIf="selectedSystem !== null">
          <span> &gt; </span>
          <span (click)="backToSystems()" class="crumb-link">Galaxy {{ selectedGalaxy }}</span>
          <span> &gt; System {{ selectedSystem }}</span>
        </ng-container>
      </div>

      <!-- Level 1: Galaxy Grid -->
      <div *ngIf="selectedGalaxy === null" class="level">
        <h3>Select Galaxy</h3>
        <div class="galaxy-grid">
          <div *ngFor="let g of galaxyNumbers" class="galaxy-tile"
               [style.background]="getGalaxyColor(g)"
               (click)="selectGalaxy(g)">
            {{ g }}
          </div>
        </div>
      </div>

      <!-- Level 2: System List -->
      <div *ngIf="selectedGalaxy !== null && selectedSystem === null" class="level">
        <h3>Galaxy {{ selectedGalaxy }} — Systems</h3>
        <div class="system-list">
          <div class="system-header">
            <span class="sys-id">System</span>
            <span class="sys-planets">Planets</span>
            <span class="sys-status">Status</span>
          </div>
          <div *ngFor="let sys of systemList" class="system-row"
               [class.own-planet]="sys.hasOwnPlanet"
               (click)="selectSystem(sys.systemId)">
            <span class="sys-id">{{ sys.systemId }}</span>
            <span class="sys-planets">{{ sys.planetCount }} / 15</span>
            <span class="sys-status" [class.occupied]="sys.planetCount > 0">
              {{ sys.hasOwnPlanet ? 'Your Colony' : (sys.planetCount > 0 ? 'Inhabited' : 'Empty') }}
            </span>
          </div>
        </div>
        <div *ngIf="systemList.length === 0" class="empty">Loading...</div>
      </div>

      <!-- Level 3: System Detail -->
      <div *ngIf="selectedGalaxy !== null && selectedSystem !== null" class="level">
        <h3>Galaxy {{ selectedGalaxy }} : System {{ selectedSystem }}</h3>
        <div class="slot-grid">
          <div *ngFor="let s of slots" class="slot-tile"
               [class.own]="s.isOwn"
               [class.enemy]="s.occupied && !s.isOwn"
               [class.has-debris]="(s.debrisMetal || 0) > 0 || (s.debrisCrystal || 0) > 0"
               [class.empty-slot]="!s.occupied"
               (click)="clickSlot(s)">
            <div class="slot-number">Slot {{ s.slot }}</div>
            <div *ngIf="!s.occupied" class="slot-empty">Empty</div>
            <ng-container *ngIf="s.occupied">
              <div class="slot-name">{{ s.planetName }}</div>
              <div class="slot-player">{{ s.isOwn ? 'You' : s.playerName }}</div>
              <div class="slot-stats" *ngIf="(s.fleetCount || 0) > 0 || (s.defenseCount || 0) > 0">
                F: {{ s.fleetCount || 0 }} | D: {{ s.defenseCount || 0 }}
              </div>
              <div class="slot-debris" *ngIf="(s.debrisMetal || 0) > 0 || (s.debrisCrystal || 0) > 0">
                ☉ {{ (s.debrisMetal || 0).toLocaleString() }} / {{ (s.debrisCrystal || 0).toLocaleString() }}
              </div>
            </ng-container>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .galaxy-container { padding: 20px; color: #ccc; }
    h2 { color: #ffd700; margin: 0 0 8px 0; }
    h3 { color: #ffd700; margin: 0 0 12px 0; font-size: 14px; }
    .breadcrumb { font-size: 12px; color: #888; margin-bottom: 16px; }
    .crumb-link { color: #4af; cursor: pointer; }
    .crumb-link:hover { text-decoration: underline; }
    .level { background: #1a1a2e; border: 1px solid #333; border-radius: 8px; padding: 16px; }

    .galaxy-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; max-width: 400px; margin: 0 auto; }
    .galaxy-tile { aspect-ratio: 1; display: flex; align-items: center; justify-content: center;
      font-size: 32px; font-weight: bold; border-radius: 12px; cursor: pointer; border: 2px solid transparent;
      transition: transform .1s, border-color .1s; }
    .galaxy-tile:hover { transform: scale(1.05); border-color: #4af; }

    .system-list { font-size: 13px; }
    .system-header { display: flex; padding: 6px 8px; background: #222; border: 1px solid #333; font-weight: bold; color: #ffd700; }
    .system-row { display: flex; padding: 6px 8px; border-bottom: 1px solid #222; cursor: pointer; }
    .system-row:hover { background: #222; }
    .system-row.own-planet { background: #0a2a1a; border-left: 3px solid #4a9; }
    .sys-id { width: 80px; color: #4af; }
    .sys-planets { width: 100px; }
    .sys-status { flex: 1; }
    .sys-status.occupied { color: #4a9; }

    .slot-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; }
    .slot-tile { background: #1a1a2e; border: 1px solid #333; border-radius: 8px; padding: 10px;
      text-align: center; font-size: 12px; cursor: pointer; transition: transform .1s; }
    .slot-tile:hover { transform: scale(1.05); }
    .slot-tile.own { background: #0a2a1a; border-color: #4a9; }
    .slot-tile.enemy { background: #2a1a1a; border-color: #f44; }
    .slot-tile.has-debris { border-color: #fa0; }
    .slot-tile.empty-slot { border-style: dashed; cursor: pointer; }
    .slot-tile.empty-slot:hover { border-color: #4a9; }
    .slot-number { color: #666; font-size: 10px; margin-bottom: 4px; }
    .slot-empty { color: #666; font-style: italic; font-size: 11px; }
    .slot-name { color: #fff; font-weight: bold; font-size: 13px; }
    .slot-player { color: #ffd700; font-size: 11px; }
    .slot-stats { color: #888; font-size: 10px; margin-top: 2px; }
    .slot-debris { color: #fa0; font-size: 10px; }

    .empty { color: #666; font-style: italic; font-size: 13px; padding: 20px; text-align: center; }
  `]
})
export class GalaxyComponent implements OnInit {
  galaxyNumbers = [1,2,3,4,5,6,7,8,9];
  selectedGalaxy: number | null = null;
  selectedSystem: number | null = null;
  systemList: SystemInfo[] = [];
  slots: SlotInfo[] = [];

  constructor(
    private gameService: GameService,
    private router: Router
  ) {}

  ngOnInit() {
    this.selectGalaxy(1);
  }

  getGalaxyColor(g: number): string {
    const colors = [
      '#1a3a5c', '#1a2a3c', '#2a1a2c', '#2a2a1c', '#1a3a2c',
      '#3a1a1c', '#1a2a3c', '#2a1a3c', '#3a2a1c'
    ];
    return colors[(g - 1) % colors.length];
  }

  selectGalaxy(g: number) {
    this.selectedGalaxy = g;
    this.selectedSystem = null;
    this.systemList = [];
    this.slots = [];
    this.gameService.getSystemList(g).subscribe(list => {
      this.systemList = list;
    });
  }

  selectSystem(systemId: number) {
    this.selectedSystem = systemId;
    this.slots = [];
    this.gameService.getSystemDetail(this.selectedGalaxy!, systemId).subscribe(detail => {
      this.slots = detail.slots;
    });
  }

  backToGalaxies() {
    this.selectedGalaxy = null;
    this.selectedSystem = null;
    this.systemList = [];
    this.slots = [];
  }

  backToSystems() {
    this.selectedSystem = null;
    this.slots = [];
  }

  clickSlot(slot: SlotInfo) {
    if (!slot.occupied) {
      this.router.navigate(['/fleet'], {
        queryParams: {
          galaxy: this.selectedGalaxy,
          systemId: this.selectedSystem,
          slot: slot.slot,
          mission: 'COLONIZE'
        }
      });
    } else if (!slot.isOwn) {
      this.router.navigate(['/fleet'], {
        queryParams: {
          targetPlanetId: slot.planetId,
          mission: 'ATTACK'
        }
      });
    }
  }
}
