import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError } from 'rxjs';

import { lanzarFallo } from '../http/traducir-fallo';
import { CrearArco, EditarArco } from '../modelos/arco';
import { ArcoRespuesta } from '../modelos/diagrama';

@Injectable({
  providedIn: 'root',
})
export class ArcoService {
  private readonly http = inject(HttpClient);

  crear(procesoId: number, datos: CrearArco): Observable<ArcoRespuesta> {
    return this.http.post<ArcoRespuesta>(this.ruta(procesoId), datos).pipe(catchError(lanzarFallo));
  }

  editar(procesoId: number, arcoId: number, datos: EditarArco): Observable<ArcoRespuesta> {
    return this.http.put<ArcoRespuesta>(`${this.ruta(procesoId)}/${arcoId}`, datos).pipe(catchError(lanzarFallo));
  }

  eliminar(procesoId: number, arcoId: number): Observable<void> {
    return this.http.delete<void>(`${this.ruta(procesoId)}/${arcoId}`).pipe(catchError(lanzarFallo));
  }

  private ruta(procesoId: number): string {
    return `/api/procesos/${procesoId}/arcos`;
  }
}
