import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError } from 'rxjs';

import { lanzarFallo } from '../http/traducir-fallo';
import { EnvioExternoRespuesta } from '../modelos/diagrama';
import { CrearEnvioExterno, EditarEnvioExterno } from '../modelos/evento';
import { MoverNodo } from '../modelos/movimiento';

@Injectable({
  providedIn: 'root',
})
export class EnvioExternoService {
  private readonly http = inject(HttpClient);

  crear(procesoId: number, datos: CrearEnvioExterno): Observable<EnvioExternoRespuesta> {
    return this.http.post<EnvioExternoRespuesta>(this.ruta(procesoId), datos).pipe(catchError(lanzarFallo));
  }

  editar(procesoId: number, envioId: number, datos: EditarEnvioExterno): Observable<EnvioExternoRespuesta> {
    return this.http
      .put<EnvioExternoRespuesta>(`${this.ruta(procesoId)}/${envioId}`, datos)
      .pipe(catchError(lanzarFallo));
  }

  mover(procesoId: number, envioId: number, datos: MoverNodo): Observable<EnvioExternoRespuesta> {
    return this.http
      .put<EnvioExternoRespuesta>(`${this.ruta(procesoId)}/${envioId}/posicion`, datos)
      .pipe(catchError(lanzarFallo));
  }

  previsualizarEliminacion(procesoId: number, envioId: number): Observable<EnvioExternoRespuesta> {
    return this.http
      .get<EnvioExternoRespuesta>(`${this.ruta(procesoId)}/${envioId}/eliminacion`)
      .pipe(catchError(lanzarFallo));
  }

  eliminar(procesoId: number, envioId: number): Observable<void> {
    return this.http.delete<void>(`${this.ruta(procesoId)}/${envioId}`).pipe(catchError(lanzarFallo));
  }

  private ruta(procesoId: number): string {
    return `/api/procesos/${procesoId}/envios-externos`;
  }
}
