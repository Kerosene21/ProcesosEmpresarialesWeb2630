import { TipoNodoFlujo } from './diagrama';
import { LONGITUD_MAXIMA_CONDICION } from './gateway';

export const LONGITUD_MAXIMA_ARCO = {
  etiqueta: 150,
  condicion: LONGITUD_MAXIMA_CONDICION,
} as const;

export interface CrearArco {
  origenTipo: TipoNodoFlujo;
  origenId: number;
  destinoTipo: TipoNodoFlujo;
  destinoId: number;
  etiqueta: string | null;
  condicion: string | null;
}

export type EditarArco = CrearArco;
