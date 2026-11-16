import { Routes } from '@angular/router';

import { ROLES_ESCRITURA } from './core/modelos/sesion';
import { invitadoGuard } from './core/seguridad/invitado-guard';
import { rolGuard } from './core/seguridad/rol-guard';
import { sesionGuard } from './core/seguridad/sesion-guard';

const APLICACION = 'Procesos Empresariales';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'procesos' },
  {
    path: 'iniciar-sesion',
    title: `Iniciar sesión · ${APLICACION}`,
    canActivate: [invitadoGuard],
    loadComponent: () => import('./paginas/inicio-sesion/inicio-sesion').then((m) => m.InicioSesion),
  },
  {
    path: 'registro-empresa',
    title: `Registrar empresa · ${APLICACION}`,
    canActivate: [invitadoGuard],
    loadComponent: () => import('./paginas/registro-empresa/registro-empresa').then((m) => m.RegistroEmpresa),
  },
  {
    path: '',
    canActivateChild: [sesionGuard],
    children: [
      {
        path: 'mi-empresa',
        title: `Mi empresa · ${APLICACION}`,
        loadComponent: () => import('./paginas/mi-empresa/mi-empresa').then((m) => m.MiEmpresa),
      },
      {
        path: 'procesos',
        title: `Procesos · ${APLICACION}`,
        loadComponent: () => import('./paginas/lista-procesos/lista-procesos').then((m) => m.ListaProcesos),
      },
      {
        path: 'procesos/nuevo',
        title: `Nuevo proceso · ${APLICACION}`,
        canActivate: [rolGuard],
        data: { roles: ROLES_ESCRITURA },
        loadComponent: () =>
          import('./paginas/formulario-proceso/formulario-proceso').then((m) => m.FormularioProceso),
      },
      {
        path: 'procesos/:id',
        title: `Detalle del proceso · ${APLICACION}`,
        loadComponent: () => import('./paginas/detalle-proceso/detalle-proceso').then((m) => m.DetalleProceso),
      },
      {
        path: 'procesos/:id/editar',
        title: `Editar proceso · ${APLICACION}`,
        canActivate: [rolGuard],
        data: { roles: ROLES_ESCRITURA },
        loadComponent: () =>
          import('./paginas/formulario-proceso/formulario-proceso').then((m) => m.FormularioProceso),
      },
      {
        path: 'procesos/:id/modelador',
        title: `Modelador · ${APLICACION}`,
        canActivate: [rolGuard],
        data: { roles: ROLES_ESCRITURA },
        loadComponent: () =>
          import('./paginas/modelador-proceso/modelador-proceso').then((m) => m.ModeladorProceso),
      },
      {
        path: 'roles-proceso',
        title: `Roles de proceso · ${APLICACION}`,
        loadComponent: () => import('./paginas/lista-roles/lista-roles').then((m) => m.ListaRoles),
      },
      {
        path: 'roles-proceso/nuevo',
        title: `Nuevo rol de proceso · ${APLICACION}`,
        canActivate: [rolGuard],
        data: { roles: ['ADMINISTRADOR'] },
        loadComponent: () => import('./paginas/formulario-rol/formulario-rol').then((m) => m.FormularioRol),
      },
      {
        path: 'roles-proceso/:id',
        title: `Rol de proceso · ${APLICACION}`,
        loadComponent: () => import('./paginas/detalle-rol/detalle-rol').then((m) => m.DetalleRol),
      },
      {
        path: 'roles-proceso/:id/editar',
        title: `Editar rol de proceso · ${APLICACION}`,
        canActivate: [rolGuard],
        data: { roles: ROLES_ESCRITURA },
        loadComponent: () => import('./paginas/formulario-rol/formulario-rol').then((m) => m.FormularioRol),
      },
      {
        path: 'usuarios',
        title: `Usuarios · ${APLICACION}`,
        canActivate: [rolGuard],
        data: { roles: ['ADMINISTRADOR'] },
        loadComponent: () => import('./paginas/lista-usuarios/lista-usuarios').then((m) => m.ListaUsuarios),
      },
      {
        path: 'usuarios/nuevo',
        title: `Nuevo usuario · ${APLICACION}`,
        canActivate: [rolGuard],
        data: { roles: ['ADMINISTRADOR'] },
        loadComponent: () =>
          import('./paginas/formulario-usuario/formulario-usuario').then((m) => m.FormularioUsuario),
      },
      {
        path: 'usuarios/:id',
        title: `Usuario · ${APLICACION}`,
        canActivate: [rolGuard],
        data: { roles: ['ADMINISTRADOR'] },
        loadComponent: () => import('./paginas/detalle-usuario/detalle-usuario').then((m) => m.DetalleUsuario),
      },
      {
        path: 'procesos/:id/historial',
        title: `Historial del proceso · ${APLICACION}`,
        loadComponent: () =>
          import('./paginas/historial-proceso/historial-proceso').then((m) => m.HistorialProceso),
      },
    ],
  },
  { path: '**', redirectTo: 'procesos' },
];
