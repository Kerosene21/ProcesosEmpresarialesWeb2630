export type VisibilidadRolProceso = 'ACTIVOS' | 'INACTIVOS' | 'TODOS';

export type AccionRolProceso = 'CREACION' | 'EDICION' | 'ELIMINACION';

export const VISIBILIDADES_ROL: readonly VisibilidadRolProceso[] = ['ACTIVOS', 'INACTIVOS', 'TODOS'];

export const LONGITUD_MAXIMA_ROL = {
  nombre: 100,
  descripcion: 500,
} as const;

export interface RolProceso {
  id: number;
  nombre: string;
  descripcion: string;
  activo: boolean;
}

export interface ProcesoUsoRol {
  id: number;
  nombre: string;
  lanes: number;
  actividadesActivas: number;
}

export interface RolProcesoResumen extends RolProceso {
  enUso: boolean;
  puedeEliminar: boolean;
  procesos: ProcesoUsoRol[];
}

export interface CrearRolProceso {
  nombre: string;
  descripcion: string;
}

export type EditarRolProceso = CrearRolProceso;

export interface HistorialRolProceso {
  fecha: string;
  usuarioCorreo: string;
  accion: AccionRolProceso;
  cambiosRealizados: string;
}

export interface FiltroRolesProceso {
  q?: string | null;
  visibilidad?: VisibilidadRolProceso | null;
  page?: number | null;
}
