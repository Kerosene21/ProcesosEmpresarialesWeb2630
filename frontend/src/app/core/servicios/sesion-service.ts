import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, switchMap, tap } from 'rxjs';

import { lanzarFallo } from '../http/traducir-fallo';
import { Credenciales, RolUsuario, SESION_ANONIMA, Sesion } from '../modelos/sesion';

export const RUTA_SESION = '/api/sesion';

const CABECERAS_FORMULARIO = new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' });

@Injectable({
  providedIn: 'root',
})
export class SesionService {
  private readonly http = inject(HttpClient);
  private readonly estado = signal<Sesion | null>(null);

  readonly sesion = this.estado.asReadonly();
  readonly autenticado = computed(() => this.estado()?.autenticado === true);
  readonly rol = computed(() => this.estado()?.rol ?? null);

  cargar(): Observable<Sesion> {
    return this.http.get<Sesion>(RUTA_SESION).pipe(
      tap((sesion) => this.estado.set(sesion)),
      catchError(lanzarFallo),
    );
  }

  asegurar(): Observable<Sesion> {
    const actual = this.estado();
    return actual ? of(actual) : this.cargar();
  }

  iniciar(credenciales: Credenciales): Observable<Sesion> {
    const cuerpo = new URLSearchParams({ correo: credenciales.correo, password: credenciales.password });
    return this.http.post<void>(RUTA_SESION, cuerpo.toString(), { headers: CABECERAS_FORMULARIO }).pipe(
      catchError(lanzarFallo),
      switchMap(() => this.cargar()),
    );
  }

  cerrar(): Observable<void> {
    return this.http.delete<void>(RUTA_SESION).pipe(
      catchError(lanzarFallo),
      tap(() => this.estado.set(SESION_ANONIMA)),
      switchMap(() => this.cargar().pipe(catchError(() => of(SESION_ANONIMA)))),
      map(() => undefined),
    );
  }

  olvidar(): void {
    this.estado.set(SESION_ANONIMA);
  }

  tieneRol(roles: readonly RolUsuario[]): boolean {
    const rol = this.rol();
    return rol !== null && roles.includes(rol);
  }
}
