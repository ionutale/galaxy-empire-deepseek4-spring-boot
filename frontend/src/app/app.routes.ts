import { Routes } from '@angular/router';
import { AuthGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./login/login.component').then(m => m.LoginComponent) },
  { path: 'overview', loadComponent: () => import('./overview/overview.component').then(m => m.OverviewComponent), canActivate: [AuthGuard] },
  { path: 'resources', loadComponent: () => import('./resources/resources.component').then(m => m.ResourcesComponent), canActivate: [AuthGuard] },
  { path: 'facilities', loadComponent: () => import('./resources/resources.component').then(m => m.ResourcesComponent), canActivate: [AuthGuard] },
  { path: 'research', loadComponent: () => import('./research/research.component').then(m => m.ResearchComponent), canActivate: [AuthGuard] },
  { path: 'shipyard', loadComponent: () => import('./shipyard/shipyard.component').then(m => m.ShipyardComponent), canActivate: [AuthGuard] },
  { path: 'fleet', loadComponent: () => import('./fleet/fleet.component').then(m => m.FleetComponent), canActivate: [AuthGuard] },
  { path: 'galaxy', loadComponent: () => import('./galaxy/galaxy.component').then(m => m.GalaxyComponent), canActivate: [AuthGuard] },
  { path: 'quests', loadComponent: () => import('./quest/quest.component').then(m => m.QuestComponent), canActivate: [AuthGuard] },
  { path: '', redirectTo: '/overview', pathMatch: 'full' },
  { path: '**', redirectTo: '/overview' }
];
