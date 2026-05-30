import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { GameService } from '../core/services/game.service';
import { QuestInfo } from '../core/models/models';

@Component({
  selector: 'app-quest',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="quest-view">
      <h2>Quests</h2>

      <div class="tab-bar">
        <button [class.active]="tab === 'achievements'" (click)="tab = 'achievements'">Achievements</button>
        <button [class.active]="tab === 'dailies'" (click)="tab = 'dailies'">Daily Quests</button>
      </div>

      <div class="quest-list">
        <div *ngFor="let q of filteredQuests()" class="quest-card"
             [class.completed]="q.completed"
             [class.claimed]="q.claimed">
          <div class="quest-icon">{{ getIcon(q.icon) }}</div>
          <div class="quest-body">
            <div class="quest-title">{{ q.title }}</div>
            <div class="quest-desc">{{ q.description }}</div>
            <div class="progress-bar">
              <div class="progress-fill" [style.width.%]="pct(q)"></div>
            </div>
            <div class="quest-progress">{{ q.progress }} / {{ q.target }}</div>
          </div>
          <div class="quest-reward">
            <div class="reward-icon">{{ q.rewardType === 'DARK_MATTER' ? '◆' : '●' }}</div>
            <div class="reward-amount">+{{ q.rewardAmount }}</div>
            <button *ngIf="q.completed && !q.claimed" class="claim-btn" (click)="claim(q.progressId!)">Claim</button>
            <span *ngIf="q.claimed" class="claimed-label">Done</span>
          </div>
        </div>
        <div *ngIf="filteredQuests().length === 0" class="empty">No quests available.</div>
      </div>
    </div>
  `,
  styles: [`
    .quest-view { padding: 20px; color: #ccc; max-width: 700px; margin: 0 auto; }
    h2 { color: #ffd700; margin: 0 0 12px 0; }
    .tab-bar { display: flex; gap: 4px; margin-bottom: 16px; }
    .tab-bar button { padding: 6px 16px; background: #1a1a2e; color: #888; border: 1px solid #333; border-radius: 4px 4px 0 0; cursor: pointer; }
    .tab-bar button.active { color: #ffd700; background: #222; border-bottom: 2px solid #ffd700; }
    .quest-list { display: flex; flex-direction: column; gap: 8px; }
    .quest-card { display: flex; gap: 12px; background: #1a1a2e; border: 1px solid #333; border-radius: 8px; padding: 12px; align-items: center; }
    .quest-card.completed { border-color: #4a9; }
    .quest-card.claimed { opacity: 0.5; }
    .quest-icon { font-size: 24px; width: 40px; text-align: center; }
    .quest-body { flex: 1; }
    .quest-title { color: #fff; font-weight: bold; font-size: 14px; }
    .quest-desc { color: #888; font-size: 12px; margin: 2px 0 6px; }
    .progress-bar { height: 6px; background: #333; border-radius: 3px; overflow: hidden; }
    .progress-fill { height: 100%; background: #4af; border-radius: 3px; transition: width 0.3s; }
    .quest-progress { color: #888; font-size: 11px; margin-top: 2px; }
    .quest-reward { text-align: center; min-width: 60px; }
    .reward-icon { font-size: 18px; color: #a855f7; }
    .reward-amount { color: #ffd700; font-size: 13px; font-weight: bold; }
    .claim-btn { margin-top: 4px; padding: 4px 12px; background: #7c3aed; color: #fff; border: none; border-radius: 4px; cursor: pointer; font-size: 11px; }
    .claim-btn:hover { background: #6d28d9; }
    .claimed-label { color: #4a9; font-size: 11px; }
    .empty { color: #666; text-align: center; padding: 20px; }
  `]
})
export class QuestComponent implements OnInit {
  tab: 'achievements' | 'dailies' = 'achievements';
  quests: QuestInfo[] = [];

  constructor(private game: GameService) {}

  ngOnInit() {
    this.loadQuests();
  }

  loadQuests() {
    this.game.getQuests().subscribe(data => {
      this.quests = data;
    });
  }

  filteredQuests() {
    return this.quests.filter(q => this.tab === 'dailies' ? q.questType === 'DAILY' : q.questType === 'ACHIEVEMENT');
  }

  pct(q: QuestInfo): number {
    return Math.min(100, (q.progress / q.target) * 100);
  }

  getIcon(icon: string): string {
    const icons: Record<string, string> = {
      'building': '🏗️', 'research': '🔬', 'combat': '⚔️', 'colony': '🚀',
      'daily_build': '🏗️', 'daily_research': '🔬', 'daily_combat': '⚔️', 'daily_fleet': '🚀'
    };
    return icons[icon] || '📋';
  }

  claim(progressId: number) {
    this.game.claimQuestReward(progressId).subscribe(() => {
      this.loadQuests();
    });
  }
}
