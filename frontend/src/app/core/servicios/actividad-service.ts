import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError } from 'rxjs';

import { lanzarFallo } from '../http/traducir-fallo';
import { CrearActividad, EditarActividad } from '../modelos/actividad';
import { ActividadRespuesta } from '../modelos/diagrama';
import { MoverActividad } from '../modelos/movimiento';

@Injectable({
  providedIn: 'root',
})
export class ActividadService {
  private readonly http = inject(HttpClient);

  crear(procesoId: number, datos: CrearActividad): Observable<ActividadRespuesta> {
    return this.http.post<ActividadRespuesta>(this.ruta(procesoId), datos).pipe(catchError(lanzarFallo));
  }

  editar(procesoId: number, actividadId: number, datos: EditarActividad): Observable<ActividadRespuesta> {
    return this.http
      .put<ActividadRespuesta>(`${this.ruta(procesoId)}/${actividadId}`, datos)
      .pipe(catchError(lanzarFallo));
  }

  mover(procesoId: number, actividadId: number, datos: MoverActividad): Observable<ActividadRespuesta> {
    return this.http
      .put<ActividadRespuesta>(`${this.ruta(procesoId)}/${actividadId}/posicion`, datos)
      .pipe(catchError(lanzarFallo));
  }

  eliminar(procesoId: number, actividadId: number): Observable<void> {
    return this.http.delete<void>(`${this.ruta(procesoId)}/${actividadId}`).pipe(catchError(lanzarFallo));
  }

  private ruta(procesoId: number): string {
    return `/api/procesos/${procesoId}/actividades`;
  }
}
