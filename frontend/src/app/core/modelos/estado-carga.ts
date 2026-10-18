import { FalloHttp } from './fallo-http';

export type EstadoCarga<T> =
  | { tipo: 'cargando' }
  | { tipo: 'listo'; datos: T }
  | { tipo: 'error'; fallo: FalloHttp };

export const CARGANDO: EstadoCarga<never> = { tipo: 'cargando' };
