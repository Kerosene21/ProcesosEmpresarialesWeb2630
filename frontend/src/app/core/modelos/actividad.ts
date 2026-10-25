import { TipoActividad } from './diagrama';

export const TIPOS_ACTIVIDAD: readonly TipoActividad[] = ['TAREA_USUARIO', 'TAREA_SISTEMA', 'TAREA_MANUAL', 'TAREA_SERVICIO'];

export const LONGITUD_MAXIMA_ACTIVIDAD = {
  nombre: 150,
} as const;

export interface EditarActividad {
  nombre: string;
  tipo: TipoActividad;
  laneId: number;
  posicionX?: number;
  posicionY?: number;
}

export interface CrearActividad extends EditarActividad {
  posicionX: number;
  posicionY: number;
}
