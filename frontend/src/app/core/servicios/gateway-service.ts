import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError } from 'rxjs';

import { lanzarFallo } from '../http/traducir-fallo';
import { GatewayRespuesta } from '../modelos/diagrama';
import { CrearGateway, EditarGateway } from '../modelos/gateway';
import { MoverNodo } from '../modelos/movimiento';

@Injectable({
  providedIn: 'root',
})
export class GatewayService {
  private readonly http = inject(HttpClient);

  crear(procesoId: number, datos: CrearGateway): Observable<GatewayRespuesta> {
    return this.http.post<GatewayRespuesta>(this.ruta(procesoId), datos).pipe(catchError(lanzarFallo));
  }

  editar(procesoId: number, gatewayId: number, datos: EditarGateway): Observable<GatewayRespuesta> {
    return this.http
      .put<GatewayRespuesta>(`${this.ruta(procesoId)}/${gatewayId}`, datos)
      .pipe(catchError(lanzarFallo));
  }

  mover(procesoId: number, gatewayId: number, datos: MoverNodo): Observable<GatewayRespuesta> {
    return this.http
      .put<GatewayRespuesta>(`${this.ruta(procesoId)}/${gatewayId}/posicion`, datos)
      .pipe(catchError(lanzarFallo));
  }

  previsualizarEliminacion(procesoId: number, gatewayId: number): Observable<GatewayRespuesta> {
    return this.http
      .get<GatewayRespuesta>(`${this.ruta(procesoId)}/${gatewayId}/eliminacion`)
      .pipe(catchError(lanzarFallo));
  }

  eliminar(procesoId: number, gatewayId: number): Observable<void> {
    return this.http.delete<void>(`${this.ruta(procesoId)}/${gatewayId}`).pipe(catchError(lanzarFallo));
  }

  private ruta(procesoId: number): string {
    return `/api/procesos/${procesoId}/gateways`;
  }
}
