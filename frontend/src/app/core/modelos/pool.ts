import { TipoPool } from './diagrama';

export type TipoPoolAdicional = Exclude<TipoPool, 'PROPIETARIO'>;

export const TIPOS_POOL_ADICIONAL: readonly TipoPoolAdicional[] = ['PARTICIPANTE', 'EXTERNO'];

export const LONGITUD_MAXIMA_POOL = {
  nombre: 150,
} as const;

export interface EditarPool {
  nombre: string;
  cajaNegra: boolean;
  empresaParticipanteId: number | null;
}

export interface CrearPool extends EditarPool {
  tipo: TipoPoolAdicional;
}
