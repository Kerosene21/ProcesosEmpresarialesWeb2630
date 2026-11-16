import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError } from 'rxjs';

import { lanzarFallo } from '../http/traducir-fallo';
import { EmpresaInvitada } from '../modelos/comparticion';

@Injectable({
  providedIn: 'root',
})
export class ComparticionService {
  private readonly http = inject(HttpClient);

  listar(procesoId: number): Observable<EmpresaInvitada[]> {
    return this.http.get<EmpresaInvitada[]>(this.ruta(procesoId)).pipe(catchError(lanzarFallo));
  }

  compartir(procesoId: number, empresaId: number): Observable<EmpresaInvitada> {
    return this.http
      .post<EmpresaInvitada>(`${this.ruta(procesoId)}/${empresaId}`, null)
      .pipe(catchError(lanzarFallo));
  }

  dejarDeCompartir(procesoId: number, empresaId: number): Observable<void> {
    return this.http.delete<void>(`${this.ruta(procesoId)}/${empresaId}`).pipe(catchError(lanzarFallo));
  }

  private ruta(procesoId: number): string {
    return `/api/procesos/${procesoId}/compartido-con`;
  }
}
