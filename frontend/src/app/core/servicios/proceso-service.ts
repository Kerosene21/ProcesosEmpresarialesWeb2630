import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';

import { traducirFallo } from '../http/traducir-fallo';
import { DiagramaProceso } from '../modelos/diagrama';
import { Pagina } from '../modelos/pagina';
import { CrearProceso, EditarProceso, FiltroProcesos, Proceso, ProcesoResumen } from '../modelos/proceso';

@Injectable({
  providedIn: 'root',
})
export class ProcesoService {
  private readonly http = inject(HttpClient);
  private readonly ruta = '/api/procesos';

  listar(filtro: FiltroProcesos = {}): Observable<Pagina<ProcesoResumen>> {
    return this.http
      .get<Pagina<ProcesoResumen>>(this.ruta, { params: parametrosDe(filtro) })
      .pipe(catchError(lanzarFallo));
  }

  obtener(id: number): Observable<Proceso> {
    return this.http.get<Proceso>(`${this.ruta}/${id}`).pipe(catchError(lanzarFallo));
  }

  obtenerDiagrama(id: number): Observable<DiagramaProceso> {
    return this.http.get<DiagramaProceso>(`${this.ruta}/${id}/diagrama`).pipe(catchError(lanzarFallo));
  }

  crear(datos: CrearProceso): Observable<Proceso> {
    return this.http.post<Proceso>(this.ruta, datos).pipe(catchError(lanzarFallo));
  }

  editar(id: number, datos: EditarProceso): Observable<Proceso> {
    return this.http.put<Proceso>(`${this.ruta}/${id}`, datos).pipe(catchError(lanzarFallo));
  }
}

function parametrosDe(filtro: FiltroProcesos): HttpParams {
  return Object.entries(filtro).reduce((parametros, [clave, valor]) => {
    const texto = valor === null || valor === undefined ? '' : String(valor).trim();
    return texto ? parametros.set(clave, texto) : parametros;
  }, new HttpParams());
}

function lanzarFallo(error: unknown): Observable<never> {
  return throwError(() => traducirFallo(error));
}
