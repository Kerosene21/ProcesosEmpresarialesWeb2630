import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError } from 'rxjs';

import { lanzarFallo } from '../http/traducir-fallo';
import { MessageCatchRespuesta } from '../modelos/diagrama';
import { CrearMessageCatch, EditarMessageCatch } from '../modelos/evento';
import { MoverNodo } from '../modelos/movimiento';

@Injectable({
  providedIn: 'root',
})
export class MessageCatchService {
  private readonly http = inject(HttpClient);

  crear(procesoId: number, datos: CrearMessageCatch): Observable<MessageCatchRespuesta> {
    return this.http.post<MessageCatchRespuesta>(this.ruta(procesoId), datos).pipe(catchError(lanzarFallo));
  }

  editar(procesoId: number, catchId: number, datos: EditarMessageCatch): Observable<MessageCatchRespuesta> {
    return this.http
      .put<MessageCatchRespuesta>(`${this.ruta(procesoId)}/${catchId}`, datos)
      .pipe(catchError(lanzarFallo));
  }

  mover(procesoId: number, catchId: number, datos: MoverNodo): Observable<MessageCatchRespuesta> {
    return this.http
      .put<MessageCatchRespuesta>(`${this.ruta(procesoId)}/${catchId}/posicion`, datos)
      .pipe(catchError(lanzarFallo));
  }

  previsualizarEliminacion(procesoId: number, catchId: number): Observable<MessageCatchRespuesta> {
    return this.http
      .get<MessageCatchRespuesta>(`${this.ruta(procesoId)}/${catchId}/eliminacion`)
      .pipe(catchError(lanzarFallo));
  }

  eliminar(procesoId: number, catchId: number): Observable<void> {
    return this.http.delete<void>(`${this.ruta(procesoId)}/${catchId}`).pipe(catchError(lanzarFallo));
  }

  private ruta(procesoId: number): string {
    return `/api/procesos/${procesoId}/message-catches`;
  }
}
