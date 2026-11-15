import { HttpClient, HttpXsrfTokenExtractor } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import {
  Observable,
  Subject,
  catchError,
  defer,
  finalize,
  map,
  of,
  shareReplay,
  switchMap,
  tap,
  throwError,
} from 'rxjs';

import { RUTAS_AUTENTICACION } from '../http/rutas-api';
import { lanzarFallo } from '../http/traducir-fallo';
import { FalloHttp } from '../modelos/fallo-http';
import {
  Credenciales,
  RespuestaToken,
  RolUsuario,
  SESION_ANONIMA,
  Sesion,
} from '../modelos/sesion';
import {
  ALMACEN_MARCAS,
  CANAL_SESION,
  CLAVE_CIERRE_PENDIENTE,
  esMensajeDeCierre,
} from './canal-sesion';

export const CODIGO_SESION_DESCARTADA = 'SESION_DESCARTADA';

const SESION_DESCARTADA: FalloHttp = {
  tipo: 'no-autenticado',
  estado: 401,
  codigo: CODIGO_SESION_DESCARTADA,
  titulo: 'Se requiere iniciar sesión',
  detalle: 'La sesión cambió mientras se completaba la solicitud.',
};

@Injectable({
  providedIn: 'root',
})
export class SesionService {
  private readonly http = inject(HttpClient);
  private readonly xsrf = inject(HttpXsrfTokenExtractor);
  private readonly canal = inject(CANAL_SESION);
  private readonly almacen = inject(ALMACEN_MARCAS);
  private readonly cierresExternos = new Subject<void>();
  private readonly estado = signal<Sesion | null>(null);
  private tokenActual: string | null = null;
  private generacion = 0;
  private renovacion: Observable<string> | null = null;

  readonly sesion = this.estado.asReadonly();
  readonly autenticado = computed(() => this.estado()?.autenticado === true);
  readonly rol = computed(() => this.estado()?.rol ?? null);
  readonly cierreEnOtraPestana = this.cierresExternos.asObservable();

  constructor() {
    this.canal?.addEventListener('message', (evento) => this.recibir(evento.data));
  }

  tokenAcceso(): string | null {
    return this.tokenActual;
  }

  cargar(): Observable<Sesion> {
    if (this.tokenActual) {
      return this.consultarActual();
    }
    return this.cierrePendiente() ? this.completarCierrePendiente() : this.restaurar();
  }

  asegurar(): Observable<Sesion> {
    const actual = this.estado();
    return actual ? of(actual) : this.cargar();
  }

  iniciar(credenciales: Credenciales): Observable<Sesion> {
    const generacion = ++this.generacion;
    this.renovacion = null;
    const cuerpo: Credenciales = { correo: credenciales.correo, password: credenciales.password };
    return this.conCsrf(this.http.post<RespuestaToken>(RUTAS_AUTENTICACION.login, cuerpo)).pipe(
      catchError(lanzarFallo),
      switchMap((respuesta) => this.aplicar(respuesta, generacion)),
      tap(() => this.recordarCierrePendiente(false)),
      map((respuesta) => respuesta.usuario),
    );
  }

  renovar(): Observable<string> {
    if (this.renovacion) {
      return this.renovacion;
    }
    const generacion = this.generacion;
    const renovacion: Observable<string> = this.conCsrf(
      this.http.post<RespuestaToken>(RUTAS_AUTENTICACION.refresh, null),
    ).pipe(
      catchError(lanzarFallo),
      switchMap((respuesta) => this.aplicar(respuesta, generacion)),
      map((respuesta) => respuesta.tokenAcceso),
      finalize(() => {
        if (this.renovacion === renovacion) {
          this.renovacion = null;
        }
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    this.renovacion = renovacion;
    return renovacion;
  }

  cerrar(): Observable<void> {
    this.olvidar();
    this.recordarCierrePendiente(true);
    this.canal?.postMessage({ tipo: 'cerrada' });
    return this.revocarEnServidor();
  }

  reintentarCierre(): Observable<void> {
    return this.revocarEnServidor();
  }

  cierrePendiente(): boolean {
    try {
      return this.almacen !== null && this.almacen.getItem(CLAVE_CIERRE_PENDIENTE) !== null;
    } catch {
      return false;
    }
  }

  olvidar(): void {
    this.generacion++;
    this.renovacion = null;
    this.tokenActual = null;
    this.estado.set(SESION_ANONIMA);
  }

  tieneRol(roles: readonly RolUsuario[]): boolean {
    const rol = this.rol();
    return rol !== null && roles.includes(rol);
  }

  private revocarEnServidor(): Observable<void> {
    return this.conCsrf(this.http.post<void>(RUTAS_AUTENTICACION.logout, null)).pipe(
      tap(() => this.recordarCierrePendiente(false)),
      map(() => undefined),
      catchError(lanzarFallo),
    );
  }

  private completarCierrePendiente(): Observable<Sesion> {
    this.olvidar();
    return this.revocarEnServidor().pipe(
      map(() => SESION_ANONIMA),
      catchError(() => of(SESION_ANONIMA)),
    );
  }

  private recordarCierrePendiente(pendiente: boolean): void {
    try {
      if (pendiente) {
        this.almacen?.setItem(CLAVE_CIERRE_PENDIENTE, '1');
      } else {
        this.almacen?.removeItem(CLAVE_CIERRE_PENDIENTE);
      }
    } catch {
      return;
    }
  }

  private recibir(dato: unknown): void {
    if (!esMensajeDeCierre(dato)) {
      return;
    }
    const estabaAutenticado = this.autenticado();
    this.olvidar();
    if (estabaAutenticado) {
      this.cierresExternos.next();
    }
  }

  private restaurar(): Observable<Sesion> {
    const generacion = this.generacion;
    return this.renovar().pipe(
      map(() => this.estado() ?? SESION_ANONIMA),
      catchError((fallo: FalloHttp) => {
        if (fallo.estado === 0 || fallo.estado >= 500) {
          return throwError(() => fallo);
        }
        if (generacion === this.generacion) {
          this.olvidar();
        }
        return of(this.estado() ?? SESION_ANONIMA);
      }),
    );
  }

  private consultarActual(): Observable<Sesion> {
    const generacion = this.generacion;
    return this.http.get<Sesion>(RUTAS_AUTENTICACION.me).pipe(
      catchError(lanzarFallo),
      switchMap((sesion) => {
        if (generacion !== this.generacion) {
          return throwError(() => SESION_DESCARTADA);
        }
        this.estado.set(sesion);
        return of(sesion);
      }),
    );
  }

  private aplicar(respuesta: RespuestaToken, generacion: number): Observable<RespuestaToken> {
    if (generacion !== this.generacion) {
      return throwError(() => SESION_DESCARTADA);
    }
    this.tokenActual = respuesta.tokenAcceso;
    this.estado.set(respuesta.usuario);
    return of(respuesta);
  }

  private conCsrf<T>(peticion: Observable<T>): Observable<T> {
    return defer(() =>
      this.xsrf.getToken()
        ? peticion
        : this.http.get<void>(RUTAS_AUTENTICACION.csrf).pipe(switchMap(() => peticion)),
    );
  }
}
