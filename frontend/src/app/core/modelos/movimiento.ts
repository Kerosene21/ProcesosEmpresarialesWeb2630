import { TipoNodoFlujo } from './diagrama';

export const POSICION_MAXIMA = 50000;

export interface MoverNodo {
  posicionX: number;
  posicionY: number;
}

export interface MoverActividad extends MoverNodo {
  laneId: number;
}

export interface PosicionNodo extends MoverNodo {
  tipo: TipoNodoFlujo;
  id: number;
}

export interface RecolocarNodos {
  nodos: PosicionNodo[];
}
