import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router } from '@angular/router';

import { AuthService } from './auth.service';
import { AuthResponse } from '../models/models';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    localStorage.clear();
    router = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
      ],
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('starts logged out when no token is stored', () => {
    expect(service.isLoggedIn()).toBeFalse();
  });

  it('posts credentials to the register endpoint', () => {
    service.register('alice', 'pw').subscribe();
    const req = httpMock.expectOne('/api/auth/register');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'alice', password: 'pw' });
    req.flush({ token: 't', playerId: 1, username: 'alice' });
  });

  it('posts credentials to the login endpoint', () => {
    service.login('alice', 'pw').subscribe();
    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    req.flush({ token: 't', playerId: 1, username: 'alice' });
  });

  it('persists the session and flips the logged-in signal', () => {
    const response: AuthResponse = { token: 'jwt', playerId: 42, username: 'alice' };
    service.saveSession(response);

    expect(service.isLoggedIn()).toBeTrue();
    expect(service.getToken()).toBe('jwt');
    expect(service.getPlayerId()).toBe(42);
    expect(service.getUsername()).toBe('alice');
  });

  it('clears the session and redirects to login on logout', () => {
    service.saveSession({ token: 'jwt', playerId: 42, username: 'alice' });
    service.logout();

    expect(service.isLoggedIn()).toBeFalse();
    expect(service.getToken()).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('returns null player id when none is stored', () => {
    expect(service.getPlayerId()).toBeNull();
  });
});
