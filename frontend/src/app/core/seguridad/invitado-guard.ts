import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { SesionService } from '../servicios/sesion-service';

export const RUTA_PRINCIPAL = '/procesos';

export const invitadoGuard: CanActivateFn = () => {
  const principal = inject(Router).createUrlTree([RUTA_PRINCIPAL]);
  return inject(SesionService)
    .asegurar()
    .pipe(
      map((sesion) => (sesion.autenticado ? principal : true)),
      catchError(() => of(true)),
    );
};
