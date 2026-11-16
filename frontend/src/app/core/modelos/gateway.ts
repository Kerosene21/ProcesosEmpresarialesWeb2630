import { TipoGateway } from './diagrama';

export const TIPOS_GATEWAY: readonly TipoGateway[] = ['EXCLUSIVO', 'PARALELO', 'INCLUSIVO'];

export const LONGITUD_MAXIMA_CONDICION = 200;

export interface CrearGateway {
  tipo: TipoGateway;
  posicionX: number;
  posicionY: number;
  poolId: number | null;
}

export interface EditarGateway {
  tipo: TipoGateway;
  condiciones: Record<number, string>;
}

export function exigeCondicion(tipo: TipoGateway): boolean {
  return tipo !== 'PARALELO';
}
