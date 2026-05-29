import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthResponse } from '../models/models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private tokenKey = 'galaxy_empire_token';
  private playerIdKey = 'galaxy_empire_player_id';
  private usernameKey = 'galaxy_empire_username';

  isLoggedIn = signal(false);

  constructor(private http: HttpClient, private router: Router) {
    this.isLoggedIn.set(!!localStorage.getItem(this.tokenKey));
  }

  register(username: string, password: string) {
    return this.http.post<AuthResponse>(`${environment.apiUrl}/auth/register`, { username, password });
  }

  login(username: string, password: string) {
    return this.http.post<AuthResponse>(`${environment.apiUrl}/auth/login`, { username, password });
  }

  saveSession(response: AuthResponse) {
    localStorage.setItem(this.tokenKey, response.token);
    localStorage.setItem(this.playerIdKey, response.playerId.toString());
    localStorage.setItem(this.usernameKey, response.username);
    this.isLoggedIn.set(true);
  }

  logout() {
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.playerIdKey);
    localStorage.removeItem(this.usernameKey);
    this.isLoggedIn.set(false);
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  getPlayerId(): number | null {
    const id = localStorage.getItem(this.playerIdKey);
    return id ? parseInt(id, 10) : null;
  }

  getUsername(): string | null {
    return localStorage.getItem(this.usernameKey);
  }
}
