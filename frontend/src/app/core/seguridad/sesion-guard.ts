import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { SesionService } from '../servicios/sesion-service';

export const RUTA_INICIO_SESION = '/iniciar-sesion';

export const sesionGuard: CanActivateFn = (_ruta, estado) => {
  const inicio = inject(Router).createUrlTree([RUTA_INICIO_SESION], { queryParams: { retorno: estado.url } });
  return inject(SesionService)
    .asegurar()
    .pipe(
      map((sesion) => (sesion.autenticado ? true : inicio)),
      catchError(() => of(inicio)),
    );
};
