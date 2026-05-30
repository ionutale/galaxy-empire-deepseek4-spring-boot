import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { GameService } from './game.service';

describe('GameService', () => {
  let service: GameService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [GameService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(GameService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('fetches the quest list', () => {
    service.getQuests().subscribe();
    const req = httpMock.expectOne('/api/game/quests');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('claims a quest reward by progress id', () => {
    service.claimQuestReward(5).subscribe();
    const req = httpMock.expectOne('/api/game/quests/5/claim');
    expect(req.request.method).toBe('POST');
    req.flush({ success: true });
  });

  it('fetches the player planets', () => {
    service.getMyPlanets().subscribe();
    const req = httpMock.expectOne('/api/game/planets/my');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('posts a building upgrade', () => {
    service.upgradeBuilding(1, 3).subscribe();
    const req = httpMock.expectOne('/api/game/planets/1/buildings/3/upgrade');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('fetches dark matter balance for a player', () => {
    service.getDarkMatter(7).subscribe();
    const req = httpMock.expectOne('/api/game/players/7/dark-matter');
    expect(req.request.method).toBe('GET');
    req.flush({ darkMatter: 100 });
  });

  it('starts research for a technology', () => {
    service.startResearch('ENERGY_TECH').subscribe();
    const req = httpMock.expectOne('/api/game/technologies/ENERGY_TECH/research');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });
});
