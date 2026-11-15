import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { SesionService } from '../servicios/sesion-service';
import { requiereBearer } from './rutas-api';

export const autorizacionInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  const token = inject(SesionService).tokenAcceso();
  if (token === null || !requiereBearer(peticion.url)) {
    return siguiente(peticion);
  }
  return siguiente(peticion.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
