import { DOCUMENT, InjectionToken, inject } from '@angular/core';

export const NOMBRE_CANAL_SESION = 'procesos-empresariales:sesion';
export const CLAVE_CIERRE_PENDIENTE = 'procesos-empresariales:cierre-pendiente';

export interface MensajeSesion {
  readonly tipo: 'cerrada';
}

export interface CanalSesion {
  postMessage(mensaje: MensajeSesion): void;
  addEventListener(tipo: 'message', oyente: (evento: MessageEvent<unknown>) => void): void;
}

export type AlmacenMarcas = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

export const CANAL_SESION = new InjectionToken<CanalSesion | null>('CANAL_SESION', {
  providedIn: 'root',
  factory: () => {
    const Canal = inject(DOCUMENT).defaultView?.BroadcastChannel;
    return Canal ? new Canal(NOMBRE_CANAL_SESION) : null;
  },
});

export const ALMACEN_MARCAS = new InjectionToken<AlmacenMarcas | null>('ALMACEN_MARCAS', {
  providedIn: 'root',
  factory: () => {
    try {
      return inject(DOCUMENT).defaultView?.localStorage ?? null;
    } catch {
      return null;
    }
  },
});

export function esMensajeDeCierre(dato: unknown): dato is MensajeSesion {
  return (
    typeof dato === 'object' && dato !== null && (dato as { tipo?: unknown }).tipo === 'cerrada'
  );
}
