import { Routes } from '@angular/router';

const APLICACION = 'Procesos Empresariales';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'procesos' },
  {
    path: 'procesos',
    title: `Procesos · ${APLICACION}`,
    loadComponent: () => import('./paginas/lista-procesos/lista-procesos').then((m) => m.ListaProcesos),
  },
  {
    path: 'procesos/nuevo',
    title: `Nuevo proceso · ${APLICACION}`,
    loadComponent: () => import('./paginas/formulario-proceso/formulario-proceso').then((m) => m.FormularioProceso),
  },
  {
    path: 'procesos/:id',
    title: `Detalle del proceso · ${APLICACION}`,
    loadComponent: () => import('./paginas/detalle-proceso/detalle-proceso').then((m) => m.DetalleProceso),
  },
  {
    path: 'procesos/:id/editar',
    title: `Editar proceso · ${APLICACION}`,
    loadComponent: () => import('./paginas/formulario-proceso/formulario-proceso').then((m) => m.FormularioProceso),
  },
  { path: '**', redirectTo: 'procesos' },
];
