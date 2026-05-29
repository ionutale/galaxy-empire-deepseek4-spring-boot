import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/services/auth.service';
import { GameService } from '../core/services/game.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="login-container">
      <div class="login-card">
        <h1>Galaxy Empire</h1>
        <h2>{{ isRegister ? 'Register' : 'Login' }}</h2>
        <form (ngSubmit)="submit()">
          <input [(ngModel)]="username" name="username" placeholder="Username" required />
          <input [(ngModel)]="password" name="password" type="password" placeholder="Password" required />
          <button type="submit">{{ isRegister ? 'Register' : 'Login' }}</button>
        </form>
        <p class="error" *ngIf="error">{{ error }}</p>
        <button class="toggle" (click)="toggleMode()">
          {{ isRegister ? 'Already have an account? Login' : 'New? Register here' }}
        </button>
      </div>
    </div>
  `,
  styles: [`
    .login-container {
      display: flex; justify-content: center; align-items: center;
      height: 100vh; background: #0a0a2e;
    }
    .login-card {
      background: #1a1a4e; padding: 2rem; border-radius: 8px;
      width: 320px; text-align: center;
    }
    h1 { color: #ffd700; margin-bottom: 0.5rem; font-size: 1.8rem; }
    h2 { color: #ccc; margin-bottom: 1rem; }
    input {
      width: 100%; padding: 0.6rem; margin-bottom: 0.5rem;
      background: #2a2a5e; border: 1px solid #444; color: #fff;
      border-radius: 4px; box-sizing: border-box;
    }
    button {
      width: 100%; padding: 0.7rem; background: #ffd700; color: #000;
      border: none; border-radius: 4px; font-weight: bold; cursor: pointer;
    }
    button:hover { background: #e6c200; }
    .toggle {
      background: transparent; color: #888; margin-top: 0.5rem;
      font-size: 0.85rem;
    }
    .toggle:hover { color: #ccc; background: transparent; }
    .error { color: #ff4444; margin-top: 0.5rem; }
  `]
})
export class LoginComponent {
  username = '';
  password = '';
  isRegister = false;
  error = '';

  constructor(
    private auth: AuthService,
    private game: GameService,
    private router: Router
  ) {}

  toggleMode() {
    this.isRegister = !this.isRegister;
    this.error = '';
  }

  submit() {
    this.error = '';
    const obs = this.isRegister
      ? this.auth.register(this.username, this.password)
      : this.auth.login(this.username, this.password);

    obs.subscribe({
      next: (response) => {
        this.auth.saveSession(response);
        if (this.isRegister) {
          this.game.createPlanet().subscribe({
            next: () => this.router.navigate(['/overview']),
            error: () => this.router.navigate(['/overview'])
          });
        } else {
          this.router.navigate(['/overview']);
        }
      },
      error: (err) => {
        this.error = err.error?.error || 'Something went wrong';
      }
    });
  }
}
