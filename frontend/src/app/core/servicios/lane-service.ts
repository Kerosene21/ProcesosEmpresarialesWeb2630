import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError } from 'rxjs';

import { lanzarFallo } from '../http/traducir-fallo';
import { LaneRespuesta } from '../modelos/diagrama';
import { CrearLane, EditarLane, ReordenarLanes } from '../modelos/lane';
import { RolProceso } from '../modelos/rol-proceso';

@Injectable({
  providedIn: 'root',
})
export class LaneService {
  private readonly http = inject(HttpClient);

  listar(procesoId: number, poolId: number): Observable<LaneRespuesta[]> {
    return this.http.get<LaneRespuesta[]>(this.ruta(procesoId, poolId)).pipe(catchError(lanzarFallo));
  }

  rolesDisponibles(procesoId: number, poolId: number): Observable<RolProceso[]> {
    return this.http
      .get<RolProceso[]>(`/api/procesos/${procesoId}/pools/${poolId}/roles-disponibles`)
      .pipe(catchError(lanzarFallo));
  }

  crear(procesoId: number, poolId: number, datos: CrearLane): Observable<LaneRespuesta> {
    return this.http.post<LaneRespuesta>(this.ruta(procesoId, poolId), datos).pipe(catchError(lanzarFallo));
  }

  editar(procesoId: number, poolId: number, laneId: number, datos: EditarLane): Observable<LaneRespuesta> {
    return this.http
      .put<LaneRespuesta>(`${this.ruta(procesoId, poolId)}/${laneId}`, datos)
      .pipe(catchError(lanzarFallo));
  }

  reordenar(procesoId: number, poolId: number, datos: ReordenarLanes): Observable<LaneRespuesta[]> {
    return this.http
      .put<LaneRespuesta[]>(`${this.ruta(procesoId, poolId)}/orden`, datos)
      .pipe(catchError(lanzarFallo));
  }

  eliminar(procesoId: number, poolId: number, laneId: number): Observable<void> {
    return this.http.delete<void>(`${this.ruta(procesoId, poolId)}/${laneId}`).pipe(catchError(lanzarFallo));
  }

  private ruta(procesoId: number, poolId: number): string {
    return `/api/procesos/${procesoId}/pools/${poolId}/lanes`;
  }
}
