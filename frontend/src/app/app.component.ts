import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="app">
      <nav *ngIf="auth.isLoggedIn()" class="top-nav">
        <a routerLink="/overview" routerLinkActive="active">Overview</a>
        <a routerLink="/resources" routerLinkActive="active">Resources</a>
        <a routerLink="/facilities" routerLinkActive="active">Facilities</a>
        <span class="spacer"></span>
        <span class="username">{{ auth.getUsername() }}</span>
        <button class="logout-btn" (click)="auth.logout()">Logout</button>
      </nav>
      <main>
        <router-outlet />
      </main>
    </div>
  `,
  styles: [`
    .app { min-height: 100vh; background: #0a0a2e; color: #ccc; font-family: sans-serif; }
    .top-nav {
      display: flex; align-items: center; gap: 0.5rem;
      background: #12123a; padding: 0.5rem 1rem; border-bottom: 1px solid #2a2a5e;
    }
    .top-nav a {
      color: #888; text-decoration: none; padding: 0.3rem 0.6rem;
      border-radius: 4px; font-size: 0.9rem;
    }
    .top-nav a:hover { color: #fff; background: #2a2a5e; }
    .top-nav a.active { color: #ffd700; background: #1a1a4e; }
    .spacer { flex: 1; }
    .username { color: #888; font-size: 0.85rem; }
    .logout-btn {
      background: transparent; color: #f44; border: 1px solid #f44;
      padding: 0.2rem 0.5rem; border-radius: 4px; cursor: pointer;
      font-size: 0.8rem;
    }
    .logout-btn:hover { background: #441; }
    main { padding: 0; }
  `]
})
export class AppComponent {
  constructor(public auth: AuthService) {}
}
