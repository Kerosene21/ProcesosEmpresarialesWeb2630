import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError } from 'rxjs';

import { parametrosDe } from '../http/parametros';
import { lanzarFallo } from '../http/traducir-fallo';
import { Pagina } from '../modelos/pagina';
import {
  CrearRolProceso,
  EditarRolProceso,
  FiltroRolesProceso,
  HistorialRolProceso,
  RolProceso,
  RolProcesoResumen,
} from '../modelos/rol-proceso';

@Injectable({
  providedIn: 'root',
})
export class RolProcesoService {
  private readonly http = inject(HttpClient);
  private readonly ruta = '/api/roles-proceso';

  listar(filtro: FiltroRolesProceso = {}): Observable<Pagina<RolProcesoResumen>> {
    return this.http
      .get<Pagina<RolProcesoResumen>>(this.ruta, { params: parametrosDe(filtro) })
      .pipe(catchError(lanzarFallo));
  }

  obtener(id: number): Observable<RolProcesoResumen> {
    return this.http.get<RolProcesoResumen>(`${this.ruta}/${id}`).pipe(catchError(lanzarFallo));
  }

  historial(id: number): Observable<HistorialRolProceso[]> {
    return this.http.get<HistorialRolProceso[]>(`${this.ruta}/${id}/historial`).pipe(catchError(lanzarFallo));
  }

  crear(datos: CrearRolProceso): Observable<RolProceso> {
    return this.http.post<RolProceso>(this.ruta, datos).pipe(catchError(lanzarFallo));
  }

  editar(id: number, datos: EditarRolProceso): Observable<RolProceso> {
    return this.http.put<RolProceso>(`${this.ruta}/${id}`, datos).pipe(catchError(lanzarFallo));
  }

  previsualizarEliminacion(id: number): Observable<RolProcesoResumen> {
    return this.http.get<RolProcesoResumen>(`${this.ruta}/${id}/eliminacion`).pipe(catchError(lanzarFallo));
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.ruta}/${id}`).pipe(catchError(lanzarFallo));
  }
}
