import {
  HttpContext,
  HttpContextToken,
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';

import { RUTA_INICIO_SESION } from '../seguridad/sesion-guard';
import { CODIGO_SESION_DESCARTADA, SesionService } from '../servicios/sesion-service';
import { esRutaDeCredenciales, rutaDeApiPropia } from './rutas-api';
import { esFalloHttp } from './traducir-fallo';

const REINTENTO = new HttpContextToken<boolean>(() => false);

export const sesionExpiradaInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  const sesion = inject(SesionService);
  const router = inject(Router);

  const expirar = (): void => {
    if (!sesion.autenticado()) {
      return;
    }
    sesion.olvidar();
    if (!router.url.startsWith(RUTA_INICIO_SESION)) {
      void router.navigate([RUTA_INICIO_SESION], {
        queryParams: { retorno: router.url, motivo: 'expirada' },
      });
    }
  };

  return siguiente(peticion).pipe(
    catchError((error: unknown) => {
      if (!esRenovable(error, peticion) || !sesion.autenticado()) {
        return throwError(() => error);
      }
      return sesion.renovar().pipe(
        catchError((fallo: unknown) => {
          if (!esFalloDeConexion(fallo) && !esSesionDescartada(fallo)) {
            expirar();
          }
          return throwError(() => error);
        }),
        switchMap(() =>
          siguiente(comoReintento(peticion)).pipe(
            catchError((errorReintento: unknown) => {
              if (esNoAutenticado(errorReintento)) {
                expirar();
              }
              return throwError(() => errorReintento);
            }),
          ),
        ),
      );
    }),
  );
};

function esRenovable(error: unknown, peticion: HttpRequest<unknown>): boolean {
  if (!esNoAutenticado(error) || peticion.context.get(REINTENTO)) {
    return false;
  }
  const ruta = rutaDeApiPropia(peticion.url);
  return ruta !== null && !esRutaDeCredenciales(ruta);
}

function esNoAutenticado(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 401;
}

function esSesionDescartada(fallo: unknown): boolean {
  return esFalloHttp(fallo) && fallo.codigo === CODIGO_SESION_DESCARTADA;
}

function esFalloDeConexion(fallo: unknown): boolean {
  return esFalloHttp(fallo)
    ? fallo.estado === 0
    : fallo instanceof HttpErrorResponse && fallo.status === 0;
}

function comoReintento(peticion: HttpRequest<unknown>): HttpRequest<unknown> {
  const contexto = new HttpContext();
  for (const clave of peticion.context.keys()) {
    contexto.set(clave, peticion.context.get(clave));
  }
  return peticion.clone({ context: contexto.set(REINTENTO, true) });
}
