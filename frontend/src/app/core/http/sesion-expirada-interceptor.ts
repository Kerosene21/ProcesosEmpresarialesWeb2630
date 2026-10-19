import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { RUTA_INICIO_SESION } from '../seguridad/sesion-guard';
import { RUTA_SESION, SesionService } from '../servicios/sesion-service';

export const sesionExpiradaInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  const sesion = inject(SesionService);
  const router = inject(Router);
  return siguiente(peticion).pipe(
    catchError((error: unknown) => {
      if (esSesionExpirada(error, peticion.url) && sesion.autenticado()) {
        sesion.olvidar();
        if (!router.url.startsWith(RUTA_INICIO_SESION)) {
          void router.navigate([RUTA_INICIO_SESION], { queryParams: { retorno: router.url, motivo: 'expirada' } });
        }
      }
      return throwError(() => error);
    }),
  );
};

function esSesionExpirada(error: unknown, url: string): boolean {
  return error instanceof HttpErrorResponse && error.status === 401 && !url.startsWith(RUTA_SESION);
}
