import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError } from 'rxjs';

import { lanzarFallo } from '../http/traducir-fallo';
import { MessageThrowRespuesta } from '../modelos/diagrama';
import { CrearMessageThrow, EditarMessageThrow } from '../modelos/evento';

@Injectable({
  providedIn: 'root',
})
export class MessageThrowService {
  private readonly http = inject(HttpClient);

  crear(procesoId: number, datos: CrearMessageThrow): Observable<MessageThrowRespuesta> {
    return this.http.post<MessageThrowRespuesta>(this.ruta(procesoId), datos).pipe(catchError(lanzarFallo));
  }

  editar(procesoId: number, throwId: number, datos: EditarMessageThrow): Observable<MessageThrowRespuesta> {
    return this.http
      .put<MessageThrowRespuesta>(`${this.ruta(procesoId)}/${throwId}`, datos)
      .pipe(catchError(lanzarFallo));
  }

  previsualizarEliminacion(procesoId: number, throwId: number): Observable<MessageThrowRespuesta> {
    return this.http
      .get<MessageThrowRespuesta>(`${this.ruta(procesoId)}/${throwId}/eliminacion`)
      .pipe(catchError(lanzarFallo));
  }

  eliminar(procesoId: number, throwId: number): Observable<void> {
    return this.http.delete<void>(`${this.ruta(procesoId)}/${throwId}`).pipe(catchError(lanzarFallo));
  }

  private ruta(procesoId: number): string {
    return `/api/procesos/${procesoId}/message-throws`;
  }
}
