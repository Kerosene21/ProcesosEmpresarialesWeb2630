import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError } from 'rxjs';

import { lanzarFallo } from '../http/traducir-fallo';
import { DiagramaProceso } from '../modelos/diagrama';
import { Historial } from '../modelos/historial';
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

  historial(id: number): Observable<Historial[]> {
    return this.http.get<Historial[]>(`${this.ruta}/${id}/historial`).pipe(catchError(lanzarFallo));
  }

  crear(datos: CrearProceso): Observable<Proceso> {
    return this.http.post<Proceso>(this.ruta, datos).pipe(catchError(lanzarFallo));
  }

  editar(id: number, datos: EditarProceso): Observable<Proceso> {
    return this.http.put<Proceso>(`${this.ruta}/${id}`, datos).pipe(catchError(lanzarFallo));
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.ruta}/${id}`).pipe(catchError(lanzarFallo));
  }
}

function parametrosDe(filtro: FiltroProcesos): HttpParams {
  return Object.entries(filtro).reduce((parametros, [clave, valor]) => {
    const texto = valor === null || valor === undefined ? '' : String(valor).trim();
    return texto ? parametros.set(clave, texto) : parametros;
  }, new HttpParams());
}
