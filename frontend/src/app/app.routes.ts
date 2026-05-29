import { Routes } from '@angular/router';
import { AuthGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./login/login.component').then(m => m.LoginComponent) },
  { path: 'overview', loadComponent: () => import('./overview/overview.component').then(m => m.OverviewComponent), canActivate: [AuthGuard] },
  { path: 'resources', loadComponent: () => import('./resources/resources.component').then(m => m.ResourcesComponent), canActivate: [AuthGuard] },
  { path: 'facilities', loadComponent: () => import('./resources/resources.component').then(m => m.ResourcesComponent), canActivate: [AuthGuard] },
  { path: '', redirectTo: '/overview', pathMatch: 'full' },
  { path: '**', redirectTo: '/overview' }
];
