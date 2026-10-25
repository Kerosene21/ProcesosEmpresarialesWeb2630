import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError } from 'rxjs';

import { lanzarFallo } from '../http/traducir-fallo';
import { PoolRespuesta } from '../modelos/diagrama';
import { CrearPool, EditarPool } from '../modelos/pool';

@Injectable({
  providedIn: 'root',
})
export class PoolService {
  private readonly http = inject(HttpClient);

  listar(procesoId: number): Observable<PoolRespuesta[]> {
    return this.http.get<PoolRespuesta[]>(this.ruta(procesoId)).pipe(catchError(lanzarFallo));
  }

  crear(procesoId: number, datos: CrearPool): Observable<PoolRespuesta> {
    return this.http.post<PoolRespuesta>(this.ruta(procesoId), datos).pipe(catchError(lanzarFallo));
  }

  editar(procesoId: number, poolId: number, datos: EditarPool): Observable<PoolRespuesta> {
    return this.http.put<PoolRespuesta>(`${this.ruta(procesoId)}/${poolId}`, datos).pipe(catchError(lanzarFallo));
  }

  eliminar(procesoId: number, poolId: number): Observable<void> {
    return this.http.delete<void>(`${this.ruta(procesoId)}/${poolId}`).pipe(catchError(lanzarFallo));
  }

  private ruta(procesoId: number): string {
    return `/api/procesos/${procesoId}/pools`;
  }
}
