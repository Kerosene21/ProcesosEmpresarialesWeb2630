export type EstadoProceso = 'BORRADOR' | 'PUBLICADO';

export type VisibilidadProceso = 'ACTIVOS' | 'INACTIVOS' | 'TODOS';

export type AlcanceProceso = 'PROPIOS' | 'COMPARTIDOS' | 'TODOS';

export const ESTADOS_PROCESO: readonly EstadoProceso[] = ['BORRADOR', 'PUBLICADO'];

export const VISIBILIDADES_PROCESO: readonly VisibilidadProceso[] = ['ACTIVOS', 'INACTIVOS', 'TODOS'];

export const ALCANCES_PROCESO: readonly AlcanceProceso[] = ['PROPIOS', 'COMPARTIDOS', 'TODOS'];

export const LONGITUD_MAXIMA_PROCESO = {
  nombre: 150,
  categoria: 100,
} as const;

export interface ProcesoResumen {
  id: number;
  nombre: string;
  descripcion: string;
  categoria: string;
  estado: EstadoProceso;
  eliminado: boolean;
  empresaPropietariaId: number;
  empresaPropietariaNombre: string;
  soloLectura: boolean;
}

export interface Proceso {
  id: number;
  nombre: string;
  descripcion: string;
  categoria: string;
  estado: EstadoProceso;
  poolId: number;
  poolNombre: string;
  eliminado: boolean;
  empresaPropietariaId: number;
  empresaPropietariaNombre: string;
  soloLectura: boolean;
}

export interface CrearProceso {
  nombre: string;
  descripcion: string;
  categoria: string;
}

export interface EditarProceso extends CrearProceso {
  estado: EstadoProceso;
}

export interface FiltroProcesos {
  q?: string | null;
  estado?: EstadoProceso | null;
  categoria?: string | null;
  visibilidad?: VisibilidadProceso | null;
  alcance?: AlcanceProceso | null;
  page?: number | null;
}
