import { RolUsuario } from './sesion';

export interface PermisoEstructura {
  rol: RolUsuario;
  configurable: boolean;
  crearPool: boolean;
  editarPool: boolean;
  eliminarPool: boolean;
  crearLane: boolean;
  editarLane: boolean;
  eliminarLane: boolean;
}

export const SIN_PERMISOS_ESTRUCTURA: Omit<PermisoEstructura, 'rol' | 'configurable'> = {
  crearPool: false,
  editarPool: false,
  eliminarPool: false,
  crearLane: false,
  editarLane: false,
  eliminarLane: false,
};
