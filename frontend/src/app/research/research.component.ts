import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { GameService } from '../core/services/game.service';
import { WebSocketService } from '../core/services/web-socket.service';
import { Technology, ResearchQueue } from '../core/models/models';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-research',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="research-container">
      <h2>Research Lab</h2>

      <div class="active-research" *ngIf="activeResearch">
        <h3>Active Research</h3>
        <p>{{ getDisplayName(activeResearch.technology) }} → Level {{ activeResearch.targetLevel }}
          <button class="speed-up" (click)="speedUpResearch(activeResearch.technology)">Speed Up</button>
        </p>
        <div class="progress-bar">
          <div class="progress-fill" [style.width.%]="getProgressPercent()"></div>
        </div>
      </div>

      <div class="tech-grid">
        <div *ngFor="let tech of technologies"
             class="tech-card"
             [class.can-research]="tech.prerequisitesMet && !tech.isResearching"
             [class.no-prereqs]="!tech.prerequisitesMet"
             [class.researching]="tech.isResearching"
             (click)="research(tech)">
          <div class="tech-name">{{ getDisplayName(tech.technology) }}</div>
          <div class="tech-level">Level {{ tech.level }}</div>
          <div class="tech-cost" *ngIf="tech.level < 50">
            <span *ngIf="tech.metalCost > 0">M: {{ tech.metalCost.toLocaleString() }}</span>
            <span *ngIf="tech.crystalCost > 0">C: {{ tech.crystalCost.toLocaleString() }}</span>
            <span *ngIf="tech.gasCost > 0">G: {{ tech.gasCost.toLocaleString() }}</span>
          </div>
          <div class="tech-time">{{ formatTime(tech.timeSeconds) }}</div>
          <div class="tech-status" *ngIf="tech.isResearching">Researching...</div>
          <div class="tech-status" *ngIf="!tech.prerequisitesMet && !tech.isResearching">Prerequisites not met</div>
          <div class="tech-status" *ngIf="tech.prerequisitesMet && !tech.isResearching && tech.level < 50">Research</div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .research-container { padding: 20px; color: #ccc; }
    .tech-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; margin-top: 16px; }
    .tech-card { background: #1a1a2e; border: 1px solid #333; border-radius: 8px; padding: 16px; cursor: pointer; transition: all 0.2s; }
    .tech-card.can-research { border-color: #4a9; }
    .tech-card.can-research:hover { border-color: #6cf; background: #1e2a3e; }
    .tech-card.no-prereqs { opacity: 0.5; cursor: not-allowed; }
    .tech-card.researching { border-color: #fa0; }
    .tech-name { font-size: 14px; font-weight: bold; color: #fff; }
    .tech-level { font-size: 12px; color: #888; margin-top: 4px; }
    .tech-cost { font-size: 11px; color: #aaa; margin-top: 8px; }
    .tech-cost span { margin-right: 8px; }
    .tech-time { font-size: 11px; color: #666; margin-top: 4px; }
    .tech-status { font-size: 11px; margin-top: 8px; }
    .active-research { background: #1a2a1a; border: 1px solid #4a9; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
    .progress-bar { height: 8px; background: #333; border-radius: 4px; margin-top: 8px; overflow: hidden; }
    .progress-fill { height: 100%; background: #4a9; transition: width 1s; }
    .speed-up { background: #7c3aed; color: #fff; border: none; padding: 2px 8px; border-radius: 4px; cursor: pointer; font-size: 11px; margin-left: 8px; }
    .speed-up:hover { background: #6d28d9; }
  `]
})
export class ResearchComponent implements OnInit, OnDestroy {
  technologies: Technology[] = [];
  activeResearch: ResearchQueue | null = null;
  private wsSubscription?: Subscription;

  constructor(
    private gameService: GameService,
    private ws: WebSocketService
  ) {}

  ngOnInit() {
    this.loadTechnologies();
    this.ws.connect();
    this.ws.subscribe('/topic/research/*', () => this.loadTechnologies());
  }

  ngOnDestroy() {
    this.wsSubscription?.unsubscribe();
  }

  loadTechnologies() {
    this.gameService.getTechnologies().subscribe(techs => {
      this.technologies = techs;
    });
    this.gameService.getResearchQueue().subscribe(queue => {
      if (queue.length > 0) {
        this.activeResearch = queue[0];
      } else {
        this.activeResearch = null;
      }
    });
  }

  speedUpResearch(technology: string) {
    this.gameService.speedUpResearch(technology).subscribe(() => {
      this.loadTechnologies();
    });
  }

  research(tech: Technology) {
    if (tech.prerequisitesMet && !tech.isResearching) {
      this.gameService.startResearch(tech.technology).subscribe(() => {
        this.loadTechnologies();
      });
    }
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

  getProgressPercent(): number {
    if (!this.activeResearch) return 0;
    return 50;
  }
}
