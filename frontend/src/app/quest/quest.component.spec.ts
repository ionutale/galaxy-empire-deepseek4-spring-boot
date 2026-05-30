import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { QuestComponent } from './quest.component';
import { GameService } from '../core/services/game.service';
import { QuestInfo } from '../core/models/models';

function quest(overrides: Partial<QuestInfo>): QuestInfo {
  return {
    progressId: 1,
    questDefinitionId: 1,
    title: 'Quest',
    description: 'desc',
    icon: 'building',
    questType: 'ACHIEVEMENT',
    category: 'BUILDING',
    progress: 0,
    target: 5,
    rewardType: 'DARK_MATTER',
    rewardAmount: 10,
    completed: false,
    claimed: false,
    ...overrides,
  };
}

describe('QuestComponent', () => {
  let component: QuestComponent;
  let fixture: ComponentFixture<QuestComponent>;
  let game: jasmine.SpyObj<GameService>;

  const quests: QuestInfo[] = [
    quest({ questDefinitionId: 1, questType: 'ACHIEVEMENT', title: 'Builder', progress: 2, target: 4 }),
    quest({ questDefinitionId: 2, questType: 'DAILY', title: 'Daily Build' }),
    quest({ questDefinitionId: 3, questType: 'ACHIEVEMENT', title: 'Done', progressId: 9, completed: true }),
  ];

  beforeEach(async () => {
    game = jasmine.createSpyObj('GameService', ['getQuests', 'claimQuestReward']);
    game.getQuests.and.returnValue(of(quests));
    game.claimQuestReward.and.returnValue(of({ success: true }));

    await TestBed.configureTestingModule({
      imports: [QuestComponent],
      providers: [{ provide: GameService, useValue: game }],
    }).compileComponents();

    fixture = TestBed.createComponent(QuestComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads quests on init', () => {
    expect(game.getQuests).toHaveBeenCalled();
    expect(component.quests.length).toBe(3);
  });

  it('shows achievements on the default tab', () => {
    const titles = component.filteredQuests().map(q => q.title);
    expect(titles).toContain('Builder');
    expect(titles).not.toContain('Daily Build');
  });

  it('shows daily quests when the dailies tab is active', () => {
    component.tab = 'dailies';
    const titles = component.filteredQuests().map(q => q.title);
    expect(titles).toEqual(['Daily Build']);
  });

  it('computes progress percentage capped at 100', () => {
    expect(component.pct(quest({ progress: 2, target: 4 }))).toBe(50);
    expect(component.pct(quest({ progress: 10, target: 4 }))).toBe(100);
  });

  it('maps known icons and falls back for unknown ones', () => {
    expect(component.getIcon('research')).toBe('🔬');
    expect(component.getIcon('mystery')).toBe('📋');
  });

  it('claims a reward and reloads the quest list', () => {
    game.getQuests.calls.reset();
    component.claim(9);
    expect(game.claimQuestReward).toHaveBeenCalledWith(9);
    expect(game.getQuests).toHaveBeenCalled();
  });
});
