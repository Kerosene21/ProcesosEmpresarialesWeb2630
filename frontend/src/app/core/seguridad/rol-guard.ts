import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { RolUsuario } from '../modelos/sesion';
import { SesionService } from '../servicios/sesion-service';
import { RUTA_PRINCIPAL } from './invitado-guard';
import { RUTA_INICIO_SESION } from './sesion-guard';

export const rolGuard: CanActivateFn = (ruta) => {
  const roles: readonly RolUsuario[] = ruta.data['roles'] ?? [];
  const sesion = inject(SesionService);
  const router = inject(Router);
  return sesion.asegurar().pipe(
    map(() => (sesion.tieneRol(roles) ? true : router.createUrlTree([RUTA_PRINCIPAL]))),
    catchError(() => of(router.createUrlTree([RUTA_INICIO_SESION]))),
  );
};
